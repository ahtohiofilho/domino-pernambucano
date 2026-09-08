package com.ahtohiofilho.dominopernambucano.ui.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePlacementAudioLogicTest {
    @Test
    fun `transition tracker only fires when an accepted move presentation finishes`() {
        val tracker = TilePlacementAudioTransitionTracker(
            initiallyPresentingMove = false,
        )

        assertFalse(
            tracker.accept(
                isPresentingMove = false,
            ),
        )

        assertFalse(
            tracker.accept(
                isPresentingMove = true,
            ),
        )

        assertFalse(
            tracker.accept(
                isPresentingMove = true,
            ),
        )

        assertTrue(
            tracker.accept(
                isPresentingMove = false,
            ),
        )

        assertFalse(
            tracker.accept(
                isPresentingMove = false,
            ),
        )
    }

    @Test
    fun `transition tracker does not replay initial historical state`() {
        val tracker = TilePlacementAudioTransitionTracker(
            initiallyPresentingMove = false,
        )

        assertFalse(
            tracker.accept(
                isPresentingMove = false,
            ),
        )
    }

    @Test
    fun `selector avoids immediate repetition`() {
        var counter = 0

        val selector = NonRepeatingSoundIndexSelector(
            soundCount = 5,
            nextInt = { bound ->
                val value = counter % bound
                counter += 1
                value
            },
        )

        var previous = selector.nextIndex()

        repeat(50) {
            val current = selector.nextIndex()

            assertNotEquals(
                previous,
                current,
            )

            previous = current
        }
    }
}
