package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundSummaryPresentationCadenceTest {
    @Test
    fun summary_ui_completion_precedes_server_fallback() {
        assertTrue(
            ROUND_SUMMARY_EXPECTED_COMPLETION_MILLIS <
                DominoMatchTiming.RoundSummaryAutoAdvanceMillis,
        )
    }

    @Test
    fun summary_keeps_at_least_one_second_of_authoritative_headroom() {
        val headroomMillis =
            DominoMatchTiming.RoundSummaryAutoAdvanceMillis -
                ROUND_SUMMARY_EXPECTED_COMPLETION_MILLIS

        assertTrue(headroomMillis >= 1_000L)
    }

    @Test
    fun summary_has_a_meaningful_visible_hold_after_entrance() {
        assertTrue(
            ROUND_SUMMARY_VISIBLE_HOLD_MILLIS >= 1_200L,
        )
        assertTrue(
            ROUND_SUMMARY_VISIBLE_HOLD_MILLIS <= 1_800L,
        )
    }
}
