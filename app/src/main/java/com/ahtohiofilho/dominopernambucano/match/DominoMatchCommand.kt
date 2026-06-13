package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.domain.PlayableMove

sealed interface DominoMatchCommand {
    data object RoundIntroFinished : DominoMatchCommand

    data class LocalMoveSelected(
        val move: PlayableMove,
    ) : DominoMatchCommand

    data object PresentationFinished : DominoMatchCommand

    data object StartNextRound : DominoMatchCommand

    data object StartNewMatch : DominoMatchCommand
}