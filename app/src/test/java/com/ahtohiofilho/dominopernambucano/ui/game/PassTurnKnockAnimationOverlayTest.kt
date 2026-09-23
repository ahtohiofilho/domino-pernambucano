package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.unit.IntOffset
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
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
                participantType = DominoParticipantType.HUMAN,
                matchMode = DominoMatchMode.PUBLIC_RANKED,
                selectedTone = HandAppearanceTone.TONE_4,
            ),
        )
    }

    @Test
    fun `remote human keeps legacy hand appearance tone`() {
        assertEquals(
            HandAppearanceTone.TONE_1,
            resolveKnockHandAppearanceTone(
                playerIndex = 1,
                localPlayerIndex = 2,
                participantType = DominoParticipantType.HUMAN,
                matchMode = DominoMatchMode.PUBLIC_RANKED,
                selectedTone = HandAppearanceTone.TONE_4,
            ),
        )
    }

    @Test
    fun `three synthetic opponents use the three tones not selected locally`() {
        val resolvedTones = (1..3).map { playerIndex ->
            resolveKnockHandAppearanceTone(
                playerIndex = playerIndex,
                localPlayerIndex = 0,
                participantType = DominoParticipantType.SYNTHETIC,
                matchMode = DominoMatchMode.PUBLIC_RANKED,
                selectedTone = HandAppearanceTone.TONE_1,
            )
        }

        assertEquals(
            listOf(
                HandAppearanceTone.TONE_2,
                HandAppearanceTone.TONE_3,
                HandAppearanceTone.TONE_4,
            ),
            resolvedTones,
        )
    }

    @Test
    fun `synthetic palette excludes a different locally selected tone`() {
        val resolvedTones = (1..3).map { playerIndex ->
            resolveKnockHandAppearanceTone(
                playerIndex = playerIndex,
                localPlayerIndex = 0,
                participantType = DominoParticipantType.SYNTHETIC,
                matchMode = DominoMatchMode.PUBLIC_RANKED,
                selectedTone = HandAppearanceTone.TONE_3,
            )
        }

        assertEquals(
            listOf(
                HandAppearanceTone.TONE_1,
                HandAppearanceTone.TONE_2,
                HandAppearanceTone.TONE_4,
            ),
            resolvedTones,
        )
    }

    @Test
    fun `offline bot seats use synthetic palette even with legacy human metadata`() {
        assertEquals(
            HandAppearanceTone.TONE_3,
            resolveKnockHandAppearanceTone(
                playerIndex = 2,
                localPlayerIndex = 0,
                participantType = DominoParticipantType.HUMAN,
                matchMode = DominoMatchMode.OFFLINE_LOCAL,
                selectedTone = HandAppearanceTone.TONE_1,
            ),
        )
    }

    @Test
    fun `application participant uses synthetic palette`() {
        assertEquals(
            HandAppearanceTone.TONE_4,
            resolveKnockHandAppearanceTone(
                playerIndex = 3,
                localPlayerIndex = 0,
                participantType = DominoParticipantType.APPLICATION,
                matchMode = DominoMatchMode.PRIVATE_UNRANKED,
                selectedTone = HandAppearanceTone.TONE_1,
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
