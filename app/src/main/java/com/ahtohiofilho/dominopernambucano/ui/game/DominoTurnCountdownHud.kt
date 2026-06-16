package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoTurnCountdownHud(
    uiState: DominoGameUiState,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isTurnClockEnabled || uiState.turnClockTotalMillis <= 0L) {
        return
    }

    val localPlayerIndex = uiState.localPlayerIndex
    val leftPlayerIndex = (localPlayerIndex + 1) % 4
    val topPlayerIndex = (localPlayerIndex + 2) % 4
    val rightPlayerIndex = (localPlayerIndex + 3) % 4

    val currentPlayerIndex = uiState.gameState.currentPlayerIndex

    Box(
        modifier = modifier,
    ) {
        CountdownNumber(
            playerIndex = topPlayerIndex,
            uiState = uiState,
            isCurrent = currentPlayerIndex == topPlayerIndex,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    top = DominoGameVisualTokens.HeaderSlotHeight + 6.dp,
                )
                .offset(x = 62.dp),
        )

        CountdownNumber(
            playerIndex = leftPlayerIndex,
            uiState = uiState,
            isCurrent = currentPlayerIndex == leftPlayerIndex,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .offset(y = (-72).dp),
        )

        CountdownNumber(
            playerIndex = rightPlayerIndex,
            uiState = uiState,
            isCurrent = currentPlayerIndex == rightPlayerIndex,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .offset(y = 72.dp),
        )

        CountdownNumber(
            playerIndex = localPlayerIndex,
            uiState = uiState,
            isCurrent = currentPlayerIndex == localPlayerIndex,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = DominoGameVisualTokens.LocalHandSlotHeight + 8.dp,
                )
                .offset(x = 76.dp),
        )
    }
}

@Composable
private fun CountdownNumber(
    playerIndex: Int,
    uiState: DominoGameUiState,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val remainingMillis = uiState.playerClockMillis.getOrNull(playerIndex)
        ?: return

    val remainingSeconds = formatCountdownSeconds(
        millis = remainingMillis,
    )

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.14f else 1f,
        label = "countdownNumberScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                getCountdownBackgroundColor(
                    remainingSeconds = remainingSeconds,
                    isCurrent = isCurrent,
                ),
            )
            .sizeIn(
                minWidth = 34.dp,
                minHeight = 34.dp,
            )
            .padding(
                horizontal = 9.dp,
                vertical = 6.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = remainingSeconds.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = getCountdownTextColor(
                isCurrent = isCurrent,
            ),
            maxLines = 1,
        )
    }
}

private fun formatCountdownSeconds(
    millis: Long,
): Long {
    return ((millis.coerceAtLeast(0L) + 999L) / 1_000L)
        .coerceAtLeast(0L)
}

private fun getCountdownBackgroundColor(
    remainingSeconds: Long,
    isCurrent: Boolean,
): Color {
    return when {
        remainingSeconds <= 5L && isCurrent -> {
            DominoSemanticColors.playableMove.copy(alpha = 0.96f)
        }

        remainingSeconds <= 10L && isCurrent -> {
            DominoSemanticColors.scoreHighlight.copy(alpha = 0.96f)
        }

        isCurrent -> {
            DominoColorTokens.PureWhite.copy(alpha = 0.92f)
        }

        else -> {
            DominoColorTokens.PureWhite.copy(alpha = 0.18f)
        }
    }
}

private fun getCountdownTextColor(
    isCurrent: Boolean,
): Color {
    return if (isCurrent) {
        DominoColorTokens.InkBlue
    } else {
        DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.74f)
    }
}