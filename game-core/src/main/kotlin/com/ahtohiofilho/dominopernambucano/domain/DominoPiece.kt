package com.ahtohiofilho.dominopernambucano.domain

data class DominoPiece(
    val left: Int,
    val right: Int,
) {
    fun flipped(): DominoPiece {
        return DominoPiece(
            left = right,
            right = left,
        )
    }
}

fun isDoublePiece(
    piece: DominoPiece,
): Boolean {
    return piece.left == piece.right
}