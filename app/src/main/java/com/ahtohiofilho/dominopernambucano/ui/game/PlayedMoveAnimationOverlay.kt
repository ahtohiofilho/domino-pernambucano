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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MOVE_ANIMATION_DURATION_MILLIS = 460
private const val MISSING_TARGET_FALLBACK_MILLIS = 280L
private const val AFTER_ANIMATION_SETTLE_MILLIS = 40L

@Composable
fun PlayedMoveAnimationOverlay(
    move: PlayableMove,
    sourcePositionInWindow: Offset?,
    target: DominoMoveTargetInWindow?,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val progress = remember(
        move,
        sourcePositionInWindow,
        target,
    ) {
        Animatable(0f)
    }

    LaunchedEffect(
        move,
        sourcePositionInWindow,
        target,
    ) {
        val source = sourcePositionInWindow
        val targetPosition = target?.positionInWindow

        if (source == null || targetPosition == null) {
            delay(MISSING_TARGET_FALLBACK_MILLIS)
            onAnimationFinished()
            return@LaunchedEffect
        }

        progress.snapTo(0f)

        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MOVE_ANIMATION_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )

        delay(AFTER_ANIMATION_SETTLE_MILLIS)

        onAnimationFinished()
    }

    val source = sourcePositionInWindow ?: return
    val moveTarget = target ?: return
    val targetPosition = moveTarget.positionInWindow

    val pieceWidthPx = with(density) {
        LOCAL_HAND_PIECE_WIDTH.toPx()
    }

    val pieceHeightPx = with(density) {
        LOCAL_HAND_PIECE_HEIGHT.toPx()
    }

    val animatedCenter = lerpOffset(
        start = source,
        end = targetPosition,
        fraction = progress.value,
    )

    val visualPiece = if (move.flipped) {
        move.piece.flipped()
    } else {
        move.piece
    }

    DominoPieceView(
        piece = visualPiece,
        faceUp = true,
        width = LOCAL_HAND_PIECE_WIDTH,
        height = LOCAL_HAND_PIECE_HEIGHT,
        isPlayable = true,
        rotationDegrees = moveTarget.rotationDegrees * progress.value,
        modifier = modifier
            .offset {
                IntOffset(
                    x = (animatedCenter.x - pieceWidthPx / 2f).roundToInt(),
                    y = (animatedCenter.y - pieceHeightPx / 2f).roundToInt(),
                )
            }
            .graphicsLayer {
                alpha = 0.96f
                shadowElevation = 22f
                scaleX = 1.08f
                scaleY = 1.08f
            },
    )
}

private fun lerpOffset(
    start: Offset,
    end: Offset,
    fraction: Float,
): Offset {
    return Offset(
        x = lerpFloat(
            start = start.x,
            end = end.x,
            fraction = fraction,
        ),
        y = lerpFloat(
            start = start.y,
            end = end.y,
            fraction = fraction,
        ),
    )
}

private fun lerpFloat(
    start: Float,
    end: Float,
    fraction: Float,
): Float {
    return start + ((end - start) * fraction)
}