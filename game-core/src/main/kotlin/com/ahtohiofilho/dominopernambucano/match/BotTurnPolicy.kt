package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import kotlin.random.Random

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

fun findRandomPlayableMove(
    state: DominoGameState,
    random: Random = Random.Default,
): PlayableMove? {
    if (isRoundFinished(state) || isGameFinished(state)) {
        return null
    }

    val currentPlayer = state.players[state.currentPlayerIndex]

    val moves = currentPlayer.hand.flatMap { piece ->
        getPlayableMoves(
            board = state.board,
            piece = piece,
            openingPiece = state.openingPiece,
        )
    }

    if (moves.isEmpty()) {
        return null
    }

    return moves[random.nextInt(moves.size)]
}