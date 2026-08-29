package com.ahtohiofilho.dominopernambucano.domain

import kotlin.random.Random

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
    val lowestPlayers = findClosedGameLowestPlayerIndexes(state)

    if (lowestPlayers.isEmpty()) {
        return null
    }

    val teamsWithLowestScore = lowestPlayers
        .map { playerIndex -> getTeamIndexForPlayer(playerIndex) }
        .toSet()

    if (teamsWithLowestScore.size != 1) {
        return null
    }

    return lowestPlayers.singleOrNull()
}

fun finishRoundByClosedGame(
    state: DominoGameState,
    tiedPartnerStarterSelector: (List<Int>) -> Int =
        ::selectRandomTiedPartnerStarter,
): DominoGameState {
    val lowestPlayers = findClosedGameLowestPlayerIndexes(state)

    if (lowestPlayers.isEmpty()) {
        return finishRoundByClosedTie(state)
    }

    val teamsWithLowestScore = lowestPlayers
        .map { playerIndex -> getTeamIndexForPlayer(playerIndex) }
        .toSet()

    if (teamsWithLowestScore.size != 1) {
        return finishRoundByClosedTie(state)
    }

    val winnerTeamIndex = teamsWithLowestScore.single()
    val uniqueWinnerPlayerIndex = lowestPlayers.singleOrNull()

    val nextRoundStarterPlayerIndex = uniqueWinnerPlayerIndex
        ?: tiedPartnerStarterSelector(lowestPlayers).also { selectedPlayerIndex ->
            require(selectedPlayerIndex in lowestPlayers) {
                "O saidor sorteado precisa ser um dos parceiros empatados."
            }
        }

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
        lastRoundWinnerIndex = nextRoundStarterPlayerIndex,
        roundWinnerPlayerIndex = uniqueWinnerPlayerIndex,
        roundWinnerTeamIndex = winnerTeamIndex,
        roundWinKind = winKind,
        gameWinnerTeamIndex = gameWinnerTeamIndex,
        currentPlayerIndex = nextRoundStarterPlayerIndex,
    )
}

private fun findClosedGameLowestPlayerIndexes(
    state: DominoGameState,
): List<Int> {
    val playerScores = state.players.indices.map { playerIndex ->
        playerIndex to getHandPipScore(state.players[playerIndex].hand)
    }

    val lowestScore = playerScores.minOfOrNull { (_, score) ->
        score
    } ?: return emptyList()

    return playerScores
        .filter { (_, score) -> score == lowestScore }
        .map { (playerIndex, _) -> playerIndex }
}

private fun selectRandomTiedPartnerStarter(
    tiedPartnerIndexes: List<Int>,
): Int {
    require(tiedPartnerIndexes.size >= 2) {
        "O sorteio do saidor exige pelo menos dois parceiros empatados."
    }

    return tiedPartnerIndexes[
        Random.nextInt(tiedPartnerIndexes.size)
    ]
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
