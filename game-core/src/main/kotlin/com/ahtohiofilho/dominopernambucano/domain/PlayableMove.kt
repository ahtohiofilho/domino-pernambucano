package com.ahtohiofilho.dominopernambucano.domain

data class PlayableMove(
    val piece: DominoPiece,
    val side: BoardSide,
    val flipped: Boolean,
)