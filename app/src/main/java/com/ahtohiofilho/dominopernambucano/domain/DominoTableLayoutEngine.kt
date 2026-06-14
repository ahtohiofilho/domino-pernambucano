package com.ahtohiofilho.dominopernambucano.domain

enum class DominoTableRoutePhase {
    CENTER_TO_EDGE,
    LATERAL_ESCAPE,
    CROSS_TABLE,
}

private data class DominoTableCursor(
    val side: BoardSide,
    val previousPiece: DominoPiece,
    val previousCenterX: Float,
    val previousCenterY: Float,
    val previousFootprint: DominoPieceFootprint,
    val direction: TableDirection,
    val phase: DominoTableRoutePhase,
    val phaseProgress: Float,
)

private data class CandidatePlacement(
    val placement: DominoTablePlacement,
    val footprint: DominoPieceFootprint,
    val advance: Float,
)

private data class PlacementResult(
    val placement: DominoTablePlacement,
    val footprint: DominoPieceFootprint,
    val nextCursor: DominoTableCursor,
)

private data class DominoTurnOffset(
    val x: Float,
    val y: Float,
)

fun calculateTablePlacements(
    boardChain: DominoBoardChain,
    metrics: DominoTableLayoutMetrics,
): List<DominoTablePlacement> {
    val openingPiece = boardChain.openingPiece ?: return emptyList()

    val openingRotation = getOpeningPieceRotationForTable(openingPiece)
    val openingFootprint = getFootprintForPieceRotation(
        rotationDegrees = openingRotation,
        metrics = metrics,
    )

    val normalizedOpeningPiece = normalizePieceForTable(openingPiece)

    val openingPlacement = DominoTablePlacement(
        piece = normalizedOpeningPiece,
        centerX = 0f,
        centerY = 0f,
        rotationDegrees = openingRotation,
    )

    val leftPlacements = calculateBranchPlacements(
        side = BoardSide.LEFT,
        pieces = boardChain.leftPieces,
        openingPiece = normalizedOpeningPiece,
        openingFootprint = openingFootprint,
        metrics = metrics,
    )

    val rightPlacements = calculateBranchPlacements(
        side = BoardSide.RIGHT,
        pieces = boardChain.rightPieces,
        openingPiece = normalizedOpeningPiece,
        openingFootprint = openingFootprint,
        metrics = metrics,
    )

    return leftPlacements + openingPlacement + rightPlacements
}

fun calculateDropTargets(
    boardChain: DominoBoardChain,
    playableMoves: List<PlayableMove>,
    metrics: DominoTableLayoutMetrics,
): List<DominoDropTarget> {
    val uniqueMovesBySide = playableMoves.distinctBy { move ->
        move.side
    }

    return uniqueMovesBySide.mapNotNull { move ->
        calculateDropTarget(
            boardChain = boardChain,
            playableMove = move,
            metrics = metrics,
        )
    }
}

private fun calculateDropTarget(
    boardChain: DominoBoardChain,
    playableMove: PlayableMove,
    metrics: DominoTableLayoutMetrics,
): DominoDropTarget? {
    val pieceToPlace = if (playableMove.flipped) {
        playableMove.piece.flipped()
    } else {
        playableMove.piece
    }

    val openingPiece = boardChain.openingPiece

    if (openingPiece == null) {
        return DominoDropTarget(
            side = playableMove.side,
            centerX = 0f,
            centerY = 0f,
            rotationDegrees = getOpeningPieceRotationForTable(pieceToPlace),
        )
    }

    val openingRotation = getOpeningPieceRotationForTable(openingPiece)
    val openingFootprint = getFootprintForPieceRotation(
        rotationDegrees = openingRotation,
        metrics = metrics,
    )

    val normalizedOpeningPiece = normalizePieceForTable(openingPiece)

    val branchPieces = when (playableMove.side) {
        BoardSide.LEFT -> boardChain.leftPieces + pieceToPlace
        BoardSide.RIGHT -> boardChain.rightPieces + pieceToPlace
    }

    val branchPlacements = calculateBranchPlacements(
        side = playableMove.side,
        pieces = branchPieces,
        openingPiece = normalizedOpeningPiece,
        openingFootprint = openingFootprint,
        metrics = metrics,
    )

    val placement = branchPlacements.lastOrNull() ?: return null

    return DominoDropTarget(
        side = playableMove.side,
        centerX = placement.centerX,
        centerY = placement.centerY,
        rotationDegrees = placement.rotationDegrees,
    )
}

