package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoPlayerClockBadge(
    remainingMillis: Long?,
    totalMillis: Long,
    isEnabled: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (!isEnabled || remainingMillis == null || totalMillis <= 0L) {
        return
    }

    val remainingSeconds = (remainingMillis / 1_000L).coerceAtLeast(0L)

    val progress = (
            remainingMillis.toFloat() / totalMillis.toFloat()
            ).coerceIn(
            minimumValue = 0f,
            maximumValue = 1f,
        )

    val clockColor = getClockColor(
        remainingSeconds = remainingSeconds,
        isCurrent = isCurrent,
    )

    Column(
        modifier = modifier
            .widthIn(
                min = if (compact) 46.dp else 58.dp,
            )
            .clip(
                RoundedCornerShape(
                    if (compact) 10.dp else 12.dp,
                ),
            )
            .background(
                DominoColorTokens.PureWhite.copy(
                    alpha = if (isCurrent) 0.22f else 0.13f,
                ),
            )
            .padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (compact) 2.dp else 3.dp,
        ),
    ) {
        Text(
            text = formatClockMillis(remainingMillis),
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            fontWeight = FontWeight.Black,
            color = clockColor,
            maxLines = 1,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 3.dp else 4.dp)
                .clip(CircleShape)
                .background(
                    DominoColorTokens.PureWhite.copy(alpha = 0.26f),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(if (compact) 3.dp else 4.dp)
                    .clip(CircleShape)
                    .background(clockColor),
            )
        }
    }
}

private fun getClockColor(
    remainingSeconds: Long,
    isCurrent: Boolean,
): Color {
    return when {
        remainingSeconds <= 5L -> DominoSemanticColors.playableMove
        remainingSeconds <= 10L -> DominoSemanticColors.scoreHighlight
        isCurrent -> DominoSemanticColors.scoreHighlight
        else -> DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.92f)
    }
}

private fun formatClockMillis(
    millis: Long,
): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return "$minutes:${seconds.toString().padStart(2, '0')}"
}