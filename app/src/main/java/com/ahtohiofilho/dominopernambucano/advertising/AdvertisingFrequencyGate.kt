package com.ahtohiofilho.dominopernambucano.advertising

internal fun isInterstitialOpportunity(
    completedMatchCount: Int,
): Boolean {
    return completedMatchCount >= 1
}

internal class AdvertisingFrequencyGate(
    private val readCompletedMatchCount: () -> Int,
    private val writeCompletedMatchCount: (Int) -> Unit,
) {
    fun recordCompletedMatch(): Boolean {
        val currentCount = readCompletedMatchCount()
            .coerceAtLeast(0)

        val nextCount = if (currentCount == Int.MAX_VALUE) {
            1
        } else {
            currentCount + 1
        }

        writeCompletedMatchCount(nextCount)

        return isInterstitialOpportunity(
            completedMatchCount = nextCount,
        )
    }
}