private fun calculateBranchPlacements(
    side: BoardSide,
    pieces: List<DominoPiece>,
    openingPiece: DominoPiece,
    openingFootprint: DominoPieceFootprint,
    metrics: DominoTableLayoutMetrics,
): List<DominoTablePlacement> {
    if (pieces.isEmpty()) {
        return emptyList()
    }

    var cursor = createInitialCursor(
        side = side,
        openingPiece = openingPiece,
        openingFootprint = openingFootprint,
    )

    val placements = mutableListOf<DominoTablePlacement>()

    pieces.forEach { piece ->
        val connectedValue = getConnectedValueForBranchPiece(
            side = side,
            piece = piece,
        )

        val result = calculateNextPlacement(
            cursor = cursor,
            piece = piece,
            connectedValue = connectedValue,
            metrics = metrics,
        )

        placements.add(result.placement)
        cursor = result.nextCursor
    }

    return placements
}

private fun calculateNextPlacement(
    cursor: DominoTableCursor,
    piece: DominoPiece,
    connectedValue: Int,
    metrics: DominoTableLayoutMetrics,
): PlacementResult {
    val initialCandidate = createCandidatePlacement(
        cursor = cursor,
        piece = piece,
        connectedValue = connectedValue,
        metrics = metrics,
        turnedBeforePlacement = false,
        previousDirection = cursor.direction,
    )

    val effectiveCursor = if (
        shouldTurnBeforePlacement(
            cursor = cursor,
            candidate = initialCandidate,
            metrics = metrics,
        )
    ) {
        createTurnCursor(cursor)
    } else {
        cursor
    }

    val turnedBeforePlacement = effectiveCursor.direction != cursor.direction

    val candidate = createCandidatePlacement(
        cursor = effectiveCursor,
        piece = piece,
        connectedValue = connectedValue,
        metrics = metrics,
        turnedBeforePlacement = turnedBeforePlacement,
        previousDirection = cursor.direction,
    )

    val nextCursor = effectiveCursor.copy(
        previousPiece = normalizePieceForTable(piece),
        previousCenterX = candidate.placement.centerX,
        previousCenterY = candidate.placement.centerY,
        previousFootprint = candidate.footprint,
        phaseProgress = effectiveCursor.phaseProgress + candidate.advance,
    )

    val normalizedNextCursor = normalizeCursorAfterPlacement(
        cursor = nextCursor,
        metrics = metrics,
    )

    return PlacementResult(
        placement = candidate.placement,
        footprint = candidate.footprint,
        nextCursor = normalizedNextCursor,
    )
}

private fun createInitialCursor(
    side: BoardSide,
    openingPiece: DominoPiece,
    openingFootprint: DominoPieceFootprint,
): DominoTableCursor {
    return when (side) {
        BoardSide.LEFT -> DominoTableCursor(
            side = side,
            previousPiece = openingPiece,
            previousCenterX = 0f,
            previousCenterY = 0f,
            previousFootprint = openingFootprint,
            direction = TableDirection.UP,
            phase = DominoTableRoutePhase.CENTER_TO_EDGE,
            phaseProgress = 0f,
        )

        BoardSide.RIGHT -> DominoTableCursor(
            side = side,
            previousPiece = openingPiece,
            previousCenterX = 0f,
            previousCenterY = 0f,
            previousFootprint = openingFootprint,
            direction = TableDirection.DOWN,
            phase = DominoTableRoutePhase.CENTER_TO_EDGE,
            phaseProgress = 0f,
        )
    }
}

private fun createCandidatePlacement(
    cursor: DominoTableCursor,
    piece: DominoPiece,
    connectedValue: Int,
    metrics: DominoTableLayoutMetrics,
    turnedBeforePlacement: Boolean,
    previousDirection: TableDirection,
): CandidatePlacement {
    val rotationDegrees = getTableRotationForConnectedValue(
        piece = piece,
        connectedValue = connectedValue,
        direction = cursor.direction,
    )

    val footprint = getFootprintForPieceRotation(
        rotationDegrees = rotationDegrees,
        metrics = metrics,
    )

    val advance = getAdjacentAdvance(
        previousFootprint = cursor.previousFootprint,
        currentFootprint = footprint,
        direction = cursor.direction,
        gap = metrics.pieceGap,
    )

    val turnOffset = getTurnVisualOffset(
        turnedBeforePlacement = turnedBeforePlacement,
        previousPiece = cursor.previousPiece,
        previousFootprint = cursor.previousFootprint,
        previousDirection = previousDirection,
    )

    val centerX = moveX(
        x = cursor.previousCenterX,
        direction = cursor.direction,
        distance = advance,
    ) + turnOffset.x

    val centerY = moveY(
        y = cursor.previousCenterY,
        direction = cursor.direction,
        distance = advance,
    ) + turnOffset.y

    return CandidatePlacement(
        placement = DominoTablePlacement(
            piece = normalizePieceForTable(piece),
            centerX = centerX,
            centerY = centerY,
            rotationDegrees = rotationDegrees,
        ),
        footprint = footprint,
        advance = advance,
    )
}

