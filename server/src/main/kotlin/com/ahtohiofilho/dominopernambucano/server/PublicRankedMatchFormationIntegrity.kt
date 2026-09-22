package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import java.security.MessageDigest
import java.security.SecureRandom

interface PublicRankedFormationEntropy {
    fun nextInt(bound: Int): Int

    fun nextNonceHex(byteCount: Int = 32): String
}

class SecurePublicRankedFormationEntropy(
    private val secureRandom: SecureRandom = SecureRandom(),
) : PublicRankedFormationEntropy {
    override fun nextInt(bound: Int): Int {
        require(bound > 0)
        return secureRandom.nextInt(bound)
    }

    override fun nextNonceHex(byteCount: Int): String {
        require(byteCount > 0)
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return bytes.toHexString()
    }
}

internal data class PublicRankedFormationCandidate(
    val playerId: String,
    val accountId: String,
    val playerName: String,
    val enqueuedAtEpochMillis: Long,
    val participantType: OnlineParticipantTypeDto =
        OnlineParticipantTypeDto.HUMAN,
)

internal data class PublicRankedMatchFormationPlan(
    val selectedCandidatesInQueueOrder:
        List<PublicRankedFormationCandidate>,
    val candidatesBySeat:
        List<PublicRankedFormationCandidate>,
    val auditNonce: String,
    val repeatedEncounterScore: Int,
    val repeatedPartnerScore: Int,
    val humanCount: Int,
    val syntheticCount: Int,
    val oldestHumanWaitMillis: Long,
)

internal fun planPublicRankedMatchFormation(
    queuedCandidates: List<PublicRankedFormationCandidate>,
    recentHistory: List<PublicRankedFormationHistoryEntry>,
    nowEpochMillis: Long,
    policy: OnlineServerStoreResourcePolicy,
    entropy: PublicRankedFormationEntropy,
): PublicRankedMatchFormationPlan? {
    if (queuedCandidates.size < 4) {
        return null
    }

    val history = recentHistory.filter { entry ->
        val referenceEpochMillis =
            entry.completedAtEpochMillis
                ?: entry.formedAtEpochMillis
        (
            nowEpochMillis - referenceEpochMillis
        ).coerceAtLeast(0L) <=
            policy.publicRankedFormationHistoryRetentionMillis
    }
    val prioritizedCandidates = queuedCandidates.sortedWith(
        compareBy<PublicRankedFormationCandidate> { candidate ->
            when (candidate.participantType) {
                OnlineParticipantTypeDto.HUMAN -> 0
                OnlineParticipantTypeDto.SYNTHETIC -> 1
                OnlineParticipantTypeDto.APPLICATION -> 2
            }
        }.thenBy { candidate ->
            candidate.enqueuedAtEpochMillis
        }.thenBy { candidate ->
            candidate.accountId
        },
    )
    val window = prioritizedCandidates.take(
        policy.publicRankedFormationLookaheadSize.coerceAtMost(
            prioritizedCandidates.size,
        ),
    )
    val oldestHuman = window.firstOrNull { candidate ->
        candidate.participantType == OnlineParticipantTypeDto.HUMAN
    } ?: return null
    val oldestHumanWaitMillis = (
        nowEpochMillis - oldestHuman.enqueuedAtEpochMillis
    ).coerceAtLeast(0L)
    val alternatives = window.filterNot { candidate ->
        candidate.accountId == oldestHuman.accountId
    }
    val fallbackEligibleQuartets = combinationsOfThree(alternatives)
        .map { companions ->
            listOf(oldestHuman) + companions
        }
        .filter { quartet ->
            isSyntheticFallbackEligible(
                candidates = quartet,
                oldestHumanWaitMillis = oldestHumanWaitMillis,
                policy = policy,
            )
        }

    if (fallbackEligibleQuartets.isEmpty()) {
        return null
    }

    val maximumHumanCount = fallbackEligibleQuartets.maxOf { quartet ->
        quartet.count { candidate ->
            candidate.participantType ==
                OnlineParticipantTypeDto.HUMAN
        }
    }
    val humanPriorityQuartets =
        fallbackEligibleQuartets.filter { quartet ->
            quartet.count { candidate ->
                candidate.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            } == maximumHumanCount
        }
    val candidateQuartets = humanPriorityQuartets.filterNot { quartet ->
        isExactCohortCoolingDown(
            candidates = quartet,
            history = history,
            nowEpochMillis = nowEpochMillis,
            cooldownMillis =
                policy.publicRankedExactCohortCooldownMillis,
        )
    }

    if (candidateQuartets.isEmpty()) {
        return null
    }

    val queueIndexByAccountId = queuedCandidates
        .mapIndexed { index, candidate ->
            candidate.accountId to index
        }
        .toMap()
    val minimumBypassPenalty = candidateQuartets.minOf { quartet ->
        quartet.sumOf { candidate ->
            requireNotNull(
                queueIndexByAccountId[candidate.accountId],
            )
        }
    }
    val fairnessEligibleQuartets = candidateQuartets.filter { quartet ->
        quartet.sumOf { candidate ->
            requireNotNull(
                queueIndexByAccountId[candidate.accountId],
            )
        } == minimumBypassPenalty
    }
    val minimumEncounterScore =
        fairnessEligibleQuartets.minOf { quartet ->
            repeatedEncounterScore(
                candidates = quartet,
                history = history,
            )
        }
    val bestQuartets = fairnessEligibleQuartets.filter { quartet ->
        repeatedEncounterScore(
            candidates = quartet,
            history = history,
        ) == minimumEncounterScore
    }
    val selectedInQueueOrder = bestQuartets[
        entropy.nextInt(bestQuartets.size)
    ].sortedBy { candidate ->
        requireNotNull(
            queueIndexByAccountId[candidate.accountId],
        )
    }
    val seatPlan = chooseSeatPlan(
        candidates = selectedInQueueOrder,
        history = history,
        entropy = entropy,
    )

    return PublicRankedMatchFormationPlan(
        selectedCandidatesInQueueOrder =
            selectedInQueueOrder,
        candidatesBySeat = seatPlan.candidatesBySeat,
        auditNonce = entropy.nextNonceHex(),
        repeatedEncounterScore = minimumEncounterScore,
        repeatedPartnerScore = seatPlan.repeatedPartnerScore,
        humanCount = maximumHumanCount,
        syntheticCount = 4 - maximumHumanCount,
        oldestHumanWaitMillis = oldestHumanWaitMillis,
    )
}

