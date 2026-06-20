package com.ahtohiofilho.dominopernambucano.domain

fun getBoardLeftValue(
    board: List<DominoPiece>,
): Int? {
    return board.firstOrNull()?.left
}

fun getBoardRightValue(
    board: List<DominoPiece>,
): Int? {
    return board.lastOrNull()?.right
}

fun getPlayableMoves(
    board: List<DominoPiece>,
    piece: DominoPiece,
    openingPiece: DominoPiece? = null,
): List<PlayableMove> {
    if (board.isEmpty()) {
        if (openingPiece != null && piece != openingPiece) {
            return emptyList()
        }

        return listOf(
            PlayableMove(
                piece = piece,
                side = BoardSide.RIGHT,
                flipped = false,
            )
        )
    }

    val leftValue = getBoardLeftValue(board)
    val rightValue = getBoardRightValue(board)

    val moves = mutableListOf<PlayableMove>()

    if (leftValue != null) {
        if (piece.right == leftValue) {
            moves.add(
                PlayableMove(
                    piece = piece,
                    side = BoardSide.LEFT,
                    flipped = false,
                )
            )
        }

        if (piece.left == leftValue) {
            moves.add(
                PlayableMove(
                    piece = piece,
                    side = BoardSide.LEFT,
                    flipped = true,
                )
            )
        }
    }

    if (rightValue != null) {
        if (piece.left == rightValue) {
            moves.add(
                PlayableMove(
                    piece = piece,
                    side = BoardSide.RIGHT,
                    flipped = false,
                )
            )
        }

        if (piece.right == rightValue) {
            moves.add(
                PlayableMove(
                    piece = piece,
                    side = BoardSide.RIGHT,
                    flipped = true,
                )
            )
        }
    }

    return moves
}

fun canPlayPiece(
    board: List<DominoPiece>,
    piece: DominoPiece,
    openingPiece: DominoPiece? = null,
): Boolean {
    return getPlayableMoves(
        board = board,
        piece = piece,
        openingPiece = openingPiece,
    ).isNotEmpty()
}

fun hasPlayablePiece(
    state: DominoGameState,
    playerIndex: Int,
): Boolean {
    if (isRoundFinished(state) || isGameFinished(state)) {
        return false
    }

    val player = state.players[playerIndex]

    return player.hand.any { piece ->
        getPlayableMoves(
            board = state.board,
            piece = piece,
            openingPiece = state.openingPiece,
        ).isNotEmpty()
    }
}