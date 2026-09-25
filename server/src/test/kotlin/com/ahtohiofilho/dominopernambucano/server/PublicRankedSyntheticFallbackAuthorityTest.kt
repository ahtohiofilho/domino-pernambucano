package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankedSyntheticFallbackAuthorityTest {
    @Test
    fun synthetic_only_queue_never_forms_a_match() {
        val plan = plan(
            humanCount = 0,
            syntheticCount = 4,
            nowEpochMillis = 120_000L,
        )

        assertNull(plan)
    }

    @Test
    fun four_humans_form_immediately_without_synthetic_fallback() {
        val plan = requireNotNull(
            plan(
                humanCount = 4,
                syntheticCount = 0,
                nowEpochMillis = 0L,
            ),
        )

        assertEquals(4, plan.humanCount)
        assertEquals(0, plan.syntheticCount)
        assertEquals(0L, plan.oldestHumanWaitMillis)
    }

    @Test
    fun synthetic_fallback_unlocks_one_seat_at_20_seconds() {
        assertNull(
            plan(
                humanCount = 3,
                syntheticCount = 1,
                nowEpochMillis = 19_999L,
            ),
        )

        val plan = requireNotNull(
            plan(
                humanCount = 3,
                syntheticCount = 1,
                nowEpochMillis = 20_000L,
            ),
        )

        assertEquals(3, plan.humanCount)
        assertEquals(1, plan.syntheticCount)
    }

    @Test
    fun synthetic_fallback_unlocks_two_seats_at_40_seconds() {
        assertNull(
            plan(
                humanCount = 2,
                syntheticCount = 2,
                nowEpochMillis = 39_999L,
            ),
        )

        val plan = requireNotNull(
            plan(
                humanCount = 2,
                syntheticCount = 2,
                nowEpochMillis = 40_000L,
            ),
        )

        assertEquals(2, plan.humanCount)
        assertEquals(2, plan.syntheticCount)
    }

    @Test
    fun synthetic_fallback_unlocks_three_seats_at_60_seconds() {
        assertNull(
            plan(
                humanCount = 1,
                syntheticCount = 3,
                nowEpochMillis = 59_999L,
            ),
        )

        val plan = requireNotNull(
            plan(
                humanCount = 1,
                syntheticCount = 3,
                nowEpochMillis = 60_000L,
            ),
        )

        assertEquals(1, plan.humanCount)
        assertEquals(3, plan.syntheticCount)
    }

    @Test
    fun humans_are_preferred_even_when_synthetics_entered_queue_first() {
        val candidates = buildList {
            repeat(4) { index ->
                add(
                    candidate(
                        number = index + 1,
                        participantType =
                            OnlineParticipantTypeDto.SYNTHETIC,
                        enqueuedAtEpochMillis = index.toLong(),
                    ),
                )
            }
            repeat(4) { index ->
                add(
                    candidate(
                        number = index + 5,
                        participantType =
                            OnlineParticipantTypeDto.HUMAN,
                        enqueuedAtEpochMillis = 100L + index,
                    ),
                )
            }
        }

        val plan = requireNotNull(
            planPublicRankedMatchFormation(
                queuedCandidates = candidates,
                recentHistory = emptyList(),
                nowEpochMillis = 100L,
                policy = fallbackPolicy(),
                entropy = ZeroEntropy(),
            ),
        )

        assertEquals(4, plan.humanCount)
        assertEquals(0, plan.syntheticCount)
        assertTrue(
            plan.selectedCandidatesInQueueOrder.all { candidate ->
                candidate.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            },
        )
    }

    @Test
    fun ticker_forms_fallback_and_human_position_ignores_standby_synthetics() {
        var now = 1_000L
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            resourcePolicy = fallbackPolicy(),
            accountIdFactory = {
                accountSequence += 1
                "account-$accountSequence"
            },
            publicRankedFormationEntropy = ZeroEntropy(),
        )

        val syntheticAccounts = (1..3).map { number ->
            requireNotNull(
                store.promoteSyntheticAccount(
                    playerId = "synthetic-$number",
                    expectedAccountId = null,
                ),
            )
        }
        val humanAccount = requireNotNull(
            store.promoteAccount(playerId = "human-1"),
        )

        syntheticAccounts.forEachIndexed { index, account ->
            configureAccount(
                store = store,
                account = account,
                tableCode = "S0${index + 1}",
            )
            val queued = store.enqueuePublicRanked(
                request = request(
                    playerId = account.playerId,
                    tableCode = "S0${index + 1}",
                ),
                identity = account.toRequestIdentity(),
            )
            assertEquals(PublicRankedQueueStatus.QUEUED, queued.status)
        }

        configureAccount(
            store = store,
            account = humanAccount,
            tableCode = "H01",
        )
        val humanQueued = store.enqueuePublicRanked(
            request = request(
                playerId = humanAccount.playerId,
                tableCode = "H01",
            ),
            identity = humanAccount.toRequestIdentity(),
        )

        assertEquals(PublicRankedQueueStatus.QUEUED, humanQueued.status)
        assertEquals(1, humanQueued.queuePosition)

        assertEquals(
            listOf("H01"),
            humanQueued.participantCodes,
        )

        now = 20_999L
        assertFalse(store.advanceAuthoritativeTime())

        now = 21_000L
        assertTrue(store.advanceAuthoritativeTime())
        assertEquals(
            listOf("H01", "S01"),
            store.getPublicRankedQueueStatus(
                humanAccount.toRequestIdentity(),
            ).participantCodes,
        )

        now = 40_999L
        assertFalse(store.advanceAuthoritativeTime())

        now = 41_000L
        assertTrue(store.advanceAuthoritativeTime())
        assertEquals(
            listOf("H01", "S01", "S02"),
            store.getPublicRankedQueueStatus(
                humanAccount.toRequestIdentity(),
            ).participantCodes,
        )

        now = 60_999L
        assertFalse(store.advanceAuthoritativeTime())

        now = 61_000L
        assertTrue(store.advanceAuthoritativeTime())

        val matched = store.getPublicRankedQueueStatus(
            humanAccount.toRequestIdentity(),
        )
        assertEquals(PublicRankedQueueStatus.MATCHED, matched.status)
        val room = requireNotNull(matched.roomSnapshot)
        assertNotNull(room.matchId)
        assertEquals(
            1,
            room.players.count { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            },
        )
        assertEquals(
            3,
            room.players.count { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.SYNTHETIC
            },
        )
    }

    @Test
    fun synthetic_fallback_admission_uses_entropy_instead_of_fifo_order() {
        var now = 1_000L
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            resourcePolicy = fallbackPolicy(),
            accountIdFactory = {
                accountSequence += 1
                "account-$accountSequence"
            },
            /*
             * Six standby synthetics are queued S01..S06.
             * Admission picks:
             *  - index 4 of 6 -> S05
             *  - index 1 of remaining 5 -> S02
             *  - index 2 of remaining 4 -> S04
             * proving that fallback admission is no longer FIFO.
             */
            publicRankedFormationEntropy = SequenceEntropy(
                values = listOf(4, 1, 2),
            ),
        )

        val syntheticAccounts = (1..6).map { number ->
            requireNotNull(
                store.promoteSyntheticAccount(
                    playerId = "variance-synthetic-$number",
                    expectedAccountId = null,
                ),
            )
        }

        syntheticAccounts.forEachIndexed { index, account ->
            val code = "S0${index + 1}"
            configureAccount(
                store = store,
                account = account,
                tableCode = code,
            )
            assertEquals(
                PublicRankedQueueStatus.QUEUED,
                store.enqueuePublicRanked(
                    request = request(
                        playerId = account.playerId,
                        tableCode = code,
                    ),
                    identity = account.toRequestIdentity(),
                ).status,
            )
        }

        val humanAccount = requireNotNull(
            store.promoteAccount(
                playerId = "variance-human-1",
            ),
        )
        configureAccount(
            store = store,
            account = humanAccount,
            tableCode = "H01",
        )
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            store.enqueuePublicRanked(
                request = request(
                    playerId = humanAccount.playerId,
                    tableCode = "H01",
                ),
                identity = humanAccount.toRequestIdentity(),
            ).status,
        )

        now = 21_000L
        assertTrue(store.advanceAuthoritativeTime())
        assertEquals(
            listOf("H01", "S05"),
            store.getPublicRankedQueueStatus(
                humanAccount.toRequestIdentity(),
            ).participantCodes,
        )

        now = 41_000L
        assertTrue(store.advanceAuthoritativeTime())
        assertEquals(
            listOf("H01", "S02", "S05"),
            store.getPublicRankedQueueStatus(
                humanAccount.toRequestIdentity(),
            ).participantCodes,
        )

        now = 61_000L
        assertTrue(store.advanceAuthoritativeTime())

        val matched = store.getPublicRankedQueueStatus(
            humanAccount.toRequestIdentity(),
        )
        assertEquals(
            PublicRankedQueueStatus.MATCHED,
            matched.status,
        )

        val room = requireNotNull(matched.roomSnapshot)
        val syntheticCodes = room.players
            .filter { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.SYNTHETIC
            }
            .map { player -> player.name }
            .toSet()

        assertEquals(
            setOf("S02", "S04", "S05"),
            syntheticCodes,
        )
        assertEquals(
            1,
            room.players.count { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            },
        )
        assertEquals(
            "H01",
            room.players.single { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            }.name,
        )
    }
    @Test
    fun new_human_arrival_resets_next_synthetic_admission_window() {
        var now = 1_000L
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            resourcePolicy = fallbackPolicy(),
            accountIdFactory = {
                accountSequence += 1
                "account-$accountSequence"
            },
            publicRankedFormationEntropy = ZeroEntropy(),
        )

        val syntheticAccounts = (1..3).map { number ->
            requireNotNull(
                store.promoteSyntheticAccount(
                    playerId = "reset-synthetic-$number",
                    expectedAccountId = null,
                ),
            )
        }
        syntheticAccounts.forEachIndexed { index, account ->
            val code = "S0${index + 1}"
            configureAccount(
                store = store,
                account = account,
                tableCode = code,
            )
            assertEquals(
                PublicRankedQueueStatus.QUEUED,
                store.enqueuePublicRanked(
                    request = request(
                        playerId = account.playerId,
                        tableCode = code,
                    ),
                    identity = account.toRequestIdentity(),
                ).status,
            )
        }

        val firstHuman = requireNotNull(
            store.promoteAccount(playerId = "reset-human-1"),
        )
        configureAccount(
            store = store,
            account = firstHuman,
            tableCode = "H01",
        )
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            store.enqueuePublicRanked(
                request = request(
                    playerId = firstHuman.playerId,
                    tableCode = "H01",
                ),
                identity = firstHuman.toRequestIdentity(),
            ).status,
        )

        now = 21_000L
        assertTrue(store.advanceAuthoritativeTime())
        assertEquals(
            listOf("H01", "S01"),
            store.getPublicRankedQueueStatus(
                firstHuman.toRequestIdentity(),
            ).participantCodes,
        )

        now = 25_000L
        val secondHuman = requireNotNull(
            store.promoteAccount(playerId = "reset-human-2"),
        )
        configureAccount(
            store = store,
            account = secondHuman,
            tableCode = "H02",
        )
        val secondHumanQueued = store.enqueuePublicRanked(
            request = request(
                playerId = secondHuman.playerId,
                tableCode = "H02",
            ),
            identity = secondHuman.toRequestIdentity(),
        )
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            secondHumanQueued.status,
        )
        assertEquals(
            listOf("H01", "H02", "S01"),
            secondHumanQueued.participantCodes,
        )

        now = 44_999L
        assertFalse(store.advanceAuthoritativeTime())
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            store.getPublicRankedQueueStatus(
                firstHuman.toRequestIdentity(),
            ).status,
        )

        now = 45_000L
        assertTrue(store.advanceAuthoritativeTime())

        val matched = store.getPublicRankedQueueStatus(
            firstHuman.toRequestIdentity(),
        )
        assertEquals(
            PublicRankedQueueStatus.MATCHED,
            matched.status,
        )
        val room = requireNotNull(matched.roomSnapshot)
        assertEquals(
            2,
            room.players.count { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            },
        )
        assertEquals(
            2,
            room.players.count { player ->
                player.participantType ==
                    OnlineParticipantTypeDto.SYNTHETIC
            },
        )
    }

    private fun plan(
        humanCount: Int,
        syntheticCount: Int,
        nowEpochMillis: Long,
    ): PublicRankedMatchFormationPlan? {
        require(humanCount + syntheticCount == 4)

        val candidates = buildList {
            repeat(humanCount) { index ->
                add(
                    candidate(
                        number = index + 1,
                        participantType =
                            OnlineParticipantTypeDto.HUMAN,
                    ),
                )
            }
            repeat(syntheticCount) { index ->
                add(
                    candidate(
                        number = humanCount + index + 1,
                        participantType =
                            OnlineParticipantTypeDto.SYNTHETIC,
                    ),
                )
            }
        }

        return planPublicRankedMatchFormation(
            queuedCandidates = candidates,
            recentHistory = emptyList(),
            nowEpochMillis = nowEpochMillis,
            policy = fallbackPolicy(),
            entropy = ZeroEntropy(),
        )
    }

    private fun candidate(
        number: Int,
        participantType: OnlineParticipantTypeDto,
        enqueuedAtEpochMillis: Long = 0L,
    ) = PublicRankedFormationCandidate(
        playerId = "player-$number",
        accountId = "account-$number",
        playerName = "P$number",
        enqueuedAtEpochMillis = enqueuedAtEpochMillis,
        participantType = participantType,
    )

    private fun fallbackPolicy() = OnlineServerStoreResourcePolicy(
        publicRankedSyntheticFallbackInitialDelayMillis = 20_000L,
        publicRankedSyntheticFallbackAdditionalSeatDelayMillis = 20_000L,
        publicRankedExactCohortCooldownMillis = 0L,
        pruneIntervalMillis = 60_000L,
    )

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
            sessionId = "fallback-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )

    private class SequenceEntropy(
        private val values: List<Int>,
    ) : PublicRankedFormationEntropy {
        private var index = 0

        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            val value = values.getOrElse(index) { 0 }
            index += 1
            return Math.floorMod(value, bound)
        }

        override fun nextNonceHex(byteCount: Int): String {
            require(byteCount > 0)
            return "ef".repeat(byteCount)
        }
    }
    private class ZeroEntropy : PublicRankedFormationEntropy {
        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            return 0
        }

        override fun nextNonceHex(byteCount: Int): String {
            require(byteCount > 0)
            return "ab".repeat(byteCount)
        }
    }
}
