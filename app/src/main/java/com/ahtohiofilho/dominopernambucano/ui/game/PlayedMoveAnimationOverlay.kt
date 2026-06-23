package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
private const val TARGET_ACQUISITION_POLL_MILLIS = 16L

@Composable
fun PlayedMoveAnimationOverlay(
    move: PlayableMove,
    sourcePositionInWindow: Offset?,
    target: DominoMoveTargetInWindow?,
    presentationKey: DominoMovePresentationKey,
    presentationId: String?,
    onAnimationTrace: (OnlineTraceType, Map<String, String>) -> Unit,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * A geometria da mesa é medida depois da composição. Reiniciar o efeito a
     * cada atualização de bounds cancelava a apresentação vigente antes do
     * primeiro frame útil, principalmente quando chegavam revisões em lote.
     * A identidade da animação é a apresentação lógica, inclusive no modo
     * offline. source/target atualizados são lidos sem reiniciar a coroutine.
     * Um target só é aceito quando carrega a mesma chave de apresentação.
     */
    val latestSource by rememberUpdatedState(sourcePositionInWindow)
    val latestTarget by rememberUpdatedState(target)
    val latestAnimationTrace by rememberUpdatedState(onAnimationTrace)
    val latestAnimationFinished by rememberUpdatedState(onAnimationFinished)

    val animatedX = remember(presentationKey) {
        Animatable(0f)
    }

    val animatedY = remember(presentationKey) {
        Animatable(0f)
    }

    val animatedRotation = remember(presentationKey) {
        Animatable(0f)
    }

    var isPieceVisible by remember(presentationKey) {
        mutableStateOf(false)
    }

    val density = LocalDensity.current
    val pieceWidthPx = with(density) {
        LOCAL_HAND_PIECE_WIDTH.toPx()
    }
    val pieceHeightPx = with(density) {
        LOCAL_HAND_PIECE_HEIGHT.toPx()
    }
    val visualPiece = remember(presentationKey) {
        getVisualPieceForPlayedMove(move)
    }
    val moveAttributes = move.toAnimationTraceAttributes()

    LaunchedEffect(presentationKey) {
        val startedAtNanos = System.nanoTime()
        var completionReported = false

        try {
            var source = latestSource
            var moveTarget = latestTarget.forPresentation(
                presentationKey = presentationKey,
            )

            while (
                (source == null || moveTarget == null) &&
                elapsedMillisSince(startedAtNanos) < MISSING_TARGET_FALLBACK_MILLIS
            ) {
                delay(TARGET_ACQUISITION_POLL_MILLIS)
                source = latestSource
                moveTarget = latestTarget.forPresentation(
                    presentationKey = presentationKey,
                )
            }

            if (source == null || moveTarget == null) {
                latestAnimationTrace(
                    OnlineTraceType.ANIMATION_FALLBACK_USED,
                    moveAttributes + mapOf(
                        "animationKind" to "move",
                        "fallbackMillis" to MISSING_TARGET_FALLBACK_MILLIS.toString(),
                        "sourceAvailable" to (source != null).toString(),
                        "targetAvailable" to (moveTarget != null).toString(),
                        "targetMatchesPresentation" to
                                (latestTarget?.presentationKey ==
                                        presentationKey).toString(),
                    ),
                )

                latestAnimationTrace(
                    OnlineTraceType.ANIMATION_FINISHED,
                    moveAttributes + mapOf(
                        "animationKind" to "move",
                        "completion" to "fallback",
                        "durationMillis" to elapsedMillisSince(startedAtNanos).toString(),
                    ),
                )

                completionReported = true
                latestAnimationFinished()
                return@LaunchedEffect
            }

            latestAnimationTrace(
                OnlineTraceType.ANIMATION_STARTED,
                moveAttributes + mapOf(
                    "animationKind" to "move",
                    "expectedDurationMillis" to MOVE_ANIMATION_DURATION_MILLIS.toString(),
                ),
            )

            animatedX.snapTo(source.x)
            animatedY.snapTo(source.y)
            animatedRotation.snapTo(0f)
            isPieceVisible = true

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

            latestAnimationTrace(
                OnlineTraceType.ANIMATION_FINISHED,
                moveAttributes + mapOf(
                    "animationKind" to "move",
                    "completion" to "animated",
                    "durationMillis" to elapsedMillisSince(startedAtNanos).toString(),
                ),
            )

            completionReported = true
            latestAnimationFinished()
        } catch (error: CancellationException) {
            /*
             * A própria confirmação de término remove o overlay da composição.
             * Esse cancelamento posterior ao callback é esperado e não deve ser
             * contado como apresentação abortada.
             */
            if (!completionReported) {
                latestAnimationTrace(
                    OnlineTraceType.ANIMATION_CANCELLED,
                    moveAttributes + mapOf(
                        "animationKind" to "move",
                        "stage" to "running",
                        "durationMillis" to elapsedMillisSince(startedAtNanos).toString(),
                    ),
                )
            }

            throw error
        }
    }

    if (!isPieceVisible) {
        return
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