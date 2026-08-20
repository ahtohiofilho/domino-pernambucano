package com.ahtohiofilho.dominopernambucano.online

data class OnlineRemotePollingPolicy(
    val enabled: Boolean,
    val intervalMillis: Long,
    val maxRateLimitBackoffMillis: Long = 60_000L,
    val maxTransportBackoffMillis: Long = 60_000L,
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

    fun calculateTransportBackoffMillis(
        consecutiveTransportFailures: Int,
    ): Long {
        require(consecutiveTransportFailures > 0)

        val maximum = maxTransportBackoffMillis.coerceAtLeast(0L)
        if (maximum == 0L) {
            return 0L
        }

        var backoff = intervalMillis
            .coerceAtLeast(1L)
            .coerceAtMost(maximum)

        repeat(consecutiveTransportFailures) {
            backoff = when {
                backoff >= maximum -> maximum
                backoff > maximum / 2L -> maximum
                else -> (backoff * 2L).coerceAtMost(maximum)
            }
        }

        return backoff
    }

    fun calculateEffectivePollingDelayMillis(
        rateLimitBackoffMillis: Long?,
        transportBackoffMillis: Long?,
    ): Long {
        return maxOf(
            intervalMillis.coerceAtLeast(0L),
            rateLimitBackoffMillis?.coerceAtLeast(0L) ?: 0L,
            transportBackoffMillis?.coerceAtLeast(0L) ?: 0L,
        )
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
