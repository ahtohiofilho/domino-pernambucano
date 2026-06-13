package com.ahtohiofilho.dominopernambucano.domain

fun createDoubleSixDominoSet(): List<DominoPiece> {
    val pieces = mutableListOf<DominoPiece>()

    for (left in 0..6) {
        for (right in left..6) {
            pieces.add(
                DominoPiece(
                    left = left,
                    right = right,
                )
            )
        }
    }

    return pieces
}