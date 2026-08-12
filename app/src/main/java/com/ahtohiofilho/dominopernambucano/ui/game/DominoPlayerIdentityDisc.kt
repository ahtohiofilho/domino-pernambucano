package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoPlayerIdentityDisc(
    name: String,
    participantType: DominoParticipantType,
    isCurrent: Boolean,
    isWinner: Boolean,
    identitySize: Dp,
    modifier: Modifier = Modifier,
) {
    val identitySource = if (
        participantType == DominoParticipantType.APPLICATION
    ) {
        stringResource(R.string.participant_app_label)
    } else {
        name
    }

    val identityGlyph = identitySource
        .trim()
        .take(1)
        .uppercase()
        .ifEmpty { "•" }

    val identityBackground = if (
        participantType == DominoParticipantType.APPLICATION
    ) {
        DominoColorTokens.AccentGreen.copy(alpha = 0.82f)
    } else {
        DominoColorTokens.PernambucoBlue.copy(alpha = 0.84f)
    }

    val identityBorder = if (isCurrent || isWinner) {
        DominoSemanticColors.scoreHighlight
    } else {
        DominoColorTokens.PureWhite.copy(alpha = 0.30f)
    }

    Box(
        modifier = modifier
            .requiredSize(identitySize)
            .clip(CircleShape)
            .background(identityBackground)
            .border(
                width = if (isCurrent || isWinner) {
                    2.dp
                } else {
                    1.dp
                },
                color = identityBorder,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = identityGlyph,
            color = DominoColorTokens.PureWhite,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
