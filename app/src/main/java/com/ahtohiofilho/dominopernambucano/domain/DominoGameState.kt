package com.ahtohiofilho.dominopernambucano.domain

data class DominoGameState(
    val board: List<DominoPiece>,
    val players: List<DominoPlayer>,
    val sleepingPieces: List<DominoPiece>,

    val currentPlayerIndex: Int,
    val lastRoundWinnerIndex: Int?,
    val openingPiece: DominoPiece?,

    val teamScores: List<Int>,

    val lastMove: PlayedMove?,

    val roundWinnerPlayerIndex: Int?,
    val roundWinnerTeamIndex: Int?,
    val roundWinKind: RoundWinKind?,

    val gameWinnerTeamIndex: Int?,

    val scoreMultiplier: Int = 1,
    val targetScore: Int = 6,
)