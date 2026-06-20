package com.ahtohiofilho.dominopernambucano.domain

fun passTurn(
    state: DominoGameState,
): DominoGameState {
    if (isRoundFinished(state) || isGameFinished(state)) {
        return state
    }

    val nextPlayerIndex = getNextCounterClockwisePlayerIndex(
        currentPlayerIndex = state.currentPlayerIndex,
        playerCount = state.players.size,
    )

    val updatedConsecutivePassTurns = state.consecutivePassTurns + 1

    val updatedState = state.copy(
        currentPlayerIndex = nextPlayerIndex,
        consecutivePassTurns = updatedConsecutivePassTurns,
    )

    val allPlayersTouchedInSequence =
        updatedConsecutivePassTurns >= state.players.size

    if (allPlayersTouchedInSequence && isClosedGame(updatedState)) {
        return finishRoundByClosedGame(updatedState)
    }

    return updatedState
}