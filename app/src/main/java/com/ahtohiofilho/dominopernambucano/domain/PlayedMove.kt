package com.ahtohiofilho.dominopernambucano.domain

data class PlayedMove(
    val playerIndex: Int,
    val piece: DominoPiece,
    val wasLaELo: Boolean,
    val wasCruzada: Boolean,
)