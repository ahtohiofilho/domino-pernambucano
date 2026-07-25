package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankedMatchFormationIntegrityTest {
    @Test
    fun oldest_waiter_is_preserved_while_recent_exact_cohort_is_bypassed() {
        val candidates = (1..5).map { number ->
            candidate(number)
        }
        val history = listOf(
            historyEntry(
                accountIdsBySeat = listOf(
                    "account-1",
                    "account-2",
                    "account-3",
                    "account-4",
                ),
                completedAtEpochMillis = 900L,
            ),
        )
        val policy = OnlineServerStoreResourcePolicy(
            publicRankedFormationLookaheadSize = 5,
            publicRankedExactCohortCooldownMillis = 1_000L,
        )

        val plan = planPublicRankedMatchFormation(
            queuedCandidates = candidates,
            recentHistory = history,
            nowEpochMillis = 1_000L,
            policy = policy,
            entropy = SequenceEntropy(),
        )

        assertEquals(
            listOf(
                "account-1",
                "account-2",
                "account-3",
                "account-5",
            ),
            requireNotNull(plan)
                .selectedCandidatesInQueueOrder
                .map { candidate -> candidate.accountId },
        )
    }

    @Test
    fun exact_cohort_waits_during_cooldown_when_no_alternative_exists() {
        val plan = planPublicRankedMatchFormation(
            queuedCandidates = (1..4).map { number ->
                candidate(number)
            },
            recentHistory = listOf(
                historyEntry(
                    accountIdsBySeat = listOf(
                        "account-1",
                        "account-2",
                        "account-3",
                        "account-4",
                    ),
                    completedAtEpochMillis = 900L,
                ),
            ),
            nowEpochMillis = 1_000L,
            policy = OnlineServerStoreResourcePolicy(
                publicRankedFormationLookaheadSize = 4,
                publicRankedExactCohortCooldownMillis = 1_000L,
            ),
            entropy = SequenceEntropy(),
        )

        assertNull(plan)
    }

    @Test
    fun seat_plan_avoids_repeating_previous_partnership_when_possible() {
        val previousSeats = listOf(
            "account-1",
            "account-2",
            "account-3",
            "account-4",
        )
        val plan = requireNotNull(
            planPublicRankedMatchFormation(
                queuedCandidates = (1..4).map { number ->
                    candidate(number)
                },
                recentHistory = listOf(
                    historyEntry(
                        accountIdsBySeat = previousSeats,
                        completedAtEpochMillis = 900L,
                    ),
                ),
                nowEpochMillis = 1_000L,
                policy = OnlineServerStoreResourcePolicy(
                    publicRankedExactCohortCooldownMillis = 0L,
                ),
                entropy = SequenceEntropy(
                    values = listOf(0, 0, 0, 0, 0),
                ),
            ),
        )
        val accountIdsBySeat = plan.candidatesBySeat.map { candidate ->
            candidate.accountId
        }
        val newPartnerPairs = setOf(
            setOf(accountIdsBySeat[0], accountIdsBySeat[2]),
            setOf(accountIdsBySeat[1], accountIdsBySeat[3]),
        )
        val previousPartnerPairs = setOf(
            setOf(previousSeats[0], previousSeats[2]),
            setOf(previousSeats[1], previousSeats[3]),
        )

        assertTrue(newPartnerPairs.intersect(previousPartnerPairs).isEmpty())
        assertEquals(0, plan.repeatedPartnerScore)
    }

    @Test
    fun audit_commitment_binds_server_nonce_queue_order_and_seat_order() {
        val queueOrder = listOf(
            "account-1",
            "account-2",
            "account-3",
            "account-4",
        )
        val seats = listOf(
            "account-3",
            "account-1",
            "account-2",
            "account-4",
        )
        val nonce = "ab".repeat(32)
        val commitment = createPublicRankedFormationAuditCommitment(
            roomId = "server-room-1",
            matchId = "server-match-1",
            formedAtEpochMillis = 1_000L,
            selectedAccountIdsInQueueOrder = queueOrder,
            accountIdsBySeat = seats,
            auditNonce = nonce,
        )

        assertTrue(commitment.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(
            commitment,
            createPublicRankedFormationAuditCommitment(
                roomId = "server-room-1",
                matchId = "server-match-1",
                formedAtEpochMillis = 1_000L,
                selectedAccountIdsInQueueOrder = queueOrder,
                accountIdsBySeat = seats.reversed(),
                auditNonce = nonce,
            ),
        )
        assertNotEquals(
            commitment,
            createPublicRankedFormationAuditCommitment(
                roomId = "server-room-1",
                matchId = "server-match-1",
                formedAtEpochMillis = 1_000L,
                selectedAccountIdsInQueueOrder = queueOrder,
                accountIdsBySeat = seats,
                auditNonce = "cd".repeat(32),
            ),
        )
    }

    @Test
    fun formation_history_and_audit_commitment_survive_restart() {
        var accountSequence = 0
        val source = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = {
                accountSequence++
                "account-$accountSequence"
            },
            publicRankedFormationEntropy = SequenceEntropy(
                values = listOf(0, 2, 1, 1, 0),
                nonceHex = "ef".repeat(32),
            ),
        )
        val accounts = (1..4).map { number ->
            requireNotNull(
                source.promoteAccount(playerId = "player-$number"),
            )
        }

        accounts.forEachIndexed { index, account ->
            source.enqueuePublicRanked(
                request = CreateOnlineRoomRequestDto(
                    localPlayerId = account.playerId,
                    playerName = "Jogador ${index + 1}",
                ),
                identity = account.toRequestIdentity(),
            )
        }

        val state = source.snapshotPersistentState()
        val formation = state.publicRankedFormationHistory.single()

        assertEquals(
            accounts.map { account -> account.accountId },
            formation.selectedAccountIdsInQueueOrder,
        )
        assertEquals(
            formation.auditCommitment,
            createPublicRankedFormationAuditCommitment(
                roomId = formation.roomId,
                matchId = formation.matchId,
                formedAtEpochMillis = formation.formedAtEpochMillis,
                selectedAccountIdsInQueueOrder =
                    formation.selectedAccountIdsInQueueOrder,
                accountIdsBySeat = formation.accountIdsBySeat,
                auditNonce = formation.auditNonce,
            ),
        )

        val restored = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        restored.restorePersistentState(state)

        assertEquals(
            formation,
            restored.snapshotPersistentState()
                .publicRankedFormationHistory
                .single(),
        )
        assertNotNull(restored.getRoomSnapshot(formation.roomId))
    }

    private fun candidate(number: Int) =
        PublicRankedFormationCandidate(
            playerId = "player-$number",
            accountId = "account-$number",
            playerName = "Jogador $number",
            enqueuedAtEpochMillis = number.toLong(),
        )

    private fun historyEntry(
        accountIdsBySeat: List<String>,
        completedAtEpochMillis: Long?,
    ) = PublicRankedFormationHistoryEntry(
        roomId = "room-history",
        matchId = "match-history",
        formedAtEpochMillis = 100L,
        completedAtEpochMillis = completedAtEpochMillis,
        selectedAccountIdsInQueueOrder =
            accountIdsBySeat.sorted(),
        accountIdsBySeat = accountIdsBySeat,
        auditNonce = "00".repeat(32),
        auditCommitment = "11".repeat(32),
    )

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "ranked-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )

    private class SequenceEntropy(
        private val values: List<Int> = emptyList(),
        private val nonceHex: String = "ab".repeat(32),
    ) : PublicRankedFormationEntropy {
        private var index = 0

        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            val value = values.getOrElse(index) { 0 }
            index++
            return Math.floorMod(value, bound)
        }

        override fun nextNonceHex(byteCount: Int): String {
            require(nonceHex.length == byteCount * 2)
            return nonceHex
        }
    }
}
