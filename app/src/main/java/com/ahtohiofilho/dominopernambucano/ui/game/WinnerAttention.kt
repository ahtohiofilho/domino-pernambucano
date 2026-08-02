package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

private const val WINNER_PULSE_DURATION_MILLIS = 460

data class WinnerAttentionMotion(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val scale: Float,
)

@Composable
fun rememberWinnerAttentionMotion(
    isWinner: Boolean,
): WinnerAttentionMotion {
    val transition = rememberInfiniteTransition(
        label = "winnerAttentionTransition",
    )

    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isWinner) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = WINNER_PULSE_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "winnerAttentionPulse",
    )

    return WinnerAttentionMotion(
        containerAlpha = 0.24f + (pulse * 0.20f),
        borderAlpha = 0.72f + (pulse * 0.28f),
        scale = 1f + (pulse * 0.025f),
    )
}

@Composable
fun WinnerStatusPill(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                color = DominoSemanticColors.scoreHighlight.copy(alpha = 0.92f),
            )
            .padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) 0.dp else 5.dp,
        ),
    ) {
        Text(
            text = "★",
            color = DominoColorTokens.InkBlue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
        )

        if (!compact) {
            Text(
                text = stringResource(R.string.game_winner_label),
                color = DominoColorTokens.InkBlue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
