package com.ahtohiofilho.dominopernambucano.domain

data class DominoPieceFootprint(
    val width: Float,
    val height: Float,
)

data class DominoTableLayoutMetrics(
    val boardWidth: Float,
    val boardHeight: Float,

    val pieceStandingWidth: Float,
    val pieceStandingHeight: Float,

    val pieceGap: Float,

    val safeMarginLeft: Float,
    val safeMarginTop: Float,
    val safeMarginRight: Float,
    val safeMarginBottom: Float,

    val lateralEscapeDistance: Float,
) {
    val pieceLyingWidth: Float
        get() = pieceStandingHeight

    val pieceLyingHeight: Float
        get() = pieceStandingWidth

    val usableLeft: Float
        get() = -boardWidth / 2f + safeMarginLeft

    val usableTop: Float
        get() = -boardHeight / 2f + safeMarginTop

    val usableRight: Float
        get() = boardWidth / 2f - safeMarginRight

    val usableBottom: Float
        get() = boardHeight / 2f - safeMarginBottom
}

fun getFootprintForPieceRotation(
    rotationDegrees: Float,
    metrics: DominoTableLayoutMetrics,
): DominoPieceFootprint {
    val normalizedRotation = ((rotationDegrees % 360f) + 360f) % 360f
    val isSideways = normalizedRotation == 90f || normalizedRotation == 270f

    return if (isSideways) {
        DominoPieceFootprint(
            width = metrics.pieceLyingWidth,
            height = metrics.pieceLyingHeight,
        )
    } else {
        DominoPieceFootprint(
            width = metrics.pieceStandingWidth,
            height = metrics.pieceStandingHeight,
        )
    }
}

fun getFootprintForPieceInDirection(
    piece: DominoPiece,
    direction: TableDirection,
    metrics: DominoTableLayoutMetrics,
): DominoPieceFootprint {
    val rotationDegrees = getRotationForPieceInSlot(
        piece = piece,
        direction = direction,
    )

    return getFootprintForPieceRotation(
        rotationDegrees = rotationDegrees,
        metrics = metrics,
    )
}