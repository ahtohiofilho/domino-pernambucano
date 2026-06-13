package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished

fun findBasicBotMove(
    state: DominoGameState,
): PlayableMove? {
    if (isRoundFinished(state) || isGameFinished(state)) {
        return null
    }

    val currentPlayer = state.players[state.currentPlayerIndex]

    return currentPlayer.hand
        .asSequence()
        .mapNotNull { piece ->
            getPlayableMoves(
                board = state.board,
                piece = piece,
                openingPiece = state.openingPiece,
            ).firstOrNull()
        }
        .firstOrNull()
}