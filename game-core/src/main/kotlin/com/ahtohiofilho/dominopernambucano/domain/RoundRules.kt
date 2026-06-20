package com.ahtohiofilho.dominopernambucano.domain

fun isLaELoMove(
    board: List<DominoPiece>,
    piece: DominoPiece,
): Boolean {
    if (board.isEmpty()) {
        return false
    }

    val leftValue = getBoardLeftValue(board) ?: return false
    val rightValue = getBoardRightValue(board) ?: return false

    if (leftValue == rightValue) {
        return false
    }

    val playableSides = getPlayableMoves(
        board = board,
        piece = piece,
    )
        .map { move -> move.side }
        .toSet()

    return playableSides.contains(BoardSide.LEFT) &&
            playableSides.contains(BoardSide.RIGHT)
}

fun isCruzadaMove(
    board: List<DominoPiece>,
    piece: DominoPiece,
): Boolean {
    if (board.isEmpty()) {
        return false
    }

    if (!isDoublePiece(piece)) {
        return false
    }

    val leftValue = getBoardLeftValue(board) ?: return false
    val rightValue = getBoardRightValue(board) ?: return false

    if (leftValue != rightValue) {
        return false
    }

    val playableSides = getPlayableMoves(
        board = board,
        piece = piece,
    )
        .map { move -> move.side }
        .toSet()

    return playableSides.contains(BoardSide.LEFT) &&
            playableSides.contains(BoardSide.RIGHT)
}

fun determineRoundWinKind(
    lastMove: PlayedMove,
): RoundWinKind {
    val isDouble = isDoublePiece(lastMove.piece)

    return when {
        lastMove.wasCruzada -> RoundWinKind.CRUZADA
        lastMove.wasLaELo -> RoundWinKind.LA_E_LO
        isDouble -> RoundWinKind.DOUBLE
        else -> RoundWinKind.COMMON
    }
}

fun getRoundPoints(
    winKind: RoundWinKind,
    scoreMultiplier: Int,
): Int {
    return winKind.basePoints * scoreMultiplier
}