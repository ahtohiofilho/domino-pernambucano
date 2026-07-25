package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankedAccountAuthorityTest {
    @Test
    fun anonymous_forged_and_mismatched_principals_cannot_create_ranked_room() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "account-player-1" },
        )
        val account = requireNotNull(
            store.promoteAccount(
                playerId = "player-1",
            ),
        )
        val request = CreateOnlineRoomRequestDto(
            localPlayerId = "player-1",
            playerName = "Jogador 1",
        )

        val anonymousResult = store.createPublicRankedRoom(
            request = request,
            identity = OnlineRequestIdentity(
                playerId = "player-1",
                kind = OnlinePrincipalKind.ANONYMOUS,
                accountId = null,
            ),
        )
        val forgedResult = store.createPublicRankedRoom(
            request = request,
            identity = account.toRequestIdentity().copy(
                accountId = "account-forged",
            ),
        )
        val mismatchedResult = store.createPublicRankedRoom(
            request = request,
            identity = account.toRequestIdentity().copy(
                playerId = "player-other",
            ),
        )

        assertFalse(anonymousResult.accepted)
        assertFalse(forgedResult.accepted)
        assertFalse(mismatchedResult.accepted)
        assertEquals(
            "Conta autenticada obrigatória para partida ranqueada.",
            anonymousResult.reason,
        )
        assertTrue(store.snapshotPersistentState().rooms.isEmpty())
        assertNull(store.getRoomSnapshot("server-room-1"))
    }

    @Test
    fun ranked_match_starts_only_after_four_canonical_account_principals() {
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
                store.promoteAccount(
                    playerId = "player-$playerNumber",
                ),
            )
        }
        val room = requireNotNull(
            store.createPublicRankedRoom(
                request = CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
                identity = accounts.getValue(1).toRequestIdentity(),
            ).roomSnapshot,
        )

        val anonymousJoin = store.joinPublicRankedRoom(
            request = JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "player-2",
                playerName = "Jogador 2",
            ),
            identity = OnlineRequestIdentity(
                playerId = "player-2",
                kind = OnlinePrincipalKind.ANONYMOUS,
                accountId = null,
            ),
        )

        assertFalse(anonymousJoin.accepted)
        assertEquals(
            1,
            requireNotNull(
                store.getRoomSnapshot(room.roomId),
            ).players.size,
        )

        listOf(2, 3, 4).forEach { playerNumber ->
            val result = store.joinPublicRankedRoom(
                request = JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "player-$playerNumber",
                    playerName = "Jogador $playerNumber",
                ),
                identity = accounts
                    .getValue(playerNumber)
                    .toRequestIdentity(),
            )

            assertTrue(result.accepted)
        }

        val finalRoom = requireNotNull(
            store.getRoomSnapshot(room.roomId),
        )
        val matchId = requireNotNull(finalRoom.matchId)
        val rankedIdentities = requireNotNull(
            store.getRankedPlayerIdentities(matchId),
        )

        assertEquals(DominoMatchMode.PUBLIC_RANKED, finalRoom.matchMode)
        assertEquals(
            accounts.values.map { account -> account.accountId },
            rankedIdentities.map { identity -> identity.accountId },
        )
        assertTrue(
            rankedIdentities.all { identity ->
                !identity.accountId.isNullOrBlank()
            },
        )
    }

    @Test
    fun canonical_ranked_accounts_are_reconstructed_after_json_restart() {
        var accountSequence = 0
        val sourceStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = {
                accountSequence++
                "account-$accountSequence"
            },
        )
        val accounts = (1..4).map { playerNumber ->
            requireNotNull(
                sourceStore.promoteAccount(
                    playerId = "player-$playerNumber",
                ),
            )
        }
        val room = requireNotNull(
            sourceStore.createPublicRankedRoom(
                request = CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
                identity = accounts[0].toRequestIdentity(),
            ).roomSnapshot,
        )

        accounts.drop(1).forEachIndexed { index, account ->
            val playerNumber = index + 2
            assertTrue(
                sourceStore.joinPublicRankedRoom(
                    request = JoinOnlineRoomRequestDto(
                        roomCode = room.roomCode,
                        localPlayerId = "player-$playerNumber",
                        playerName = "Jogador $playerNumber",
                    ),
                    identity = account.toRequestIdentity(),
                ).accepted,
            )
        }

        val finalRoom = requireNotNull(
            sourceStore.getRoomSnapshot(room.roomId),
        )
        val matchId = requireNotNull(finalRoom.matchId)
        val json = Json {
            encodeDefaults = true
        }
        val persisted = json.decodeFromString<OnlineServerStoreState>(
            json.encodeToString(
                sourceStore.snapshotPersistentState(),
            ),
        )
        val restartedStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        restartedStore.restorePersistentState(persisted)

        val restoredIdentities = requireNotNull(
            restartedStore.getRankedPlayerIdentities(matchId),
        )
        assertNotNull(restartedStore.getMatchSnapshot(matchId))
        assertEquals(
            accounts.map { account -> account.accountId },
            restoredIdentities.map { identity -> identity.accountId },
        )
    }

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "ranked-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )
}
