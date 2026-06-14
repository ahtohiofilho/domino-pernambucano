package com.ahtohiofilho.dominopernambucano.domain

enum class TableDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

data class DominoTableSlot(
    val x: Int,
    val y: Int,
    val direction: TableDirection,
)

data class DominoTablePlacement(
    val piece: DominoPiece,
    val centerX: Float,
    val centerY: Float,
    val rotationDegrees: Float,
)

fun getRotationForPieceInSlot(
    piece: DominoPiece,
    direction: TableDirection,
): Float {
    return if (isDoublePiece(piece)) {
        getDoubleRotation(direction)
    } else {
        getStraightRotation(direction)
    }
}

private fun getStraightRotation(
    direction: TableDirection,
): Float {
    return when (direction) {
        TableDirection.UP,
        TableDirection.DOWN -> 0f

        TableDirection.LEFT,
        TableDirection.RIGHT -> 90f
    }
}

private fun getDoubleRotation(
    direction: TableDirection,
): Float {
    return when (direction) {
        TableDirection.UP,
        TableDirection.DOWN -> 90f

        TableDirection.LEFT,
        TableDirection.RIGHT -> 0f
    }
}