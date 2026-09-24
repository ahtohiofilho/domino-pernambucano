package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePresentationLifecyclePolicyTest {
    @Test
    fun every_player_visible_narrative_phase_blocks_snapshot_promotion() {
        val move = PlayableMove(
            piece = DominoPiece(6, 6),
            side = BoardSide.RIGHT,
            flipped = false,
        )

        val phases = listOf(
            DominoMatchPhase.RoundIntro,
            DominoMatchPhase.PresentingMove(
                playerIndex = 0,
                move = move,
            ),
            DominoMatchPhase.PresentingPass(
                playerIndex = 1,
            ),
            DominoMatchPhase.RoundSummary,
        )

        phases.forEach { phase ->
            val contract = requireNotNull(
                resolveOnlinePresentationLifecycleContract(
                    phase = phase,
                    gameplayWatchdogMillis = 2_500L,
                ),
            )

            assertTrue(
                contract.blocksSnapshotPromotionUntilUiCompletion,
            )
            assertTrue(
                "Toda apresentação bloqueante precisa de escape por watchdog.",
                contract.watchdogMillis > 0L,
            )
        }
    }

    @Test
    fun non_narrative_phases_do_not_create_presentation_contracts() {
        assertNull(
            resolveOnlinePresentationLifecycleContract(
                phase = DominoMatchPhase.WaitingForLocalMove,
                gameplayWatchdogMillis = 2_500L,
            ),
        )
        assertNull(
            resolveOnlinePresentationLifecycleContract(
                phase = DominoMatchPhase.MatchFinished,
                gameplayWatchdogMillis = 2_500L,
            ),
        )
    }

    @Test
    fun round_intro_watchdog_outlives_server_fallback_window() {
        val contract = requireNotNull(
            resolveOnlinePresentationLifecycleContract(
                phase = DominoMatchPhase.RoundIntro,
                gameplayWatchdogMillis = 2_500L,
            ),
        )

        assertEquals(
            OnlinePresentationLifecycleKind.ROUND_INTRO,
            contract.kind,
        )
        assertTrue(
            contract.watchdogMillis >
                DominoMatchTiming.RoundIntroServerFallbackMillis,
        )
    }

    @Test
    fun round_summary_watchdog_outlives_normal_ui_hold() {
        val contract = requireNotNull(
            resolveOnlinePresentationLifecycleContract(
                phase = DominoMatchPhase.RoundSummary,
                gameplayWatchdogMillis = 2_500L,
            ),
        )

        assertEquals(
            OnlinePresentationLifecycleKind.ROUND_SUMMARY,
            contract.kind,
        )
        assertTrue(
            contract.watchdogMillis >
                DominoMatchTiming.RoundSummaryAutoAdvanceMillis,
        )
    }
}