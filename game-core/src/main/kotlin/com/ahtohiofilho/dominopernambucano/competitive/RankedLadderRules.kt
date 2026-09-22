package com.ahtohiofilho.dominopernambucano.competitive

import java.math.BigInteger

data class RankedLadderStats(
    val victories: Long = 0L,
    val games: Long = 0L,
    val teamBalance: Long = 0L,
    val individualPoints: Long = 0L,
    val assists: Long = 0L,
    val touchesGiven: Long = 0L,
    val automaticRounds: Long = 0L,
    val automaticPlays: Long = 0L,
    val timeoutRounds: Long = 0L,
) {
    init {
        require(victories >= 0L)
        require(games >= 0L)
        require(victories <= games)
        require(individualPoints >= 0L)
        require(assists >= 0L)
        require(touchesGiven >= 0L)
        require(automaticRounds >= 0L)
        require(automaticPlays >= 0L)
        require(timeoutRounds >= 0L)
    }

    val isEligible: Boolean
        get() = games > 0L

    val defeats: Long
        get() = games - victories

    val victoryBalance: Long
        get() = victories - defeats

    val scoreNumerator: Long
        get() = victories + 1L

    val scoreDenominator: Long
        get() = games + 2L
}

data class RankedLadderEntry(
    val technicalId: String,
    val stats: RankedLadderStats,
) {
    init {
        require(technicalId.isNotBlank())
    }
}

val RankedLadderEntryComparator: Comparator<RankedLadderEntry> =
    rankedLadderEntryComparator(
        rankingRuleVersion = RANKING_RULE_VERSION_V1,
    )

fun rankedLadderEntryComparator(
    rankingRuleVersion: Int,
): Comparator<RankedLadderEntry> {
    requireSupportedRankingRuleVersion(rankingRuleVersion)

    return Comparator { left, right ->
        compareRankedLadderEntries(
            left = left,
            right = right,
            rankingRuleVersion = rankingRuleVersion,
        )
    }
}

fun rankEligibleEntries(
    entries: Collection<RankedLadderEntry>,
    rankingRuleVersion: Int = CURRENT_RANKING_RULE_VERSION,
): List<RankedLadderEntry> {
    return entries
        .filter { entry -> entry.stats.isEligible }
        .sortedWith(
            rankedLadderEntryComparator(
                rankingRuleVersion = rankingRuleVersion,
            ),
        )
}

fun compareRankedLadderEntries(
    left: RankedLadderEntry,
    right: RankedLadderEntry,
    rankingRuleVersion: Int = CURRENT_RANKING_RULE_VERSION,
): Int {
    requireSupportedRankingRuleVersion(rankingRuleVersion)

    return when (rankingRuleVersion) {
        RANKING_RULE_VERSION_V1 ->
            compareRankedLadderEntriesV1(
                left = left,
                right = right,
            )

        RANKING_RULE_VERSION_V2 ->
            compareRankedLadderEntriesV2(
                left = left,
                right = right,
            )

        RANKING_RULE_VERSION_V3 ->
            compareRankedLadderEntriesV3(
                left = left,
                right = right,
            )

        else -> error("Versão de ranking não suportada.")
    }
}

fun areRankedLadderEntriesPubliclyTied(
    left: RankedLadderEntry,
    right: RankedLadderEntry,
    rankingRuleVersion: Int,
): Boolean {
    requireSupportedRankingRuleVersion(rankingRuleVersion)

    return when (rankingRuleVersion) {
        RANKING_RULE_VERSION_V1 -> false
        RANKING_RULE_VERSION_V2 ->
            compareRankedLadderV2Criteria(
                left = left.stats,
                right = right.stats,
            ) == 0

        RANKING_RULE_VERSION_V3 ->
            compareRankedLadderV3Criteria(
                left = left.stats,
                right = right.stats,
            ) == 0

        else -> error("Versão de ranking não suportada.")
    }
}

private fun compareRankedLadderEntriesV1(
    left: RankedLadderEntry,
    right: RankedLadderEntry,
): Int {
    val scoreComparison = compareRankedScores(
        left = left.stats,
        right = right.stats,
    )

    if (scoreComparison != 0) {
        return scoreComparison
    }

    compareDescending(
        left = left.stats.teamBalance,
        right = right.stats.teamBalance,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.stats.individualPoints,
        right = right.stats.individualPoints,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.stats.touchesGiven,
        right = right.stats.touchesGiven,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareAscending(
        left = left.stats.automaticRounds,
        right = right.stats.automaticRounds,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    return left.technicalId.compareTo(right.technicalId)
}

private fun compareRankedLadderEntriesV2(
    left: RankedLadderEntry,
    right: RankedLadderEntry,
): Int {
    val criteriaComparison = compareRankedLadderV2Criteria(
        left = left.stats,
        right = right.stats,
    )

    if (criteriaComparison != 0) {
        return criteriaComparison
    }

    return left.technicalId.compareTo(right.technicalId)
}

private fun compareRankedLadderV2Criteria(
    left: RankedLadderStats,
    right: RankedLadderStats,
): Int {
    compareDescending(
        left = left.victoryBalance,
        right = right.victoryBalance,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.teamBalance,
        right = right.teamBalance,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.individualPoints,
        right = right.individualPoints,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.assists,
        right = right.assists,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.touchesGiven,
        right = right.touchesGiven,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    return compareAscending(
        left = left.automaticPlays,
        right = right.automaticPlays,
    )
}

private fun compareRankedLadderEntriesV3(
    left: RankedLadderEntry,
    right: RankedLadderEntry,
): Int {
    val criteriaComparison = compareRankedLadderV3Criteria(
        left = left.stats,
        right = right.stats,
    )

    if (criteriaComparison != 0) {
        return criteriaComparison
    }

    return left.technicalId.compareTo(right.technicalId)
}

private fun compareRankedLadderV3Criteria(
    left: RankedLadderStats,
    right: RankedLadderStats,
): Int {
    compareDescending(
        left = left.victoryBalance,
        right = right.victoryBalance,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.teamBalance,
        right = right.teamBalance,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.individualPoints,
        right = right.individualPoints,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.assists,
        right = right.assists,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    compareDescending(
        left = left.touchesGiven,
        right = right.touchesGiven,
    ).takeIf { comparison -> comparison != 0 }
        ?.let { comparison -> return comparison }

    return compareAscending(
        left = left.timeoutRounds,
        right = right.timeoutRounds,
    )
}

private fun compareRankedScores(
    left: RankedLadderStats,
    right: RankedLadderStats,
): Int {
    val leftScaled = BigInteger.valueOf(left.scoreNumerator)
        .multiply(BigInteger.valueOf(right.scoreDenominator))
    val rightScaled = BigInteger.valueOf(right.scoreNumerator)
        .multiply(BigInteger.valueOf(left.scoreDenominator))

    return rightScaled.compareTo(leftScaled)
}

private fun requireSupportedRankingRuleVersion(
    rankingRuleVersion: Int,
) {
    require(
        rankingRuleVersion == RANKING_RULE_VERSION_V1 ||
            rankingRuleVersion == RANKING_RULE_VERSION_V2 ||
            rankingRuleVersion == RANKING_RULE_VERSION_V3,
    ) {
        "Versão de ranking não suportada: $rankingRuleVersion"
    }
}

private fun compareDescending(
    left: Long,
    right: Long,
): Int = right.compareTo(left)

private fun compareAscending(
    left: Long,
    right: Long,
): Int = left.compareTo(right)
