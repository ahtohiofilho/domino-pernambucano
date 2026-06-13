package com.ahtohiofilho.dominopernambucano.domain

fun playMoveForCurrentPlayer(
    state: DominoGameState,
    playableMove: PlayableMove,
): DominoGameState {
    if (isRoundFinished(state) || isGameFinished(state)) {
        return state
    }

    val currentPlayerIndex = state.currentPlayerIndex
    val currentPlayer = state.players[currentPlayerIndex]

    if (!currentPlayer.hand.contains(playableMove.piece)) {
        return state
    }

    val validMoves = getPlayableMoves(
        board = state.board,
        piece = playableMove.piece,
        openingPiece = state.openingPiece,
    )

    if (!validMoves.contains(playableMove)) {
        return state
    }

    val pieceToPlace = if (playableMove.flipped) {
        playableMove.piece.flipped()
    } else {
        playableMove.piece
    }

    val updatedBoard = when (playableMove.side) {
        BoardSide.LEFT -> listOf(pieceToPlace) + state.board
        BoardSide.RIGHT -> state.board + pieceToPlace
    }

    val updatedPlayer = currentPlayer.copy(
        hand = currentPlayer.hand - playableMove.piece,
    )

    val updatedPlayers = state.players.toMutableList()
    updatedPlayers[currentPlayerIndex] = updatedPlayer

    val wasLaELo = isLaELoMove(
        board = state.board,
        piece = playableMove.piece,
    )

    val wasCruzada = isCruzadaMove(
        board = state.board,
        piece = playableMove.piece,
    )

    val playedMove = PlayedMove(
        playerIndex = currentPlayerIndex,
        piece = playableMove.piece,
        wasLaELo = wasLaELo,
        wasCruzada = wasCruzada,
    )

    val baseState = state.copy(
        board = updatedBoard,
        players = updatedPlayers,
        lastMove = playedMove,
        consecutivePassTurns = 0,
    )

    if (updatedPlayer.hand.isEmpty()) {
        return finishRoundByHit(
            state = baseState,
            winnerPlayerIndex = currentPlayerIndex,
            lastMove = playedMove,
        )
    }

    val nextPlayerIndex = getNextCounterClockwisePlayerIndex(
        currentPlayerIndex = currentPlayerIndex,
        playerCount = state.players.size,
    )

    return baseState.copy(
        currentPlayerIndex = nextPlayerIndex,
    )
}

private fun finishRoundByHit(
    state: DominoGameState,
    winnerPlayerIndex: Int,
    lastMove: PlayedMove,
): DominoGameState {
    val winKind = determineRoundWinKind(lastMove)
    val winnerTeamIndex = getTeamIndexForPlayer(winnerPlayerIndex)

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