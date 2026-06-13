package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.domain.PlayableMove

sealed interface DominoMatchPhase {
    data object RoundIntro : DominoMatchPhase

    data object WaitingForLocalMove : DominoMatchPhase

    data class PresentingMove(
        val playerIndex: Int,
        val move: PlayableMove,
    ) : DominoMatchPhase

    data class PresentingPass(
        val playerIndex: Int,
    ) : DominoMatchPhase

    data object RoundSummary : DominoMatchPhase

    data object MatchFinished : DominoMatchPhase
}