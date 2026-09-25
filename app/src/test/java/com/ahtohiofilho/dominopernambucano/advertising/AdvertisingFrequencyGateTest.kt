package com.ahtohiofilho.dominopernambucano.advertising

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvertisingFrequencyGateTest {
    @Test
    fun first_completed_match_is_an_interstitial_opportunity() {
        var completedMatches = 0
        val gate = gateFor(
            read = { completedMatches },
            write = { completedMatches = it },
        )

        assertTrue(gate.recordCompletedMatch())
        assertEquals(1, completedMatches)
    }

    @Test
    fun every_completed_match_is_an_interstitial_opportunity() {
        var completedMatches = 0
        val gate = gateFor(
            read = { completedMatches },
            write = { completedMatches = it },
        )

        repeat(12) {
            assertTrue(gate.recordCompletedMatch())
        }

        assertEquals(12, completedMatches)
    }

    @Test
    fun zero_and_negative_counts_are_not_standalone_opportunities() {
        assertFalse(
            isInterstitialOpportunity(
                completedMatchCount = 0,
            ),
        )
        assertFalse(
            isInterstitialOpportunity(
                completedMatchCount = -1,
            ),
        )
    }

    @Test
    fun persisted_count_still_allows_the_next_completed_match() {
        var completedMatches = 37
        val gate = gateFor(
            read = { completedMatches },
            write = { completedMatches = it },
        )

        assertTrue(gate.recordCompletedMatch())
        assertEquals(38, completedMatches)
    }

    @Test
    fun corrupted_negative_persisted_count_recovers_to_first_completion() {
        var completedMatches = -50
        val gate = gateFor(
            read = { completedMatches },
            write = { completedMatches = it },
        )

        assertTrue(gate.recordCompletedMatch())
        assertEquals(1, completedMatches)
    }

    @Test
    fun max_value_rolls_over_to_one_without_suppressing_the_opportunity() {
        var completedMatches = Int.MAX_VALUE
        val gate = gateFor(
            read = { completedMatches },
            write = { completedMatches = it },
        )

        assertTrue(gate.recordCompletedMatch())
        assertEquals(1, completedMatches)
    }

    @Test
    fun interstitial_without_previous_display_is_allowed() {
        assertTrue(
            isInterstitialCooldownSatisfied(
                lastShownEpochMillis = 0L,
                nowEpochMillis = 100_000L,
            ),
        )
    }

    @Test
    fun interstitial_is_suppressed_until_six_minutes_have_elapsed() {
        val lastShown = 1_000_000L

        assertFalse(
            isInterstitialCooldownSatisfied(
                lastShownEpochMillis = lastShown,
                nowEpochMillis =
                    lastShown +
                        InterstitialCooldownMillis -
                        1L,
            ),
        )

        assertTrue(
            isInterstitialCooldownSatisfied(
                lastShownEpochMillis = lastShown,
                nowEpochMillis =
                    lastShown +
                        InterstitialCooldownMillis,
            ),
        )
    }

    @Test
    fun clock_rollback_does_not_bypass_interstitial_cooldown() {
        assertFalse(
            isInterstitialCooldownSatisfied(
                lastShownEpochMillis = 2_000_000L,
                nowEpochMillis = 1_900_000L,
            ),
        )
    }

    @Test
    fun shown_interstitial_persists_timestamp_and_blocks_immediate_repeat() {
        var persisted = 0L
        var now = 5_000_000L

        val gate = AdvertisingInterstitialCooldownGate(
            readLastShownEpochMillis = { persisted },
            writeLastShownEpochMillis = { persisted = it },
            nowEpochMillis = { now },
        )

        assertTrue(gate.canShowInterstitial())

        gate.recordInterstitialShown()

        assertEquals(now, persisted)
        assertFalse(gate.canShowInterstitial())

        now += InterstitialCooldownMillis

        assertTrue(gate.canShowInterstitial())
    }

    private fun gateFor(
        read: () -> Int,
        write: (Int) -> Unit,
    ): AdvertisingFrequencyGate {
        return AdvertisingFrequencyGate(
            readCompletedMatchCount = read,
            writeCompletedMatchCount = write,
        )
    }
}