private fun getTurnVisualOffset(
    turnedBeforePlacement: Boolean,
    previousPiece: DominoPiece,
    previousFootprint: DominoPieceFootprint,
    previousDirection: TableDirection,
): DominoTurnOffset {
    if (!turnedBeforePlacement) {
        return DominoTurnOffset(
            x = 0f,
            y = 0f,
        )
    }

    if (isDoublePiece(previousPiece)) {
        return DominoTurnOffset(
            x = 0f,
            y = 0f,
        )
    }

    return when (previousDirection) {
        TableDirection.UP -> DominoTurnOffset(
            x = 0f,
            y = -previousFootprint.height / 4f,
        )

        TableDirection.DOWN -> DominoTurnOffset(
            x = 0f,
            y = previousFootprint.height / 4f,
        )

        TableDirection.LEFT -> DominoTurnOffset(
            x = -previousFootprint.width / 4f,
            y = 0f,
        )

        TableDirection.RIGHT -> DominoTurnOffset(
            x = previousFootprint.width / 4f,
            y = 0f,
        )
    }
}

private fun getConnectedValueForBranchPiece(
    side: BoardSide,
    piece: DominoPiece,
): Int {
    return when (side) {
        BoardSide.LEFT -> piece.right
        BoardSide.RIGHT -> piece.left
    }
}

private fun getTableRotationForConnectedValue(
    piece: DominoPiece,
    connectedValue: Int,
    direction: TableDirection,
): Float {
    if (isDoublePiece(piece)) {
        return getRotationForPieceInSlot(
            piece = piece,
            direction = direction,
        )
    }

    val normalizedPiece = normalizePieceForTable(piece)
    val topValue = normalizedPiece.left
    val bottomValue = normalizedPiece.right

    return when (direction) {
        TableDirection.UP -> {
            if (bottomValue == connectedValue) {
                0f
            } else {
                180f
            }
        }

        TableDirection.DOWN -> {
            if (topValue == connectedValue) {
                0f
            } else {
                180f
            }
        }

        TableDirection.LEFT -> {
            if (topValue == connectedValue) {
                90f
            } else {
                270f
            }
        }

        TableDirection.RIGHT -> {
            if (bottomValue == connectedValue) {
                90f
            } else {
                270f
            }
        }
    }
}

private fun shouldTurnBeforePlacement(
    cursor: DominoTableCursor,
    candidate: CandidatePlacement,
    metrics: DominoTableLayoutMetrics,
): Boolean {
    if (cursor.phase == DominoTableRoutePhase.CROSS_TABLE) {
        return false
    }

    if (!doesPlacementFitBounds(candidate, metrics)) {
        return true
    }

    return when (cursor.phase) {
        DominoTableRoutePhase.CENTER_TO_EDGE -> {
            !wouldLeaveRoomForMinimumNextPiece(
                candidate = candidate,
                direction = cursor.direction,
                metrics = metrics,
            )
        }

        DominoTableRoutePhase.LATERAL_ESCAPE -> {
            cursor.phaseProgress + candidate.advance > metrics.lateralEscapeDistance
        }

        DominoTableRoutePhase.CROSS_TABLE -> false
    }
}

private fun normalizeCursorAfterPlacement(
    cursor: DominoTableCursor,
    metrics: DominoTableLayoutMetrics,
): DominoTableCursor {
    if (cursor.phase == DominoTableRoutePhase.CROSS_TABLE) {
        return cursor
    }

    if (
        cursor.phase == DominoTableRoutePhase.LATERAL_ESCAPE &&
        cursor.phaseProgress >= metrics.lateralEscapeDistance
    ) {
        return createTurnCursor(cursor)
    }

    return cursor
}

private fun createTurnCursor(
    cursor: DominoTableCursor,
): DominoTableCursor {
    return when (cursor.side) {
        BoardSide.LEFT -> createLeftTurnCursor(cursor)
        BoardSide.RIGHT -> createRightTurnCursor(cursor)
    }
}

private fun createLeftTurnCursor(
    cursor: DominoTableCursor,
): DominoTableCursor {
    return when (cursor.phase) {
        DominoTableRoutePhase.CENTER_TO_EDGE -> cursor.copy(
            direction = TableDirection.LEFT,
            phase = DominoTableRoutePhase.LATERAL_ESCAPE,
            phaseProgress = 0f,
        )

        DominoTableRoutePhase.LATERAL_ESCAPE -> cursor.copy(
            direction = TableDirection.DOWN,
            phase = DominoTableRoutePhase.CROSS_TABLE,
            phaseProgress = 0f,
        )

        DominoTableRoutePhase.CROSS_TABLE -> cursor
    }
}

