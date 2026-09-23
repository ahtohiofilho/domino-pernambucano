package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import com.ahtohiofilho.dominopernambucano.ui.personalization.HandAppearanceTone
import com.ahtohiofilho.dominopernambucano.ui.personalization.toKnockHandColorFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val KNOCK_HAND_SIZE = 92.dp

private const val KNOCK_APPEAR_MILLIS = 160
private const val KNOCK_PRE_KNOCK_HOLD_MILLIS = 140L
private const val KNOCK_PREPARE_MILLIS = 35
private const val KNOCK_IMPACT_MILLIS = 45
private const val KNOCK_RETURN_MILLIS = 55
private const val KNOCK_BETWEEN_MILLIS = 35L
private const val KNOCK_HOLD_AFTER_MILLIS = 160L
private const val KNOCK_END_MILLIS = 160

private const val KNOCK_OFFSET_PX = 14f
private const val KNOCK_ROTATION_DEGREES = 5f

private const val KNOCK_SIDE_TOWARD_CENTER_SCREEN_FRACTION = 0.08f
private const val KNOCK_BOTTOM_TOWARD_CENTER_SCREEN_FRACTION = 0.10f
private const val KNOCK_TOP_TOWARD_CENTER_SCREEN_FRACTION = 0.16f
private const val KNOCK_PLAYER_RIGHT_SCREEN_FRACTION = 0.20f

