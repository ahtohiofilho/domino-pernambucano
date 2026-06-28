package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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

private const val CountdownLateralOffsetFraction = 0.18f
private val CountdownWarningYellow = Color(0xFFFFC107)
private val CountdownCriticalRed = Color(0xFFD32F2F)

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

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val horizontalLateralOffset = maxWidth * CountdownLateralOffsetFraction
        val verticalLateralOffset = maxHeight * CountdownLateralOffsetFraction

        CountdownClocks(
            playerIndex = topPlayerIndex,
            uiState = uiState,
            isCurrent = currentPlayerIndex == topPlayerIndex,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    top = DominoGameVisualTokens.HeaderSlotHeight + 6.dp,
                )
                .offset(
                    x = horizontalLateralOffset,
                ),
        )

        CountdownClocks(
            playerIndex = leftPlayerIndex,
            uiState = uiState,
            isCurrent = currentPlayerIndex == leftPlayerIndex,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = 10.dp,
                )
                .offset(
                    y = -verticalLateralOffset,
                ),
        )

        CountdownClocks(
            playerIndex = rightPlayerIndex,
            uiState = uiState,
            isCurrent = currentPlayerIndex == rightPlayerIndex,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    end = 10.dp,
                )
                .offset(
                    y = verticalLateralOffset,
                ),
        )

        CountdownClocks(
            playerIndex = localPlayerIndex,
            uiState = uiState,
            isCurrent = currentPlayerIndex == localPlayerIndex,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = DominoGameVisualTokens.LocalHandSlotHeight + 8.dp,
                )
                .offset(
                    x = horizontalLateralOffset,
                ),
        )
    }
}

@Composable
private fun CountdownClocks(
    playerIndex: Int,
    uiState: DominoGameUiState,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val remainingMillis = uiState.playerClockMillis.getOrNull(playerIndex)
        ?: return
    val reserveMillis = uiState.playerClockReserveMillis
        .getOrNull(playerIndex)
        ?: 0L

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.14f else 1f,
        label = "countdownClocksScale",
    )

    Row(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountdownClockNumber(
            remainingMillis = remainingMillis,
            isCurrent = isCurrent,
        )

        Text(
            text = "+",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = getCountdownPlusColor(
                isCurrent = isCurrent,
            ),
            maxLines = 1,
        )

        CountdownClockNumber(
            remainingMillis = reserveMillis,
            isCurrent = isCurrent,
        )
    }
}

@Composable
private fun CountdownClockNumber(
    remainingMillis: Long,
    isCurrent: Boolean,
) {
    val remainingSeconds = formatCountdownSeconds(
        millis = remainingMillis,
    )

    Box(
        modifier = Modifier
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
                remainingSeconds = remainingSeconds,
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
    val color = when {
        remainingSeconds <= 5L -> CountdownCriticalRed
        remainingSeconds <= 12L -> CountdownWarningYellow
        else -> DominoColorTokens.PureWhite
    }

    return color.copy(
        alpha = if (isCurrent) 0.96f else 0.54f,
    )
}

private fun getCountdownTextColor(
    remainingSeconds: Long,
    isCurrent: Boolean,
): Color {
    val color = if (remainingSeconds <= 5L) {
        DominoColorTokens.PureWhite
    } else {
        DominoColorTokens.InkBlue
    }

    return color.copy(
        alpha = if (isCurrent) 1f else 0.82f,
    )
}

private fun getCountdownPlusColor(
    isCurrent: Boolean,
): Color {
    return DominoColorTokens.PureWhite.copy(
        alpha = if (isCurrent) 0.96f else 0.70f,
    )
}
