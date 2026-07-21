package com.ahtohiofilho.dominopernambucano.competitive

import java.math.BigInteger

data class RankedLadderStats(
    val victories: Long = 0L,
    val games: Long = 0L,
    val teamBalance: Long = 0L,
    val individualPoints: Long = 0L,
    val touchesGiven: Long = 0L,
    val automaticRounds: Long = 0L,
) {
    init {
        require(victories >= 0L)
        require(games >= 0L)
        require(victories <= games)
        require(individualPoints >= 0L)
        require(touchesGiven >= 0L)
        require(automaticRounds >= 0L)
    }

    val isEligible: Boolean
        get() = games > 0L

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
    Comparator { left, right ->
        compareRankedLadderEntries(
            left = left,
            right = right,
        )
    }

fun rankEligibleEntries(
    entries: Collection<RankedLadderEntry>,
): List<RankedLadderEntry> {
    return entries
        .filter { entry -> entry.stats.isEligible }
        .sortedWith(RankedLadderEntryComparator)
}

fun compareRankedLadderEntries(
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

private fun compareDescending(
    left: Long,
    right: Long,
): Int = right.compareTo(left)

private fun compareAscending(
    left: Long,
    right: Long,
): Int = left.compareTo(right)