internal fun createPublicRankedFormationAuditCommitment(
    roomId: String,
    matchId: String,
    formedAtEpochMillis: Long,
    selectedAccountIdsInQueueOrder: List<String>,
    accountIdsBySeat: List<String>,
    auditNonce: String,
): String {
    require(selectedAccountIdsInQueueOrder.size == 4)
    require(accountIdsBySeat.size == 4)
    require(
        selectedAccountIdsInQueueOrder.toSet() ==
            accountIdsBySeat.toSet(),
    )
    require(auditNonce.matches(Regex("[0-9a-f]{64}")))

    val canonical = listOf(
        roomId,
        matchId,
        formedAtEpochMillis.toString(),
        selectedAccountIdsInQueueOrder.joinToString(","),
        accountIdsBySeat.joinToString(","),
        auditNonce,
    ).joinToString("|")

    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .toHexString()
}

private fun isSyntheticFallbackEligible(
    candidates: List<PublicRankedFormationCandidate>,
    oldestHumanWaitMillis: Long,
    policy: OnlineServerStoreResourcePolicy,
): Boolean {
    require(candidates.size == 4)

    val humanCount = candidates.count { candidate ->
        candidate.participantType == OnlineParticipantTypeDto.HUMAN
    }
    val syntheticCount = candidates.count { candidate ->
        candidate.participantType == OnlineParticipantTypeDto.SYNTHETIC
    }

    if (humanCount + syntheticCount != candidates.size) {
        return false
    }
    if (humanCount == 0) {
        return false
    }
    if (syntheticCount == 0) {
        return true
    }

    val requiredWaitMillis =
        policy.publicRankedSyntheticFallbackInitialDelayMillis +
            (
                syntheticCount - 1
            ).toLong() *
            policy.publicRankedSyntheticFallbackAdditionalSeatDelayMillis

    return oldestHumanWaitMillis >= requiredWaitMillis
}

private data class SeatPlan(
    val candidatesBySeat: List<PublicRankedFormationCandidate>,
    val repeatedPartnerScore: Int,
)

