package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import kotlin.math.sqrt

fun isPositionInsideRect(
    positionInWindow: Offset,
    rect: Rect?,
): Boolean {
    return rect?.contains(positionInWindow) == true
}

fun findHighlightedDropSideOrNull(
    positionInWindow: Offset,
    playableMoves: List<PlayableMove>,
    dropTargets: List<DominoDropTargetInWindow>,
    localHandBoundsInWindow: Rect?,
    maxDistancePx: Float,
): BoardSide? {
    if (isPositionInsideRect(positionInWindow, localHandBoundsInWindow)) {
        return null
    }

    return findNearestDropSide(
        positionInWindow = positionInWindow,
        playableMoves = playableMoves,
        dropTargets = dropTargets,
        maxDistancePx = maxDistancePx,
    )
}

fun findPlayableMoveForDropSide(
    playableMoves: List<PlayableMove>,
    dropSide: BoardSide?,
): PlayableMove? {
    if (dropSide == null) {
        return null
    }

    return playableMoves.firstOrNull { move ->
        move.side == dropSide
    }
}

private fun findNearestDropSide(
    positionInWindow: Offset,
    playableMoves: List<PlayableMove>,
    dropTargets: List<DominoDropTargetInWindow>,
    maxDistancePx: Float,
): BoardSide? {
    val playableSides = playableMoves
        .map { move -> move.side }
        .toSet()

    val nearestTarget = dropTargets
        .asSequence()
        .filter { target -> target.side in playableSides }
        .map { target ->
            target to getDistance(
                first = positionInWindow,
                second = target.positionInWindow,
            )
        }
        .minByOrNull { (_, distance) -> distance }
        ?: return null

    val distance = nearestTarget.second

    if (distance > maxDistancePx) {
        return null
    }

    return nearestTarget.first.side
}

private fun getDistance(
    first: Offset,
    second: Offset,
): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y

    return sqrt(dx * dx + dy * dy)
}