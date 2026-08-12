package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankedMatchmakingQueueAuthorityTest {
    @Test
    fun public_store_contract_exposes_queue_instead_of_room_selection() {
        val methodNames = OnlineServerStore::class.java.methods
            .map { method -> method.name }
            .toSet()

        assertTrue("enqueuePublicRanked" in methodNames)
        assertTrue("cancelPublicRankedQueue" in methodNames)
        assertTrue("getPublicRankedQueueStatus" in methodNames)
        assertFalse("createPublicRankedRoom" in methodNames)
        assertFalse("joinPublicRankedRoom" in methodNames)
    }

    @Test
    fun queue_rejects_anonymous_forged_and_mismatched_identities() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "account-player-1" },
        )
        val account = requireNotNull(
            store.promoteAccount(playerId = "player-1"),
        )
        val request = request(playerNumber = 1)

        val anonymous = store.enqueuePublicRanked(
            request = request,
            identity = OnlineRequestIdentity(
                playerId = "player-1",
                kind = OnlinePrincipalKind.ANONYMOUS,
                accountId = null,
            ),
        )
        val forged = store.enqueuePublicRanked(
            request = request,
            identity = account.toRequestIdentity().copy(
                accountId = "account-forged",
            ),
        )
        val mismatched = store.enqueuePublicRanked(
            request = request.copy(localPlayerId = "player-other"),
            identity = account.toRequestIdentity(),
        )

        assertFalse(anonymous.accepted)
        assertFalse(forged.accepted)
        assertFalse(mismatched.accepted)
        assertEquals(PublicRankedQueueStatus.REJECTED, anonymous.status)
        assertTrue(store.snapshotPersistentState().rooms.isEmpty())
    }

    @Test
    fun first_four_distinct_accounts_form_one_atomic_fifo_match() {
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = {
                accountSequence++
                "account-$accountSequence"
            },
            publicRankedFormationEntropy =
                SequencePublicRankedFormationEntropy(
                    values = listOf(0, 2, 1, 1, 0),
                ),
        )
        val accounts = (1..5).associateWith { playerNumber ->
            requireNotNull(
                store.promoteAccount(playerId = "player-$playerNumber"),
            )
        }
        accounts.forEach { (playerNumber, account) ->
            requireNotNull(
                store.updateAccountProfile(
                    accountId = account.accountId,
                    publicDisplayName = "Jogador Teste",
                    tableName = rankedTableCode(playerNumber),
                ),
            )
        }

        (1..3).forEach { playerNumber ->
            val result = store.enqueuePublicRanked(
                request = request(playerNumber),
                identity = accounts.getValue(playerNumber).toRequestIdentity(),
            )

            assertEquals(PublicRankedQueueStatus.QUEUED, result.status)
            assertEquals(playerNumber, result.queuePosition)
        }

        val fourthResult = store.enqueuePublicRanked(
            request = request(4),
            identity = accounts.getValue(4).toRequestIdentity(),
        )

        assertEquals(PublicRankedQueueStatus.MATCHED, fourthResult.status)
        val matchedRoom = requireNotNull(fourthResult.roomSnapshot)
        assertEquals(DominoMatchMode.PUBLIC_RANKED, matchedRoom.matchMode)
        assertEquals(OnlineRoomStatusDto.IN_MATCH, matchedRoom.status)
        assertNotNull(matchedRoom.matchId)
        assertEquals(
            setOf("player-1", "player-2", "player-3", "player-4"),
            matchedRoom.players
                .map { player -> player.playerId }
                .toSet(),
        )
        assertEquals(
            listOf("player-3", "player-1", "player-2", "player-4"),
            matchedRoom.players
                .sortedBy { player -> player.seatIndex }
                .map { player -> player.playerId },
        )

        val expectedSeatByPlayer = mapOf(
            1 to 1,
            2 to 2,
            3 to 0,
            4 to 3,
        )
        (1..4).forEach { playerNumber ->
            val status = store.getPublicRankedQueueStatus(
                identity = accounts.getValue(playerNumber).toRequestIdentity(),
            )
            assertEquals(PublicRankedQueueStatus.MATCHED, status.status)
            assertEquals(matchedRoom.roomId, status.roomSnapshot?.roomId)
            assertEquals(
                expectedSeatByPlayer.getValue(playerNumber),
                status.localSeatIndex,
            )
        }

        val fifthResult = store.enqueuePublicRanked(
            request = request(5),
            identity = accounts.getValue(5).toRequestIdentity(),
        )
        assertEquals(PublicRankedQueueStatus.QUEUED, fifthResult.status)
        assertEquals(1, fifthResult.queuePosition)
    }

    @Test
    fun repeated_enqueue_refreshes_authoritative_table_code_without_changing_fifo_position() {
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = {
                accountSequence++
                "account-$accountSequence"
            },
        )
        val accounts = (1..4).associateWith { playerNumber ->
            requireNotNull(
                store.promoteAccount(playerId = "player-$playerNumber"),
            )
        }
        accounts.forEach { (playerNumber, account) ->
            requireNotNull(
                store.updateAccountProfile(
                    accountId = account.accountId,
                    publicDisplayName = "Jogador Teste",
                    tableName = rankedTableCode(playerNumber),
                ),
            )
        }

        val first = store.enqueuePublicRanked(
            request = request(1),
            identity = accounts.getValue(1).toRequestIdentity(),
        )
        store.enqueuePublicRanked(
            request = request(2),
            identity = accounts.getValue(2).toRequestIdentity(),
        )
        requireNotNull(
            store.updateAccountProfile(
                accountId = accounts.getValue(1).accountId,
                publicDisplayName = "Jogador Teste",
                tableName = "Z01",
            ),
        )
        val refreshed = store.enqueuePublicRanked(
            request = request(1).copy(playerName = "Z01"),
            identity = accounts.getValue(1).toRequestIdentity(),
        )

        assertEquals(1, first.queuePosition)
        assertEquals(1, refreshed.queuePosition)

        store.enqueuePublicRanked(
            request = request(3),
            identity = accounts.getValue(3).toRequestIdentity(),
        )
        val matched = store.enqueuePublicRanked(
            request = request(4),
            identity = accounts.getValue(4).toRequestIdentity(),
        )

        assertEquals(
            "Z01",
            matched.roomSnapshot
                ?.players
                ?.single { player -> player.playerId == "player-1" }
                ?.name,
        )
    }

    @Test
    fun cancellation_and_expiration_remove_waiting_entries() {
        var now = 1_000L
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            resourcePolicy = OnlineServerStoreResourcePolicy(
                publicRankedQueueEntryRetentionMillis = 1_000L,
                pruneIntervalMillis = 1L,
            ),
            accountIdFactory = {
                accountSequence++
                "account-$accountSequence"
            },
        )
        val account1 = requireNotNull(
            store.promoteAccount(playerId = "player-1"),
        )
        val account2 = requireNotNull(
            store.promoteAccount(playerId = "player-2"),
        )
        requireNotNull(
            store.updateAccountProfile(
                accountId = account1.accountId,
                publicDisplayName = "Jogador Teste",
                tableName = rankedTableCode(1),
            ),
        )
        requireNotNull(
            store.updateAccountProfile(
                accountId = account2.accountId,
                publicDisplayName = "Jogador Teste",
                tableName = rankedTableCode(2),
            ),
        )
        store.enqueuePublicRanked(
            request = request(1),
            identity = account1.toRequestIdentity(),
        )
        store.enqueuePublicRanked(
            request = request(2),
            identity = account2.toRequestIdentity(),
        )

        val cancelled = store.cancelPublicRankedQueue(
            identity = account1.toRequestIdentity(),
        )
        assertTrue(cancelled.accepted)
        assertEquals(PublicRankedQueueStatus.NOT_QUEUED, cancelled.status)

        now = 2_001L
        val expired = store.getPublicRankedQueueStatus(
            identity = account2.toRequestIdentity(),
        )
        assertEquals(PublicRankedQueueStatus.NOT_QUEUED, expired.status)
    }

    @Test
    fun player_in_private_room_cannot_enter_ranked_queue() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "account-player-1" },
        )
        val account = requireNotNull(
            store.promoteAccount(playerId = "player-1"),
        )
        val privateRoom = store.createRoom(request(1))

        val result = store.enqueuePublicRanked(
            request = request(1),
            identity = account.toRequestIdentity(),
        )

        assertTrue(privateRoom.accepted)
        assertFalse(result.accepted)
        assertEquals(PublicRankedQueueStatus.REJECTED, result.status)
        assertEquals(
            "O jogador já participa de outra sala ativa.",
            result.reason,
        )
        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            privateRoom.roomSnapshot?.matchMode,
        )
    }

    @Test
    fun waiting_queue_is_ephemeral_but_an_already_matched_room_survives_restart() {
        var accountSequence = 0
        val source = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = {
                accountSequence++
                "account-$accountSequence"
            },
        )
        val accounts = (1..4).map { playerNumber ->
            requireNotNull(
                source.promoteAccount(playerId = "player-$playerNumber"),
            )
        }
        accounts.forEachIndexed { index, account ->
            requireNotNull(
                source.updateAccountProfile(
                    accountId = account.accountId,
                    publicDisplayName = "Jogador Teste",
                    tableName = rankedTableCode(index + 1),
                ),
            )
        }

        source.enqueuePublicRanked(
            request = request(1),
            identity = accounts[0].toRequestIdentity(),
        )
        source.enqueuePublicRanked(
            request = request(2),
            identity = accounts[1].toRequestIdentity(),
        )

        val waitingRestart = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        waitingRestart.restorePersistentState(source.snapshotPersistentState())

        assertEquals(
            PublicRankedQueueStatus.NOT_QUEUED,
            waitingRestart.getPublicRankedQueueStatus(
                accounts[0].toRequestIdentity(),
            ).status,
        )

        source.enqueuePublicRanked(
            request = request(3),
            identity = accounts[2].toRequestIdentity(),
        )
        val matched = source.enqueuePublicRanked(
            request = request(4),
            identity = accounts[3].toRequestIdentity(),
        )
        val matchedRoomId = requireNotNull(matched.roomSnapshot?.roomId)

        val matchedRestart = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        matchedRestart.restorePersistentState(source.snapshotPersistentState())

        val restoredStatus = matchedRestart.getPublicRankedQueueStatus(
            accounts[0].toRequestIdentity(),
        )
        assertEquals(PublicRankedQueueStatus.MATCHED, restoredStatus.status)
        assertEquals(matchedRoomId, restoredStatus.roomSnapshot?.roomId)
        assertNull(restoredStatus.queuePosition)
    }

    private fun request(playerNumber: Int) =
        CreateOnlineRoomRequestDto(
            localPlayerId = "player-$playerNumber",
            playerName = rankedTableCode(playerNumber),
        )

    private fun rankedTableCode(
        playerNumber: Int,
    ): String = "P0$playerNumber"

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "ranked-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )

    private class SequencePublicRankedFormationEntropy(
        private val values: List<Int>,
    ) : PublicRankedFormationEntropy {
        private var nextValueIndex = 0

        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            val value = values.getOrElse(nextValueIndex) { 0 }
            nextValueIndex++
            return Math.floorMod(value, bound)
        }

        override fun nextNonceHex(byteCount: Int): String {
            require(byteCount > 0)
            return "ab".repeat(byteCount)
        }
    }
}