private fun chooseSeatPlan(
    candidates: List<PublicRankedFormationCandidate>,
    history: List<PublicRankedFormationHistoryEntry>,
    entropy: PublicRankedFormationEntropy,
): SeatPlan {
    require(candidates.size == 4)

    val partnershipPartitions = listOf(
        listOf(
            candidates[0] to candidates[1],
            candidates[2] to candidates[3],
        ),
        listOf(
            candidates[0] to candidates[2],
            candidates[1] to candidates[3],
        ),
        listOf(
            candidates[0] to candidates[3],
            candidates[1] to candidates[2],
        ),
    )
    val scoreByPartition = partnershipPartitions.associateWith { partition ->
        partition.sumOf { (first, second) ->
            recentPartnerCount(
                firstAccountId = first.accountId,
                secondAccountId = second.accountId,
                history = history,
            )
        }
    }
    val minimumPartnerScore = scoreByPartition.values.min()
    val bestPartitions = partnershipPartitions.filter { partition ->
        scoreByPartition.getValue(partition) == minimumPartnerScore
    }
    val selectedPartition = bestPartitions[
        entropy.nextInt(bestPartitions.size)
    ]
    val firstTeam = selectedPartition[0]
    val secondTeam = selectedPartition[1]
    val evenTeamFirst = entropy.nextInt(2) == 0
    val evenTeam = if (evenTeamFirst) {
        firstTeam
    } else {
        secondTeam
    }
    val oddTeam = if (evenTeamFirst) {
        secondTeam
    } else {
        firstTeam
    }
    val orderedEvenTeam = if (entropy.nextInt(2) == 0) {
        listOf(evenTeam.first, evenTeam.second)
    } else {
        listOf(evenTeam.second, evenTeam.first)
    }
    val orderedOddTeam = if (entropy.nextInt(2) == 0) {
        listOf(oddTeam.first, oddTeam.second)
    } else {
        listOf(oddTeam.second, oddTeam.first)
    }

    return SeatPlan(
        candidatesBySeat = listOf(
            orderedEvenTeam[0],
            orderedOddTeam[0],
            orderedEvenTeam[1],
            orderedOddTeam[1],
        ),
        repeatedPartnerScore = minimumPartnerScore,
    )
}

private fun combinationsOfThree(
    candidates: List<PublicRankedFormationCandidate>,
): List<List<PublicRankedFormationCandidate>> {
    val combinations =
        mutableListOf<List<PublicRankedFormationCandidate>>()

    for (first in 0 until candidates.size - 2) {
        for (second in first + 1 until candidates.size - 1) {
            for (third in second + 1 until candidates.size) {
                combinations += listOf(
                    candidates[first],
                    candidates[second],
                    candidates[third],
                )
            }
        }
    }

    return combinations
}

private fun isExactCohortCoolingDown(
    candidates: List<PublicRankedFormationCandidate>,
    history: List<PublicRankedFormationHistoryEntry>,
    nowEpochMillis: Long,
    cooldownMillis: Long,
): Boolean {
    if (cooldownMillis <= 0L) {
        return false
    }

    /*
     * Exact-cohort cooldown is an anti-repeat/anti-collusion rule for human
     * competitive matchmaking. Applying it to server-authorized SYNTHETIC
     * fallback defeats the availability guarantee: one human plus the same
     * three synthetic accounts can visibly fill the lobby while formation is
     * blocked for the full cooldown window.
     *
     * Keep the protection intact for four-human cohorts. Mixed fallback
     * cohorts may rematch immediately; seat planning still minimizes repeated
     * partnerships independently.
     */
    if (
        candidates.any { candidate ->
            candidate.participantType !=
                OnlineParticipantTypeDto.HUMAN
        }
    ) {
        return false
    }

    val accountIds = candidates
        .map { candidate -> candidate.accountId }
        .toSet()

    return history.any { entry ->
        entry.accountIdsBySeat.toSet() == accountIds &&
            (
                nowEpochMillis -
                    (
                        entry.completedAtEpochMillis
                            ?: entry.formedAtEpochMillis
                    )
            ).coerceAtLeast(0L) < cooldownMillis
    }
}

private fun repeatedEncounterScore(
    candidates: List<PublicRankedFormationCandidate>,
    history: List<PublicRankedFormationHistoryEntry>,
): Int {
    var score = 0

    for (first in 0 until candidates.lastIndex) {
        for (second in first + 1..candidates.lastIndex) {
            score += history.count { entry ->
                candidates[first].accountId in
                    entry.accountIdsBySeat &&
                    candidates[second].accountId in
                    entry.accountIdsBySeat
            }
        }
    }

    return score
}

private fun recentPartnerCount(
    firstAccountId: String,
    secondAccountId: String,
    history: List<PublicRankedFormationHistoryEntry>,
): Int {
    val pair = setOf(firstAccountId, secondAccountId)

    return history.count { entry ->
        entry.accountIdsBySeat.size == 4 &&
            (
                setOf(
                    entry.accountIdsBySeat[0],
                    entry.accountIdsBySeat[2],
                ) == pair ||
                    setOf(
                        entry.accountIdsBySeat[1],
                        entry.accountIdsBySeat[3],
                    ) == pair
            )
    }
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
