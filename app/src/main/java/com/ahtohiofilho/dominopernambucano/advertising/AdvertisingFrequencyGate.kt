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
internal const val InterstitialCooldownMillis =
    6L * 60L * 1_000L

internal fun isInterstitialCooldownSatisfied(
    lastShownEpochMillis: Long,
    nowEpochMillis: Long,
    cooldownMillis: Long = InterstitialCooldownMillis,
): Boolean {
    if (cooldownMillis <= 0L) {
        return true
    }

    if (lastShownEpochMillis <= 0L) {
        return true
    }

    if (nowEpochMillis < lastShownEpochMillis) {
        return false
    }

    return nowEpochMillis - lastShownEpochMillis >=
        cooldownMillis
}

internal class AdvertisingInterstitialCooldownGate(
    private val readLastShownEpochMillis: () -> Long,
    private val writeLastShownEpochMillis: (Long) -> Unit,
    private val nowEpochMillis: () -> Long,
) {
    fun canShowInterstitial(): Boolean {
        return isInterstitialCooldownSatisfied(
            lastShownEpochMillis =
                readLastShownEpochMillis(),
            nowEpochMillis = nowEpochMillis(),
        )
    }

    fun recordInterstitialShown() {
        writeLastShownEpochMillis(
            nowEpochMillis().coerceAtLeast(1L),
        )
    }
}
