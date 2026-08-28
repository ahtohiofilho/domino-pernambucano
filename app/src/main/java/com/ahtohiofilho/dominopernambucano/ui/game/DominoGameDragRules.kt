package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import kotlin.math.abs
import kotlin.math.sqrt

private const val DROP_SIDE_TIE_EPSILON_PX = 0.5f

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
    tableBoundsInWindow: Rect?,
    previousHighlightedSide: BoardSide? = null,
): BoardSide? {
    if (!isPositionInsideRect(positionInWindow, tableBoundsInWindow)) {
        return null
    }

    return findNearestDropSide(
        positionInWindow = positionInWindow,
        playableMoves = playableMoves,
        dropTargets = dropTargets,
        preferredSideOnTie = previousHighlightedSide,
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

fun findNearestDropSide(
    positionInWindow: Offset,
    playableMoves: List<PlayableMove>,
    dropTargets: List<DominoDropTargetInWindow>,
    maxDistancePx: Float? = null,
    preferredSideOnTie: BoardSide? = null,
): BoardSide? {
    val playableSides = playableMoves.map { move -> move.side }.toSet()

    if (playableSides.isEmpty()) {
        return null
    }

    if (playableSides.size == 1) {
        return playableSides.first()
    }

    val nearestDistanceBySide = dropTargets
        .asSequence()
        .filter { target -> target.side in playableSides }
        .groupBy { target -> target.side }
        .mapValues { (_, targets) ->
            targets.minOf { target ->
                getDistance(
                    first = positionInWindow,
                    second = target.positionInWindow,
                )
            }
        }

    if (nearestDistanceBySide.isEmpty()) {
        return null
    }

    val minimumDistance = nearestDistanceBySide.values.minOrNull()
        ?: return null

    if (maxDistancePx != null && minimumDistance > maxDistancePx) {
        return null
    }

    val tiedSides = nearestDistanceBySide
        .filterValues { distance ->
            abs(distance - minimumDistance) <= DROP_SIDE_TIE_EPSILON_PX
        }
        .keys

    if (
        preferredSideOnTie != null &&
        preferredSideOnTie in tiedSides
    ) {
        return preferredSideOnTie
    }

    return when {
        BoardSide.LEFT in tiedSides -> BoardSide.LEFT
        BoardSide.RIGHT in tiedSides -> BoardSide.RIGHT
        else -> null
    }
}

private fun getDistance(
    first: Offset,
    second: Offset,
): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y

    return sqrt(dx * dx + dy * dy)
}