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
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = sourcePositionInWindow
    val moveTarget = target

    if (source == null || moveTarget == null) {
        LaunchedEffect(
            move,
            sourcePositionInWindow,
            target,
        ) {
            delay(MISSING_TARGET_FALLBACK_MILLIS)
            onAnimationFinished()
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
        move,
        source,
        moveTarget,
    ) {
        Animatable(source.x)
    }

    val animatedY = remember(
        move,
        source,
        moveTarget,
    ) {
        Animatable(source.y)
    }

    val animatedRotation = remember(
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
        move,
        source,
        moveTarget,
    ) {
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

        onAnimationFinished()
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