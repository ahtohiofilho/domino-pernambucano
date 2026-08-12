package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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

internal enum class DominoPlayerClockKind {
    PRIMARY,
    RESERVE,
}

internal enum class DominoPlayerClockUrgency {
    NORMAL,
    WARNING,
    CRITICAL,
}

internal fun resolveDominoPlayerClockUrgency(
    kind: DominoPlayerClockKind,
    remainingMillis: Long,
): DominoPlayerClockUrgency {
    if (kind == DominoPlayerClockKind.RESERVE) {
        return DominoPlayerClockUrgency.NORMAL
    }

    val remainingSeconds = (
        remainingMillis
            .coerceAtLeast(0L) +
            999L
        ) / 1_000L

    return when {
        remainingSeconds <= 5L ->
            DominoPlayerClockUrgency.CRITICAL

        remainingSeconds <= 10L ->
            DominoPlayerClockUrgency.WARNING

        else ->
            DominoPlayerClockUrgency.NORMAL
    }
}

internal fun formatDominoPlayerClockMillis(
    millis: Long,
): String {
    val totalSeconds = (
        millis
            .coerceAtLeast(0L) +
            999L
        ) / 1_000L

    return totalSeconds
        .toString()
        .padStart(2, '0')
}

@Composable
internal fun DominoPlayerClockBadge(
    remainingMillis: Long?,
    kind: DominoPlayerClockKind,
    isEnabled: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (!isEnabled || remainingMillis == null) {
        return
    }

    val urgency = resolveDominoPlayerClockUrgency(
        kind = kind,
        remainingMillis = remainingMillis,
    )

    val isPrimary =
        kind == DominoPlayerClockKind.PRIMARY

    val shape = RoundedCornerShape(
        if (compact) 9.dp else 11.dp,
    )

    val backgroundColor = resolveClockBackgroundColor(
        kind = kind,
        urgency = urgency,
        isCurrent = isCurrent,
    )

    val contentColor = resolveClockContentColor(
        kind = kind,
        urgency = urgency,
        isCurrent = isCurrent,
    )

    Row(
        modifier = modifier
            .widthIn(
                min = when {
                    isPrimary && compact -> 50.dp
                    isPrimary -> 58.dp
                    compact -> 43.dp
                    else -> 48.dp
                },
            )
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = if (isPrimary) 1.dp else 0.75.dp,
                color = resolveClockBorderColor(
                    kind = kind,
                    urgency = urgency,
                    isCurrent = isCurrent,
                ),
                shape = shape,
            )
            .padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 4.dp else 5.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) 4.dp else 5.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isPrimary) "▶" else "⌛",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = contentColor.copy(
                alpha = if (isPrimary) 0.78f else 0.68f,
            ),
            maxLines = 1,
        )

        Text(
            text = formatDominoPlayerClockMillis(
                millis = remainingMillis,
            ),
            style = if (isPrimary && !compact) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.Black,
            color = contentColor,
            maxLines = 1,
        )
    }
}

private fun resolveClockBackgroundColor(
    kind: DominoPlayerClockKind,
    urgency: DominoPlayerClockUrgency,
    isCurrent: Boolean,
): Color {
    if (kind == DominoPlayerClockKind.RESERVE) {
        return DominoColorTokens.PernambucoBlueDark.copy(
            alpha = if (isCurrent) 0.92f else 0.76f,
        )
    }

    val color = when (urgency) {
        DominoPlayerClockUrgency.CRITICAL ->
            Color(0xFFD32F2F)

        DominoPlayerClockUrgency.WARNING ->
            Color(0xFFFFC107)

        DominoPlayerClockUrgency.NORMAL ->
            DominoColorTokens.PureWhite
    }

    return color.copy(
        alpha = if (isCurrent) 0.98f else 0.74f,
    )
}

private fun resolveClockContentColor(
    kind: DominoPlayerClockKind,
    urgency: DominoPlayerClockUrgency,
    isCurrent: Boolean,
): Color {
    if (kind == DominoPlayerClockKind.RESERVE) {
        return DominoSemanticColors.primaryTextOnDark.copy(
            alpha = if (isCurrent) 0.94f else 0.78f,
        )
    }

    val color = if (
        urgency == DominoPlayerClockUrgency.CRITICAL
    ) {
        DominoColorTokens.PureWhite
    } else {
        DominoColorTokens.InkBlue
    }

    return color.copy(
        alpha = if (isCurrent) 1f else 0.88f,
    )
}

private fun resolveClockBorderColor(
    kind: DominoPlayerClockKind,
    urgency: DominoPlayerClockUrgency,
    isCurrent: Boolean,
): Color {
    if (kind == DominoPlayerClockKind.RESERVE) {
        return DominoSemanticColors.scoreHighlight.copy(
            alpha = if (isCurrent) 0.62f else 0.36f,
        )
    }

    return when (urgency) {
        DominoPlayerClockUrgency.CRITICAL ->
            DominoColorTokens.PureWhite.copy(
                alpha = if (isCurrent) 0.82f else 0.52f,
            )

        DominoPlayerClockUrgency.WARNING ->
            DominoColorTokens.InkBlue.copy(
                alpha = if (isCurrent) 0.54f else 0.34f,
            )

        DominoPlayerClockUrgency.NORMAL ->
            DominoSemanticColors.scoreHighlight.copy(
                alpha = if (isCurrent) 0.72f else 0.40f,
            )
    }
}
