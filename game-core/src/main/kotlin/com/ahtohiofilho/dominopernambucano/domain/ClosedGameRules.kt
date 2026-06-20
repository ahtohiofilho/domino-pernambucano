package com.ahtohiofilho.dominopernambucano.domain

fun getHandPipScore(
    hand: List<DominoPiece>,
): Int {
    return hand.sumOf { piece ->
        piece.left + piece.right
    }
}

fun isClosedGame(
    state: DominoGameState,
): Boolean {
    if (state.board.isEmpty()) {
        return false
    }

    if (isRoundFinished(state) || isGameFinished(state)) {
        return false
    }

    return state.players.none { player ->
        player.hand.any { piece ->
            getPlayableMoves(
                board = state.board,
                piece = piece,
                openingPiece = state.openingPiece,
            ).isNotEmpty()
        }
    }
}

fun findClosedGameWinnerPlayerIndexOrNull(
    state: DominoGameState,
): Int? {
    val playerScores = state.players.indices.map { playerIndex ->
        playerIndex to getHandPipScore(state.players[playerIndex].hand)
    }

    val lowestScore = playerScores.minOfOrNull { (_, score) ->
        score
    } ?: return null

    val playersWithLowestScore = playerScores
        .filter { (_, score) -> score == lowestScore }
        .map { (playerIndex, _) -> playerIndex }

    val teamsWithLowestScore = playersWithLowestScore
        .map { playerIndex -> getTeamIndexForPlayer(playerIndex) }
        .toSet()

    if (teamsWithLowestScore.size > 1) {
        return null
    }

    return playersWithLowestScore.firstOrNull()
}

fun finishRoundByClosedGame(
    state: DominoGameState,
): DominoGameState {
    val winnerPlayerIndex = findClosedGameWinnerPlayerIndexOrNull(state)

    if (winnerPlayerIndex == null) {
        return finishRoundByClosedTie(state)
    }

    val winnerTeamIndex = getTeamIndexForPlayer(winnerPlayerIndex)
    val winKind = RoundWinKind.CLOSED

    val roundPoints = getRoundPoints(
        winKind = winKind,
        scoreMultiplier = state.scoreMultiplier,
    )

    val updatedTeamScores = state.teamScores.toMutableList()
    updatedTeamScores[winnerTeamIndex] =
        updatedTeamScores[winnerTeamIndex] + roundPoints

    val gameWinnerTeamIndex = if (
        updatedTeamScores[winnerTeamIndex] >= state.targetScore
    ) {
        winnerTeamIndex
    } else {
        null
    }

    return state.copy(
        teamScores = updatedTeamScores,
        lastRoundWinnerIndex = winnerPlayerIndex,
        roundWinnerPlayerIndex = winnerPlayerIndex,
        roundWinnerTeamIndex = winnerTeamIndex,
        roundWinKind = winKind,
        gameWinnerTeamIndex = gameWinnerTeamIndex,
        currentPlayerIndex = winnerPlayerIndex,
    )
}

private fun finishRoundByClosedTie(
    state: DominoGameState,
): DominoGameState {
    return state.copy(
        roundWinnerPlayerIndex = null,
        roundWinnerTeamIndex = null,
        roundWinKind = RoundWinKind.CLOSED_TIE,
    )
}