package com.ahtohiofilho.dominopernambucano.online

data class OnlineRemotePollingPolicy(
    val enabled: Boolean,
    val intervalMillis: Long,
    val maxRateLimitBackoffMillis: Long = 60_000L,
) {
    fun calculateRateLimitBackoffMillis(
        consecutiveRateLimits: Int,
        retryAfterMillis: Long?,
    ): Long {
        require(consecutiveRateLimits > 0)

        val maximum = maxRateLimitBackoffMillis.coerceAtLeast(0L)
        if (maximum == 0L) {
            return 0L
        }

        var fallback = intervalMillis
            .coerceAtLeast(1L)
            .coerceAtMost(maximum)

        repeat(consecutiveRateLimits) {
            fallback = when {
                fallback >= maximum -> maximum
                fallback > maximum / 2L -> maximum
                else -> (fallback * 2L).coerceAtMost(maximum)
            }
        }

        val retryAfter = retryAfterMillis
            ?.coerceAtLeast(0L)
            ?.coerceAtMost(maximum)
            ?: 0L

        return maxOf(
            fallback,
            retryAfter,
        ).coerceAtMost(maximum)
    }

    companion object {
        val Disabled = OnlineRemotePollingPolicy(
            enabled = false,
            intervalMillis = 0L,
        )

        val Default = OnlineRemotePollingPolicy(
            enabled = true,
            intervalMillis = 1_000L,
        )
    }
}
