package com.ahtohiofilho.dominopernambucano.ui

/**
 * Bounded retry budget for session reconciliation after foreground/network churn.
 * A timeout is NOT proof that a server-owned participation has expired.
 * Only an authoritative remote result can clear the persisted binding.
 */
internal object PendingOnlineRecoveryRetryPolicy {
    const val MaxAutomaticAttempts = 4

    fun delayBeforeRetryMillis(completedAttempts: Int): Long? {
        if (completedAttempts !in 1 until MaxAutomaticAttempts) return null
        return 1_000L shl (completedAttempts - 1)
    }
}
