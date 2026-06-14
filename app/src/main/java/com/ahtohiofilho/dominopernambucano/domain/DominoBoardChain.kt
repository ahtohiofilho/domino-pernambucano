package com.ahtohiofilho.dominopernambucano.domain

data class DominoBoardChain(
    val openingPiece: DominoPiece? = null,
    val leftPieces: List<DominoPiece> = emptyList(),
    val rightPieces: List<DominoPiece> = emptyList(),
) {
    fun isEmpty(): Boolean {
        return openingPiece == null
    }

    fun asVisualPiecesFromTopToBottom(): List<DominoPiece> {
        val opening = openingPiece ?: return emptyList()

        return leftPieces.asReversed() + opening + rightPieces
    }
}

fun addPieceToBoardChain(
    chain: DominoBoardChain,
    piece: DominoPiece,
    side: BoardSide,
): DominoBoardChain {
    if (chain.openingPiece == null) {
        return chain.copy(
            openingPiece = piece,
        )
    }

    return when (side) {
        BoardSide.LEFT -> chain.copy(
            leftPieces = chain.leftPieces + piece,
        )

        BoardSide.RIGHT -> chain.copy(
            rightPieces = chain.rightPieces + piece,
        )
    }
}