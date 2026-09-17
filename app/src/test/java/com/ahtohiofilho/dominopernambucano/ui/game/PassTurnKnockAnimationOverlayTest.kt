package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.unit.IntOffset
import com.ahtohiofilho.dominopernambucano.ui.personalization.HandAppearanceTone
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

    @Test
    fun `local player uses selected hand appearance tone`() {
        assertEquals(
            HandAppearanceTone.TONE_4,
            resolveKnockHandAppearanceTone(
                playerIndex = 2,
                localPlayerIndex = 2,
                selectedTone = HandAppearanceTone.TONE_4,
            ),
        )
    }

    @Test
    fun `other players keep legacy hand appearance tone`() {
        assertEquals(
            HandAppearanceTone.TONE_1,
            resolveKnockHandAppearanceTone(
                playerIndex = 1,
                localPlayerIndex = 2,
                selectedTone = HandAppearanceTone.TONE_4,
            ),
        )
    }

    @Test
    fun `knock placement uses refined vertical offsets and preserves sides`() {
        assertEquals(
            IntOffset(
                x = 200,
                y = -100,
            ),
            getKnockPlacementOffset(
                playerIndex = 0,
                screenWidthPx = 1000f,
                screenHeightPx = 1000f,
            ),
        )
        assertEquals(
            IntOffset(
                x = 80,
                y = 0,
            ),
            getKnockPlacementOffset(
                playerIndex = 1,
                screenWidthPx = 1000f,
                screenHeightPx = 1000f,
            ),
        )
        assertEquals(
            IntOffset(
                x = -200,
                y = 160,
            ),
            getKnockPlacementOffset(
                playerIndex = 2,
                screenWidthPx = 1000f,
                screenHeightPx = 1000f,
            ),
        )
        assertEquals(
            IntOffset(
                x = -80,
                y = 0,
            ),
            getKnockPlacementOffset(
                playerIndex = 3,
                screenWidthPx = 1000f,
                screenHeightPx = 1000f,
            ),
        )
    }
}