private fun createRightTurnCursor(
    cursor: DominoTableCursor,
): DominoTableCursor {
    return when (cursor.phase) {
        DominoTableRoutePhase.CENTER_TO_EDGE -> cursor.copy(
            direction = TableDirection.RIGHT,
            phase = DominoTableRoutePhase.LATERAL_ESCAPE,
            phaseProgress = 0f,
        )

        DominoTableRoutePhase.LATERAL_ESCAPE -> cursor.copy(
            direction = TableDirection.UP,
            phase = DominoTableRoutePhase.CROSS_TABLE,
            phaseProgress = 0f,
        )

        DominoTableRoutePhase.CROSS_TABLE -> cursor
    }
}

private fun doesPlacementFitBounds(
    candidate: CandidatePlacement,
    metrics: DominoTableLayoutMetrics,
): Boolean {
    val left = candidate.placement.centerX - candidate.footprint.width / 2f
    val top = candidate.placement.centerY - candidate.footprint.height / 2f
    val right = candidate.placement.centerX + candidate.footprint.width / 2f
    val bottom = candidate.placement.centerY + candidate.footprint.height / 2f

    return left >= metrics.usableLeft &&
            top >= metrics.usableTop &&
            right <= metrics.usableRight &&
            bottom <= metrics.usableBottom
}

private fun wouldLeaveRoomForMinimumNextPiece(
    candidate: CandidatePlacement,
    direction: TableDirection,
    metrics: DominoTableLayoutMetrics,
): Boolean {
    val minimumNextFootprint = getMinimumNextFootprint(
        direction = direction,
        metrics = metrics,
    )

    val advance = getAdjacentAdvance(
        previousFootprint = candidate.footprint,
        currentFootprint = minimumNextFootprint,
        direction = direction,
        gap = metrics.pieceGap,
    )

    val projectedCenterX = moveX(
        x = candidate.placement.centerX,
        direction = direction,
        distance = advance,
    )

    val projectedCenterY = moveY(
        y = candidate.placement.centerY,
        direction = direction,
        distance = advance,
    )

    val projectedCandidate = CandidatePlacement(
        placement = DominoTablePlacement(
            piece = candidate.placement.piece,
            centerX = projectedCenterX,
            centerY = projectedCenterY,
            rotationDegrees = 0f,
        ),
        footprint = minimumNextFootprint,
        advance = advance,
    )

    return doesPlacementFitBounds(
        candidate = projectedCandidate,
        metrics = metrics,
    )
}

private fun getMinimumNextFootprint(
    direction: TableDirection,
    metrics: DominoTableLayoutMetrics,
): DominoPieceFootprint {
    return when (direction) {
        TableDirection.UP,
        TableDirection.DOWN -> DominoPieceFootprint(
            width = metrics.pieceStandingWidth,
            height = metrics.pieceStandingHeight,
        )

        TableDirection.LEFT,
        TableDirection.RIGHT -> DominoPieceFootprint(
            width = metrics.pieceLyingWidth,
            height = metrics.pieceLyingHeight,
        )
    }
}

private fun getAdjacentAdvance(
    previousFootprint: DominoPieceFootprint,
    currentFootprint: DominoPieceFootprint,
    direction: TableDirection,
    gap: Float,
): Float {
    return when (direction) {
        TableDirection.UP,
        TableDirection.DOWN -> {
            previousFootprint.height / 2f + gap + currentFootprint.height / 2f
        }

        TableDirection.LEFT,
        TableDirection.RIGHT -> {
            previousFootprint.width / 2f + gap + currentFootprint.width / 2f
        }
    }
}

private fun moveX(
    x: Float,
    direction: TableDirection,
    distance: Float,
): Float {
    return when (direction) {
        TableDirection.LEFT -> x - distance
        TableDirection.RIGHT -> x + distance
        TableDirection.UP,
        TableDirection.DOWN -> x
    }
}

private fun moveY(
    y: Float,
    direction: TableDirection,
    distance: Float,
): Float {
    return when (direction) {
        TableDirection.UP -> y - distance
        TableDirection.DOWN -> y + distance
        TableDirection.LEFT,
        TableDirection.RIGHT -> y
    }
}

private fun getOpeningPieceRotationForTable(
    piece: DominoPiece,
): Float {
    if (isDoublePiece(piece)) {
        return 90f
    }

    val normalizedPiece = normalizePieceForTable(piece)

    return if (normalizedPiece == piece) {
        0f
    } else {
        180f
    }
}

private fun normalizePieceForTable(
    piece: DominoPiece,
): DominoPiece {
    return if (piece.left <= piece.right) {
        piece
    } else {
        piece.flipped()
    }
}