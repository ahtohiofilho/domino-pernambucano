package com.ahtohiofilho.dominopernambucano.ui.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostMatchRecoveryPolicyTest {
    @Test
    fun recovery_is_valid_inside_exactly_one_minute() {
        assertTrue(isPostMatchRecoveryActive(1_000L, 1_000L))
        assertTrue(isPostMatchRecoveryActive(1_000L, 60_999L))
        assertFalse(isPostMatchRecoveryActive(1_000L, 61_000L))
    }

    @Test
    fun reopening_does_not_restart_expiry_and_clock_rollback_is_rejected() {
        val completedAt = 50_000L
        assertTrue(isPostMatchRecoveryActive(completedAt, 70_000L))
        assertTrue(isPostMatchRecoveryActive(completedAt, 100_000L))
        assertFalse(isPostMatchRecoveryActive(completedAt, 110_000L))
        assertFalse(isPostMatchRecoveryActive(completedAt, 49_999L))
    }

    @Test
    fun absent_or_invalid_receipts_cannot_restore_results() {
        assertFalse(isPostMatchRecoveryActive(0L, 5_000L))
        assertFalse(isPostMatchRecoveryActive(-1L, 5_000L))
        assertFalse(isPostMatchRecoveryActive(1_000L, 1_100L, 0L))
    }
}
