package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableMagnificationPolicyTest {
    @Test
    fun board_with_no_drag_allows_magnification_in_every_match_phase() {
        val phases = listOf(
            DominoMatchPhase.RoundIntro,
            DominoMatchPhase.WaitingForLocalMove,
            DominoMatchPhase.PresentingPass(playerIndex = 1),
            DominoMatchPhase.RoundSummary,
            DominoMatchPhase.MatchFinished,
        )

        phases.forEach { phase ->
            assertTrue(
                shouldAllowTableMagnification(
                    phase = phase,
                    boardHasPieces = true,
                    dragActive = false,
                ),
            )
        }
    }

    @Test
    fun empty_board_blocks_magnification() {
        assertFalse(
            shouldAllowTableMagnification(
                phase = DominoMatchPhase.WaitingForLocalMove,
                boardHasPieces = false,
                dragActive = false,
            ),
        )
    }

    @Test
    fun active_piece_drag_blocks_magnification() {
        assertFalse(
            shouldAllowTableMagnification(
                phase = DominoMatchPhase.WaitingForLocalMove,
                boardHasPieces = true,
                dragActive = true,
            ),
        )
    }

    @Test
    fun turn_countdown_is_elevated_only_while_magnified_table_is_visible() {
        assertTrue(
            shouldElevateTurnCountdownAboveOverlays(
                tableMagnified = true,
                magnificationEnabled = true,
            ),
        )

        assertFalse(
            shouldElevateTurnCountdownAboveOverlays(
                tableMagnified = false,
                magnificationEnabled = true,
            ),
        )

        assertFalse(
            shouldElevateTurnCountdownAboveOverlays(
                tableMagnified = true,
                magnificationEnabled = false,
            ),
        )
    }
}