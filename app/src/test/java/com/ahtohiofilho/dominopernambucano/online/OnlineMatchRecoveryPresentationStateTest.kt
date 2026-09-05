package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineMatchRecoveryPresentationStateTest {
    @Test
    fun recovered_round_summary_is_not_rewritten_to_round_intro() {
        val state = runtimeState(
            phase = DominoMatchPhase.RoundSummary,
        )

        assertEquals(
            DominoMatchPhase.RoundSummary,
            initialOnlineMatchPresentationState(state).phase,
        )
    }

    @Test
    fun recovered_match_finished_is_not_rewritten_to_round_intro() {
        val state = runtimeState(
            phase = DominoMatchPhase.MatchFinished,
        )

        assertEquals(
            DominoMatchPhase.MatchFinished,
            initialOnlineMatchPresentationState(state).phase,
        )
    }

    @Test
    fun active_game_still_enters_through_round_intro_presentation() {
        val state = runtimeState(
            phase = DominoMatchPhase.WaitingForLocalMove,
        )

        assertEquals(
            DominoMatchPhase.RoundIntro,
            initialOnlineMatchPresentationState(state).phase,
        )
    }

    private fun runtimeState(
        phase: DominoMatchPhase,
    ): DominoMatchRuntimeState {
        return DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = phase,
        )
    }
}