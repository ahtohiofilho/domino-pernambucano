package com.ahtohiofilho.dominopernambucano.advertising

internal const val FirstInterstitialOpportunityMatch = 3
internal const val InterstitialOpportunityIntervalMatches = 3

internal fun isInterstitialOpportunity(
    completedMatchCount: Int,
): Boolean {
    if (completedMatchCount < FirstInterstitialOpportunityMatch) {
        return false
    }

    return completedMatchCount % InterstitialOpportunityIntervalMatches == 0
}

internal class AdvertisingFrequencyGate(
    private val readCompletedMatchCount: () -> Int,
    private val writeCompletedMatchCount: (Int) -> Unit,
) {
    fun recordCompletedMatch(): Boolean {
        val currentCount = readCompletedMatchCount()
            .coerceAtLeast(0)

        val nextCount = if (currentCount == Int.MAX_VALUE) {
            FirstInterstitialOpportunityMatch
        } else {
            currentCount + 1
        }

        writeCompletedMatchCount(nextCount)

        return isInterstitialOpportunity(
            completedMatchCount = nextCount,
        )
    }
}
