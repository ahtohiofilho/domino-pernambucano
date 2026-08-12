package com.ahtohiofilho.dominopernambucano.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class DominoPlayerClockBadgeTest {
    @Test
    fun clock_format_uses_seconds_with_at_least_two_digits() {
        assertEquals(
            "20",
            formatDominoPlayerClockMillis(20_000L),
        )
        assertEquals(
            "65",
            formatDominoPlayerClockMillis(65_000L),
        )
    }

    @Test
    fun positive_fractional_second_does_not_render_zero_before_timeout() {
        assertEquals(
            "01",
            formatDominoPlayerClockMillis(1L),
        )
        assertEquals(
            "00",
            formatDominoPlayerClockMillis(0L),
        )
    }

    @Test
    fun primary_clock_has_warning_and_critical_states() {
        assertEquals(
            DominoPlayerClockUrgency.NORMAL,
            resolveDominoPlayerClockUrgency(
                kind = DominoPlayerClockKind.PRIMARY,
                remainingMillis = 20_000L,
            ),
        )
        assertEquals(
            DominoPlayerClockUrgency.WARNING,
            resolveDominoPlayerClockUrgency(
                kind = DominoPlayerClockKind.PRIMARY,
                remainingMillis = 10_000L,
            ),
        )
        assertEquals(
            DominoPlayerClockUrgency.CRITICAL,
            resolveDominoPlayerClockUrgency(
                kind = DominoPlayerClockKind.PRIMARY,
                remainingMillis = 5_000L,
            ),
        )
    }

    @Test
    fun reserve_clock_never_becomes_current_turn_urgency() {
        listOf(
            20_000L,
            5_000L,
            1_000L,
            0L,
        ).forEach { remainingMillis ->
            assertEquals(
                DominoPlayerClockUrgency.NORMAL,
                resolveDominoPlayerClockUrgency(
                    kind = DominoPlayerClockKind.RESERVE,
                    remainingMillis = remainingMillis,
                ),
            )
        }
    }
}
