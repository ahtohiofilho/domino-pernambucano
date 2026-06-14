package com.ahtohiofilho.dominopernambucano.domain

data class DominoDropTarget(
    val side: BoardSide,
    val centerX: Float,
    val centerY: Float,
    val rotationDegrees: Float,
)