@Composable
fun PassTurnKnockAnimationOverlay(
    playerIndex: Int,
    localPlayerIndex: Int,
    handAppearanceTone: HandAppearanceTone =
        HandAppearanceTone.TONE_1,
    participantType: DominoParticipantType,
    matchMode: DominoMatchMode,
    presentationId: String?,
    onAnimationTrace: (OnlineTraceType, Map<String, String>) -> Unit,
    onKnockImpact: () -> Unit = {},
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit,
) {
    val screenPlayerIndex = resolveKnockScreenPlayerIndex(
        playerIndex = playerIndex,
        localPlayerIndex = localPlayerIndex,
    )

    val effectiveHandAppearanceTone =
        resolveKnockHandAppearanceTone(
            playerIndex = playerIndex,
            localPlayerIndex = localPlayerIndex,
            participantType = participantType,
            matchMode = matchMode,
            selectedTone = handAppearanceTone,
        )

    val offsetAnim = remember(presentationId, playerIndex, localPlayerIndex) {
        Animatable(0f)
    }

    val rotationAnim = remember(presentationId, playerIndex, localPlayerIndex) {
        Animatable(0f)
    }

    val alphaAnim = remember(presentationId, playerIndex, localPlayerIndex) {
        Animatable(0f)
    }

    var frameIndex by remember(presentationId, playerIndex, localPlayerIndex) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(
        presentationId,
        playerIndex,
        localPlayerIndex,
    ) {
        val startedAtNanos = System.nanoTime()
        var completionReported = false

        onAnimationTrace(
            OnlineTraceType.ANIMATION_STARTED,
            mapOf(
                "animationKind" to "pass",
                "playerIndex" to playerIndex.toString(),
                "localPlayerIndex" to localPlayerIndex.toString(),
                "screenPlayerIndex" to screenPlayerIndex.toString(),
                "expectedDurationMillis" to expectedKnockDurationMillis().toString(),
            ),
        )

        try {
            offsetAnim.snapTo(0f)
            rotationAnim.snapTo(0f)
            alphaAnim.snapTo(0f)
            frameIndex = 0

            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = KNOCK_APPEAR_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )

            delay(KNOCK_PRE_KNOCK_HOLD_MILLIS)

            suspend fun knockOnce() {
                frameIndex = 0

                offsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = KNOCK_PREPARE_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )

                frameIndex = 1
                onKnockImpact()

                val offsetJob = launch {
                    offsetAnim.animateTo(
                        targetValue = KNOCK_OFFSET_PX,
                        animationSpec = tween(
                            durationMillis = KNOCK_IMPACT_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }

                val rotationJob = launch {
                    rotationAnim.animateTo(
                        targetValue = KNOCK_ROTATION_DEGREES,
                        animationSpec = tween(
                            durationMillis = KNOCK_IMPACT_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }

                offsetJob.join()
                rotationJob.join()

                frameIndex = 0

                val returnOffsetJob = launch {
                    offsetAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = KNOCK_RETURN_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }

                val returnRotationJob = launch {
                    rotationAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = KNOCK_RETURN_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }

                returnOffsetJob.join()
                returnRotationJob.join()
            }

            knockOnce()
            delay(KNOCK_BETWEEN_MILLIS)
            knockOnce()

            delay(KNOCK_HOLD_AFTER_MILLIS)

            alphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = KNOCK_END_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )

            onAnimationTrace(
                OnlineTraceType.ANIMATION_FINISHED,
                mapOf(
                    "animationKind" to "pass",
                    "playerIndex" to playerIndex.toString(),
                    "localPlayerIndex" to localPlayerIndex.toString(),
                    "screenPlayerIndex" to screenPlayerIndex.toString(),
                    "durationMillis" to elapsedKnockMillis(startedAtNanos).toString(),
                ),
            )

            completionReported = true
            onAnimationFinished()
        } catch (error: CancellationException) {
            /*
             * Ao confirmar a apresentação, o coordenador promove a próxima
             * revisão e este overlay sai da composição. Isso é encerramento
             * normal, não cancelamento visual.
             */
            if (!completionReported) {
                onAnimationTrace(
                    OnlineTraceType.ANIMATION_CANCELLED,
                    mapOf(
                        "animationKind" to "pass",
                        "playerIndex" to playerIndex.toString(),
                        "localPlayerIndex" to localPlayerIndex.toString(),
                        "screenPlayerIndex" to screenPlayerIndex.toString(),
                        "durationMillis" to elapsedKnockMillis(startedAtNanos).toString(),
                    ),
                )
            }

            throw error
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = getKnockAlignmentForPlayer(screenPlayerIndex),
    ) {
        val density = LocalDensity.current

        val screenWidthPx = with(density) {
            maxWidth.toPx()
        }

        val screenHeightPx = with(density) {
            maxHeight.toPx()
        }

        val handRotation = getKnockBaseRotationForPlayer(screenPlayerIndex)

        val directionalOffset = getDirectionalKnockOffset(
            playerIndex = screenPlayerIndex,
            offset = offsetAnim.value,
        )

        val placementOffset = getKnockPlacementOffset(
            playerIndex = screenPlayerIndex,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )

        Image(
            painter = painterResource(
                id = getKnockFrameRes(frameIndex),
            ),
            contentDescription = stringResource(R.string.game_pass_content_description),
            colorFilter =
                effectiveHandAppearanceTone
                    .toKnockHandColorFilter(),
            modifier = Modifier
                .offset {
                    placementOffset + directionalOffset
                }
                .size(KNOCK_HAND_SIZE)
                .graphicsLayer {
                    alpha = alphaAnim.value
                    rotationZ = handRotation + getDirectionalRotation(
                        playerIndex = screenPlayerIndex,
                        rotation = rotationAnim.value,
                    )
                    shadowElevation = 18f
                },
        )
    }
}

internal fun resolveKnockScreenPlayerIndex(
    playerIndex: Int,
    localPlayerIndex: Int,
    playerCount: Int = 4,
): Int {
    require(playerCount > 0) {
        "playerCount deve ser positivo."
    }

    val relativeIndex = (playerIndex - localPlayerIndex) % playerCount

    return if (relativeIndex < 0) {
        relativeIndex + playerCount
    } else {
        relativeIndex
    }
}

internal fun resolveKnockHandAppearanceTone(
    playerIndex: Int,
    localPlayerIndex: Int,
    participantType: DominoParticipantType,
    matchMode: DominoMatchMode,
    selectedTone: HandAppearanceTone,
): HandAppearanceTone {
    if (playerIndex == localPlayerIndex) {
        return selectedTone
    }

    val usesSyntheticPalette =
        matchMode == DominoMatchMode.OFFLINE_LOCAL ||
            participantType != DominoParticipantType.HUMAN

    if (!usesSyntheticPalette) {
        return HandAppearanceTone.TONE_1
    }

    val screenPlayerIndex = resolveKnockScreenPlayerIndex(
        playerIndex = playerIndex,
        localPlayerIndex = localPlayerIndex,
    )

    val availableSyntheticTones =
        HandAppearanceTone.entries.filterNot { tone ->
            tone == selectedTone
        }

    return availableSyntheticTones.getOrElse(
        index = screenPlayerIndex - 1,
    ) {
        HandAppearanceTone.TONE_1
    }
}

private fun expectedKnockDurationMillis(): Long {
    val singleKnockMillis =
        KNOCK_PREPARE_MILLIS + KNOCK_IMPACT_MILLIS + KNOCK_RETURN_MILLIS

    return (
        KNOCK_APPEAR_MILLIS +
                KNOCK_PRE_KNOCK_HOLD_MILLIS +
                singleKnockMillis +
                KNOCK_BETWEEN_MILLIS +
                singleKnockMillis +
                KNOCK_HOLD_AFTER_MILLIS +
                KNOCK_END_MILLIS
        ).toLong()
}

private fun elapsedKnockMillis(
    startedAtNanos: Long,
): Long {
    return ((System.nanoTime() - startedAtNanos) / 1_000_000L)
        .coerceAtLeast(0L)
}

private fun getKnockFrameRes(
    frameIndex: Int,
): Int {
    return when (frameIndex) {
        1 -> R.drawable.knock_hand_impact
        else -> R.drawable.knock_hand_base
    }
}

private fun getKnockAlignmentForPlayer(
    playerIndex: Int,
): Alignment {
    return when (playerIndex) {
        0 -> Alignment.BottomCenter
        1 -> Alignment.CenterStart
        2 -> Alignment.TopCenter
        3 -> Alignment.CenterEnd
        else -> Alignment.Center
    }
}

private fun getKnockBaseRotationForPlayer(
    playerIndex: Int,
): Float {
    return when (playerIndex) {
        0 -> 180f
        1 -> 270f
        2 -> 0f
        3 -> 90f
        else -> 180f
    }
}

private fun getDirectionalRotation(
    playerIndex: Int,
    rotation: Float,
): Float {
    return when (playerIndex) {
        1,
        2 -> -rotation

        else -> rotation
    }
}

private fun getDirectionalKnockOffset(
    playerIndex: Int,
    offset: Float,
): IntOffset {
    return when (playerIndex) {
        0 -> IntOffset(
            x = 0,
            y = -offset.roundToInt(),
        )

        1 -> IntOffset(
            x = offset.roundToInt(),
            y = 0,
        )

        2 -> IntOffset(
            x = 0,
            y = offset.roundToInt(),
        )

        3 -> IntOffset(
            x = -offset.roundToInt(),
            y = 0,
        )

        else -> IntOffset.Zero
    }
}

internal fun getKnockPlacementOffset(
    playerIndex: Int,
    screenWidthPx: Float,
    screenHeightPx: Float,
): IntOffset {
    val towardCenterX = when (playerIndex) {
        1 -> screenWidthPx * KNOCK_SIDE_TOWARD_CENTER_SCREEN_FRACTION
        3 -> -screenWidthPx * KNOCK_SIDE_TOWARD_CENTER_SCREEN_FRACTION
        else -> 0f
    }

    val towardCenterY = when (playerIndex) {
        0 -> -screenHeightPx * KNOCK_BOTTOM_TOWARD_CENTER_SCREEN_FRACTION
        2 -> screenHeightPx * KNOCK_TOP_TOWARD_CENTER_SCREEN_FRACTION
        else -> 0f
    }

    val playerRightX = when (playerIndex) {
        0 -> screenWidthPx * KNOCK_PLAYER_RIGHT_SCREEN_FRACTION
        2 -> -screenWidthPx * KNOCK_PLAYER_RIGHT_SCREEN_FRACTION
        else -> 0f
    }

    return IntOffset(
        x = (towardCenterX + playerRightX).roundToInt(),
        y = towardCenterY.roundToInt(),
    )
}
