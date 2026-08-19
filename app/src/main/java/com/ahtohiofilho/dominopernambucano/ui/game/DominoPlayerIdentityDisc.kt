package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val isApplication =
        participantType == DominoParticipantType.APPLICATION

    val identityDescription = if (isApplication) {
        stringResource(R.string.participant_app_label)
    } else {
        name.trim()
    }

    val identityBackground = if (isApplication) {
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
            )
            .semantics {
                contentDescription = identityDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(identitySize * 0.18f),
        ) {
            val iconColor = DominoColorTokens.PureWhite

            if (isApplication) {
                val lineWidth = size.minDimension * 0.08f
                val antennaX = size.width * 0.50f

                drawLine(
                    color = iconColor,
                    start = Offset(
                        x = antennaX,
                        y = size.height * 0.04f,
                    ),
                    end = Offset(
                        x = antennaX,
                        y = size.height * 0.21f,
                    ),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Round,
                )

                drawCircle(
                    color = iconColor,
                    radius = size.minDimension * 0.055f,
                    center = Offset(
                        x = antennaX,
                        y = size.height * 0.04f,
                    ),
                )

                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(
                        x = size.width * 0.14f,
                        y = size.height * 0.21f,
                    ),
                    size = Size(
                        width = size.width * 0.72f,
                        height = size.height * 0.58f,
                    ),
                    cornerRadius = CornerRadius(
                        x = size.minDimension * 0.13f,
                        y = size.minDimension * 0.13f,
                    ),
                )

                drawCircle(
                    color = identityBackground,
                    radius = size.minDimension * 0.07f,
                    center = Offset(
                        x = size.width * 0.36f,
                        y = size.height * 0.47f,
                    ),
                )

                drawCircle(
                    color = identityBackground,
                    radius = size.minDimension * 0.07f,
                    center = Offset(
                        x = size.width * 0.64f,
                        y = size.height * 0.47f,
                    ),
                )

                drawLine(
                    color = identityBackground,
                    start = Offset(
                        x = size.width * 0.36f,
                        y = size.height * 0.65f,
                    ),
                    end = Offset(
                        x = size.width * 0.64f,
                        y = size.height * 0.65f,
                    ),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Round,
                )
            } else {
                drawCircle(
                    color = iconColor,
                    radius = size.minDimension * 0.18f,
                    center = Offset(
                        x = size.width * 0.50f,
                        y = size.height * 0.34f,
                    ),
                )

                drawOval(
                    color = iconColor,
                    topLeft = Offset(
                        x = size.width * 0.18f,
                        y = size.height * 0.56f,
                    ),
                    size = Size(
                        width = size.width * 0.64f,
                        height = size.height * 0.34f,
                    ),
                )
            }
        }
    }
}