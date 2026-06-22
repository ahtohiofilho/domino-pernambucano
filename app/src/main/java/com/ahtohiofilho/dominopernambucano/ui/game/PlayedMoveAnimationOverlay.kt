package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MOVE_ANIMATION_DURATION_MILLIS = 720
private const val MISSING_TARGET_FALLBACK_MILLIS = 280L

@Composable
fun PlayedMoveAnimationOverlay(
    move: PlayableMove,
    sourcePositionInWindow: Offset?,
    target: DominoMoveTargetInWindow?,
    presentationId: String?,
    onAnimationTrace: (OnlineTraceType, Map<String, String>) -> Unit,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = sourcePositionInWindow
    val moveTarget = target
    val moveAttributes = move.toAnimationTraceAttributes()

    if (source == null || moveTarget == null) {
        LaunchedEffect(
            presentationId,
            move,
            sourcePositionInWindow,
            target,
        ) {
            val startedAtNanos = System.nanoTime()

            onAnimationTrace(
                OnlineTraceType.ANIMATION_FALLBACK_USED,
                moveAttributes + mapOf(
                    "animationKind" to "move",
                    "fallbackMillis" to MISSING_TARGET_FALLBACK_MILLIS.toString(),
                    "sourceAvailable" to (source != null).toString(),
                    "targetAvailable" to (moveTarget != null).toString(),
                ),
            )

            try {
                delay(MISSING_TARGET_FALLBACK_MILLIS)

                onAnimationTrace(
                    OnlineTraceType.ANIMATION_FINISHED,
                    moveAttributes + mapOf(
                        "animationKind" to "move",
                        "completion" to "fallback",
                        "durationMillis" to elapsedMillisSince(startedAtNanos).toString(),
                    ),
                )

                onAnimationFinished()
            } catch (error: CancellationException) {
                onAnimationTrace(
                    OnlineTraceType.ANIMATION_CANCELLED,
                    moveAttributes + mapOf(
                        "animationKind" to "move",
                        "stage" to "fallback_wait",
                        "durationMillis" to elapsedMillisSince(startedAtNanos).toString(),
                    ),
                )

                throw error
            }
        }

        return
    }

    val density = LocalDensity.current

    val pieceWidthPx = with(density) {
        LOCAL_HAND_PIECE_WIDTH.toPx()
    }

    val pieceHeightPx = with(density) {
        LOCAL_HAND_PIECE_HEIGHT.toPx()
    }

    val animatedX = remember(
        presentationId,
        move,
        source,
        moveTarget,
    ) {
        Animatable(source.x)
    }

    val animatedY = remember(
        presentationId,
        move,
        source,
        moveTarget,
    ) {
        Animatable(source.y)
    }

    val animatedRotation = remember(
        presentationId,
        move,
        source,
        moveTarget,
    ) {
        Animatable(0f)
    }

    val visualPiece = remember(move) {
        getVisualPieceForPlayedMove(move)
    }

    LaunchedEffect(
        presentationId,
        move,
        source,
        moveTarget,
    ) {
        val startedAtNanos = System.nanoTime()

        onAnimationTrace(
            OnlineTraceType.ANIMATION_STARTED,
            moveAttributes + mapOf(
                "animationKind" to "move",
                "expectedDurationMillis" to MOVE_ANIMATION_DURATION_MILLIS.toString(),
            ),
        )

        try {
            animatedX.snapTo(source.x)
            animatedY.snapTo(source.y)
            animatedRotation.snapTo(0f)

            val xJob = launch {
                animatedX.animateTo(
                    targetValue = moveTarget.positionInWindow.x,
                    animationSpec = tween(
                        durationMillis = MOVE_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }

            val yJob = launch {
                animatedY.animateTo(
                    targetValue = moveTarget.positionInWindow.y,
                    animationSpec = tween(
                        durationMillis = MOVE_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }

            val rotationJob = launch {
                animatedRotation.animateTo(
                    targetValue = moveTarget.rotationDegrees,
                    animationSpec = tween(
                        durationMillis = MOVE_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }

            xJob.join()
            yJob.join()
            rotationJob.join()

            onAnimationTrace(
                OnlineTraceType.ANIMATION_FINISHED,
                moveAttributes + mapOf(
                    "animationKind" to "move",
                    "completion" to "animated",
                    "durationMillis" to elapsedMillisSince(startedAtNanos).toString(),
                ),
            )

            onAnimationFinished()
        } catch (error: CancellationException) {
            onAnimationTrace(
                OnlineTraceType.ANIMATION_CANCELLED,
                moveAttributes + mapOf(
                    "animationKind" to "move",
                    "stage" to "running",
                    "durationMillis" to elapsedMillisSince(startedAtNanos).toString(),
                ),
            )

            throw error
        }
    }

    DominoPieceView(
        piece = visualPiece,
        faceUp = true,
        width = LOCAL_HAND_PIECE_WIDTH,
        height = LOCAL_HAND_PIECE_HEIGHT,
        isPlayable = false,
        rotationDegrees = animatedRotation.value,
        autoOrientToPieceOrder = false,
        modifier = modifier.offset {
            IntOffset(
                x = (animatedX.value - pieceWidthPx / 2f).roundToInt(),
                y = (animatedY.value - pieceHeightPx / 2f).roundToInt(),
            )
        },
    )
}

private fun PlayableMove.toAnimationTraceAttributes(): Map<String, String> {
    return mapOf(
        "piece" to "${piece.left}-${piece.right}",
        "boardSide" to side.name,
        "flipped" to flipped.toString(),
    )
}

private fun elapsedMillisSince(
    startedAtNanos: Long,
): Long {
    return ((System.nanoTime() - startedAtNanos) / 1_000_000L)
        .coerceAtLeast(0L)
}

private fun getVisualPieceForPlayedMove(
    playableMove: PlayableMove,
): DominoPiece {
    val pieceToPlace = if (playableMove.flipped) {
        playableMove.piece.flipped()
    } else {
        playableMove.piece
    }

    return if (pieceToPlace.left <= pieceToPlace.right) {
        pieceToPlace
    } else {
        pieceToPlace.flipped()
    }
}