package com.ahtohiofilho.dominopernambucano.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PassTurnKnockAnimationOverlayTest {
    @Test
    fun `resolve screen player index keeps local player at bottom`() {
        assertEquals(
            0,
            resolveKnockScreenPlayerIndex(
                playerIndex = 1,
                localPlayerIndex = 1,
            ),
        )
    }

    @Test
    fun `resolve screen player index rotates opponents from local perspective`() {
        assertEquals(
            1,
            resolveKnockScreenPlayerIndex(
                playerIndex = 2,
                localPlayerIndex = 1,
            ),
        )
        assertEquals(
            2,
            resolveKnockScreenPlayerIndex(
                playerIndex = 3,
                localPlayerIndex = 1,
            ),
        )
        assertEquals(
            3,
            resolveKnockScreenPlayerIndex(
                playerIndex = 0,
                localPlayerIndex = 1,
            ),
        )
    }
}
