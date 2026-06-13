package com.ahtohiofilho.dominopernambucano.domain

fun isRoundFinished(
    state: DominoGameState,
): Boolean {
    return state.roundWinKind != null
}

fun isGameFinished(
    state: DominoGameState,
): Boolean {
    return state.gameWinnerTeamIndex != null
}