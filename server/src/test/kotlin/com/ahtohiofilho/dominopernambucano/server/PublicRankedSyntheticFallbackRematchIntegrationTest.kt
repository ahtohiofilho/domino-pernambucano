package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankedSyntheticFallbackRematchIntegrationTest {
    @Test
    fun same_human_and_three_synthetics_form_again_inside_human_cohort_cooldown() {
        var now = 1_000L
        var accountSequence = 0

        val policy = OnlineServerStoreResourcePolicy(
            publicRankedSyntheticFallbackInitialDelayMillis = 20_000L,
            publicRankedSyntheticFallbackAdditionalSeatDelayMillis = 20_000L,
            publicRankedExactCohortCooldownMillis =
                30L * 60L * 1_000L,
            pruneIntervalMillis = 1_000L,
        )

        val firstStore = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            resourcePolicy = policy,
            accountIdFactory = {
                accountSequence += 1
                "account-$accountSequence"
            },
            publicRankedFormationEntropy = ZeroEntropy(),
        )

        val syntheticAccounts = (1..3).map { number ->
            requireNotNull(
                firstStore.promoteSyntheticAccount(
                    playerId = "synthetic-$number",
                    expectedAccountId = null,
                ),
            )
        }
        val humanAccount = requireNotNull(
            firstStore.promoteAccount(
                playerId = "human-1",
            ),
        )

        syntheticAccounts.forEachIndexed { index, account ->
            configureAccount(
                store = firstStore,
                account = account,
                tableCode = "S0${index + 1}",
            )
        }
        configureAccount(
            store = firstStore,
            account = humanAccount,
            tableCode = "H01",
        )

        enqueueCohort(
            store = firstStore,
            syntheticAccounts = syntheticAccounts,
            humanAccount = humanAccount,
        )

        now = 21_000L
        assertTrue(firstStore.advanceAuthoritativeTime())

        now = 41_000L
        assertTrue(firstStore.advanceAuthoritativeTime())

        now = 61_000L
        assertTrue(firstStore.advanceAuthoritativeTime())

        val firstMatch = firstStore.getPublicRankedQueueStatus(
            humanAccount.toRequestIdentity(),
        )
        assertEquals(
            PublicRankedQueueStatus.MATCHED,
            firstMatch.status,
        )
        val firstRoom = requireNotNull(firstMatch.roomSnapshot)
        val firstFormationAt = now

        /*
         * Preserve the real recent formation history while dropping old
         * resource surfaces. This emulates the state relevant to a rematch:
         * no active room can satisfy the queue query, but the exact cohort is
         * still inside the all-human anti-repeat cooldown window.
         */
        val stateAfterFirstFormation =
            firstStore.snapshotPersistentState()

        val rematchState = stateAfterFirstFormation.copy(
            rooms = emptyList(),
            matches = emptyList(),
            actionResults = emptyList(),
        )

        val secondStore = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            resourcePolicy = policy,
            accountIdFactory = {
                accountSequence += 1
                "account-$accountSequence"
            },
            publicRankedFormationEntropy = ZeroEntropy(),
        )

        secondStore.restorePersistentState(
            state = rematchState,
        )

        now = 61_001L
        enqueueCohort(
            store = secondStore,
            syntheticAccounts = syntheticAccounts,
            humanAccount = humanAccount,
        )

        now = 81_001L
        assertTrue(secondStore.advanceAuthoritativeTime())

        now = 101_001L
        assertTrue(secondStore.advanceAuthoritativeTime())

        now = 121_001L
        assertTrue(secondStore.advanceAuthoritativeTime())

        val secondMatch = secondStore.getPublicRankedQueueStatus(
            humanAccount.toRequestIdentity(),
        )
        assertEquals(
            PublicRankedQueueStatus.MATCHED,
            secondMatch.status,
        )

        val secondRoom = requireNotNull(secondMatch.roomSnapshot)

        assertNotEquals(
            firstRoom.roomId,
            secondRoom.roomId,
        )
        assertEquals(
            firstRoom.players
                .map { player -> player.playerId }
                .toSet(),
            secondRoom.players
                .map { player -> player.playerId }
                .toSet(),
        )
        assertEquals(
            1,
            secondRoom.players.count { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            },
        )
        assertEquals(
            3,
            secondRoom.players.count { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.SYNTHETIC
            },
        )
        assertTrue(
            now - firstFormationAt <
                policy.publicRankedExactCohortCooldownMillis,
        )
    }

    private fun enqueueCohort(
        store: InMemoryOnlineServerStore,
        syntheticAccounts: List<OnlineServerAccount>,
        humanAccount: OnlineServerAccount,
    ) {
        syntheticAccounts.forEachIndexed { index, account ->
            val result = store.enqueuePublicRanked(
                request = request(
                    playerId = account.playerId,
                    tableCode = "S0${index + 1}",
                ),
                identity = account.toRequestIdentity(),
            )

            assertEquals(
                PublicRankedQueueStatus.QUEUED,
                result.status,
            )
        }

        val humanResult = store.enqueuePublicRanked(
            request = request(
                playerId = humanAccount.playerId,
                tableCode = "H01",
            ),
            identity = humanAccount.toRequestIdentity(),
        )

        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            humanResult.status,
        )
        assertEquals(
            1,
            humanResult.queuePosition,
        )
    }

    private fun configureAccount(
        store: InMemoryOnlineServerStore,
        account: OnlineServerAccount,
        tableCode: String,
    ) {
        requireNotNull(
            store.updateAccountProfile(
                accountId = account.accountId,
                publicDisplayName = "Jogador Teste",
                tableName = tableCode,
            ),
        )
    }

    private fun request(
        playerId: String,
        tableCode: String,
    ) = CreateOnlineRoomRequestDto(
        localPlayerId = playerId,
        playerName = tableCode,
    )

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "rematch-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )

    private class ZeroEntropy : PublicRankedFormationEntropy {
        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            return 0
        }

        override fun nextNonceHex(byteCount: Int): String {
            require(byteCount > 0)
            return "cd".repeat(byteCount)
        }
    }
}
