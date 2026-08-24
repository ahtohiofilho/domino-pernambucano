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
