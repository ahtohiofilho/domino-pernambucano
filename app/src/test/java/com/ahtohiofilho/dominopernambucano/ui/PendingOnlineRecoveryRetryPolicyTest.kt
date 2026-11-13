package com.ahtohiofilho.dominopernambucano.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingOnlineRecoveryRetryPolicyTest {
    @Test
    fun retries_are_bounded_and_exponentially_spaced() {
        assertEquals(1_000L, PendingOnlineRecoveryRetryPolicy.delayBeforeRetryMillis(1))
        assertEquals(2_000L, PendingOnlineRecoveryRetryPolicy.delayBeforeRetryMillis(2))
        assertEquals(4_000L, PendingOnlineRecoveryRetryPolicy.delayBeforeRetryMillis(3))
        assertNull(PendingOnlineRecoveryRetryPolicy.delayBeforeRetryMillis(4))
        assertNull(PendingOnlineRecoveryRetryPolicy.delayBeforeRetryMillis(0))
        assertNull(PendingOnlineRecoveryRetryPolicy.delayBeforeRetryMillis(-1))
        assertNull(PendingOnlineRecoveryRetryPolicy.delayBeforeRetryMillis(100))
    }

    @Test
    fun retry_budget_prevents_unbounded_network_loops() {
        assertEquals(4, PendingOnlineRecoveryRetryPolicy.MaxAutomaticAttempts)
    }
}
