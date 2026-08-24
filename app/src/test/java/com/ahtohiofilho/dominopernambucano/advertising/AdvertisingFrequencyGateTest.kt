package com.ahtohiofilho.dominopernambucano.advertising

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertisingFrequencyGateTest {
    @Test
    fun first_two_matches_have_no_interstitial_opportunity() {
        var completedMatches = 0
        val gate = gateFor(
            read = { completedMatches },
            write = { completedMatches = it },
        )

        assertFalse(gate.recordCompletedMatch())
        assertFalse(gate.recordCompletedMatch())
    }

    @Test
    fun third_and_sixth_matches_are_opportunities() {
        var completedMatches = 0
        val gate = gateFor(
            read = { completedMatches },
            write = { completedMatches = it },
        )

        assertFalse(gate.recordCompletedMatch())
        assertFalse(gate.recordCompletedMatch())
        assertTrue(gate.recordCompletedMatch())
        assertFalse(gate.recordCompletedMatch())
        assertFalse(gate.recordCompletedMatch())
        assertTrue(gate.recordCompletedMatch())
    }

    @Test
    fun missed_opportunity_does_not_accumulate() {
        assertTrue(
            isInterstitialOpportunity(
                completedMatchCount = 3,
            ),
        )
        assertFalse(
            isInterstitialOpportunity(
                completedMatchCount = 4,
            ),
        )
        assertFalse(
            isInterstitialOpportunity(
                completedMatchCount = 5,
            ),
        )
        assertTrue(
            isInterstitialOpportunity(
                completedMatchCount = 6,
            ),
        )
    }

    @Test
    fun persisted_count_continues_frequency_window() {
        var completedMatches = 5
        val gate = gateFor(
            read = { completedMatches },
            write = { completedMatches = it },
        )

        assertTrue(gate.recordCompletedMatch())
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
