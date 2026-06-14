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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
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

private const val KNOCK_TOWARD_CENTER_SCREEN_FRACTION = 0.08f
private const val KNOCK_PLAYER_RIGHT_SCREEN_FRACTION = 0.20f

@Composable
fun PassTurnKnockAnimationOverlay(
    playerIndex: Int,
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit,
) {
    val offsetAnim = remember(playerIndex) {
        Animatable(0f)
    }

    val rotationAnim = remember(playerIndex) {
        Animatable(0f)
    }

    val alphaAnim = remember(playerIndex) {
        Animatable(0f)
    }

    var frameIndex by remember(playerIndex) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(playerIndex) {
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

        onAnimationFinished()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = getKnockAlignmentForPlayer(playerIndex),
    ) {
        val density = LocalDensity.current

        val screenWidthPx = with(density) {
            maxWidth.toPx()
        }

        val screenHeightPx = with(density) {
            maxHeight.toPx()
        }

        val handRotation = getKnockBaseRotationForPlayer(playerIndex)

        val directionalOffset = getDirectionalKnockOffset(
            playerIndex = playerIndex,
            offset = offsetAnim.value,
        )

        val placementOffset = getKnockPlacementOffset(
            playerIndex = playerIndex,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )

        Image(
            painter = painterResource(
                id = getKnockFrameRes(frameIndex),
            ),
            contentDescription = "Toque",
            modifier = Modifier
                .offset {
                    placementOffset + directionalOffset
                }
                .size(KNOCK_HAND_SIZE)
                .graphicsLayer {
                    alpha = alphaAnim.value
                    rotationZ = handRotation + getDirectionalRotation(
                        playerIndex = playerIndex,
                        rotation = rotationAnim.value,
                    )
                    shadowElevation = 18f
                },
        )
    }
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

private fun getKnockPlacementOffset(
    playerIndex: Int,
    screenWidthPx: Float,
    screenHeightPx: Float,
): IntOffset {
    val towardCenterX = when (playerIndex) {
        1 -> screenWidthPx * KNOCK_TOWARD_CENTER_SCREEN_FRACTION
        3 -> -screenWidthPx * KNOCK_TOWARD_CENTER_SCREEN_FRACTION
        else -> 0f
    }

    val towardCenterY = when (playerIndex) {
        0 -> -screenHeightPx * KNOCK_TOWARD_CENTER_SCREEN_FRACTION
        2 -> screenHeightPx * KNOCK_TOWARD_CENTER_SCREEN_FRACTION
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