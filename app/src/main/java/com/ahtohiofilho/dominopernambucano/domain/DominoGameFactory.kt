package com.ahtohiofilho.dominopernambucano.domain

fun createInitialDominoGameState(): DominoGameState {
    val shuffledPieces = createDoubleSixDominoSet().shuffled()
    val players = createRoundPlayers(shuffledPieces)
    val openingRule = findOpeningRule(players)

    return DominoGameState(
        board = emptyList(),
        players = players,
        sleepingPieces = shuffledPieces.drop(24).take(4),

        currentPlayerIndex = openingRule.playerIndex,
        lastRoundWinnerIndex = null,
        openingPiece = openingRule.piece,

        teamScores = listOf(0, 0),

        lastMove = null,

        roundWinnerPlayerIndex = null,
        roundWinnerTeamIndex = null,
        roundWinKind = null,

        gameWinnerTeamIndex = null,

        consecutivePassTurns = 0,
        scoreMultiplier = 1,
        targetScore = 6,
    )
}

fun createNextRoundDominoGameState(
    previousState: DominoGameState,
): DominoGameState {
    val shuffledPieces = createDoubleSixDominoSet().shuffled()
    val players = createRoundPlayers(shuffledPieces)

    val previousRoundWasClosedTie =
        previousState.roundWinKind == RoundWinKind.CLOSED_TIE

    val openingRule = findOpeningRule(players)

    val starterPlayerIndex = if (previousRoundWasClosedTie) {
        openingRule.playerIndex
    } else {
        previousState.lastRoundWinnerIndex ?: openingRule.playerIndex
    }

    val openingPiece = if (previousRoundWasClosedTie) {
        openingRule.piece
    } else {
        null
    }

    val nextScoreMultiplier = if (previousRoundWasClosedTie) {
        previousState.scoreMultiplier + 1
    } else {
        1
    }

    return DominoGameState(
        board = emptyList(),
        players = players,
        sleepingPieces = shuffledPieces.drop(24).take(4),

        currentPlayerIndex = starterPlayerIndex,
        lastRoundWinnerIndex = previousState.lastRoundWinnerIndex,
        openingPiece = openingPiece,

        teamScores = previousState.teamScores,

        lastMove = null,

        roundWinnerPlayerIndex = null,
        roundWinnerTeamIndex = null,
        roundWinKind = null,

        gameWinnerTeamIndex = null,

        consecutivePassTurns = 0,
        scoreMultiplier = nextScoreMultiplier,
        targetScore = previousState.targetScore,
    )
}

private fun createRoundPlayers(
    shuffledPieces: List<DominoPiece>,
): List<DominoPlayer> {
    return listOf(
        DominoPlayer(
            id = 0,
            name = "Você",
            hand = shuffledPieces.take(6),
        ),
        DominoPlayer(
            id = 1,
            name = "Jogador 2",
            hand = shuffledPieces.drop(6).take(6),
        ),
        DominoPlayer(
            id = 2,
            name = "Jogador 3",
            hand = shuffledPieces.drop(12).take(6),
        ),
        DominoPlayer(
            id = 3,
            name = "Jogador 4",
            hand = shuffledPieces.drop(18).take(6),
        ),
    )
}

private data class OpeningRule(
    val playerIndex: Int,
    val piece: DominoPiece,
)

private fun findOpeningRule(
    players: List<DominoPlayer>,
): OpeningRule {
    val startingDoubles = listOf(
        DominoPiece(6, 6),
        DominoPiece(5, 5),
        DominoPiece(4, 4),
        DominoPiece(3, 3),
        DominoPiece(2, 2),
    )

    for (startingPiece in startingDoubles) {
        val playerIndex = players.indexOfFirst { player ->
            player.hand.contains(startingPiece)
        }

        if (playerIndex != -1) {
            return OpeningRule(
                playerIndex = playerIndex,
                piece = startingPiece,
            )
        }
    }

    return OpeningRule(
        playerIndex = 0,
        piece = players[0].hand.first(),
    )
}