package com.ahtohiofilho.dominopernambucano.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class DominoRelativeSeatLayoutTest {
    @Test
    fun seat_zero_keeps_the_current_visual_orientation() {
        val layout = calculateDominoRelativeSeatLayout(
            localPlayerIndex = 0,
            playerCount = 4,
        )

        assertEquals(0, layout.localPlayerIndex)
        assertEquals(2, layout.topPlayerIndex)
        assertEquals(1, layout.leftPlayerIndex)
        assertEquals(3, layout.rightPlayerIndex)
    }

    @Test
    fun seat_one_moves_its_owner_to_the_local_bottom_position() {
        val layout = calculateDominoRelativeSeatLayout(
            localPlayerIndex = 1,
            playerCount = 4,
        )

        assertEquals(1, layout.localPlayerIndex)
        assertEquals(3, layout.topPlayerIndex)
        assertEquals(2, layout.leftPlayerIndex)
        assertEquals(0, layout.rightPlayerIndex)
    }

    @Test
    fun every_local_seat_has_a_unique_relative_positioning() {
        val layouts = (0..3).map { localPlayerIndex ->
            calculateDominoRelativeSeatLayout(
                localPlayerIndex = localPlayerIndex,
                playerCount = 4,
            )
        }

        layouts.forEach { layout ->
            val visibleIndexes = setOf(
                layout.localPlayerIndex,
                layout.topPlayerIndex,
                layout.leftPlayerIndex,
                layout.rightPlayerIndex,
            )

            assertEquals(4, visibleIndexes.size)
        }
    }
}