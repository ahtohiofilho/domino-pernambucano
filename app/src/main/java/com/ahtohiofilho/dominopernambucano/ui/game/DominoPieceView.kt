package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlin.math.min

@Composable
fun DominoPieceView(
    piece: DominoPiece,
    faceUp: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 58.dp,
    height: Dp = 34.dp,
    isPlayable: Boolean = false,
    rotationDegrees: Float = 0f,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotationDegrees
            }
            .width(width)
            .height(height)
            .clip(shape)
            .border(
                width = if (isPlayable) 2.dp else 1.dp,
                color = if (isPlayable) {
                    DominoSemanticColors.playableMove
                } else {
                    DominoColorTokens.InkBlue.copy(alpha = 0.22f)
                },
                shape = shape,
            )
            .then(clickableModifier),
    ) {
        Canvas(
            modifier = Modifier
                .width(width)
                .height(height),
        ) {
            val backgroundColor = if (faceUp) {
                DominoColorTokens.SurfaceWhite
            } else {
                DominoColorTokens.PernambucoBlue
            }

            drawRoundRect(
                color = backgroundColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    x = 8.dp.toPx(),
                    y = 8.dp.toPx(),
                ),
            )

            if (!faceUp) {
                drawBackPattern()
                return@Canvas
            }

            val dividerColor = DominoColorTokens.InkBlue.copy(alpha = 0.20f)
            val pipColor = DominoColorTokens.InkBlue

            drawLine(
                color = dividerColor,
                start = Offset(
                    x = size.width / 2f,
                    y = 5.dp.toPx(),
                ),
                end = Offset(
                    x = size.width / 2f,
                    y = size.height - 5.dp.toPx(),
                ),
                strokeWidth = 1.dp.toPx(),
            )

            drawPips(
                value = piece.left,
                left = 0f,
                top = 0f,
                width = size.width / 2f,
                height = size.height,
                color = pipColor,
            )

            drawPips(
                value = piece.right,
                left = size.width / 2f,
                top = 0f,
                width = size.width / 2f,
                height = size.height,
                color = pipColor,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackPattern() {
    val lineColor = DominoColorTokens.PureWhite.copy(alpha = 0.22f)

    drawLine(
        color = lineColor,
        start = Offset(
            x = size.width * 0.22f,
            y = size.height * 0.20f,
        ),
        end = Offset(
            x = size.width * 0.78f,
            y = size.height * 0.80f,
        ),
        strokeWidth = 1.2.dp.toPx(),
    )

    drawLine(
        color = lineColor,
        start = Offset(
            x = size.width * 0.78f,
            y = size.height * 0.20f,
        ),
        end = Offset(
            x = size.width * 0.22f,
            y = size.height * 0.80f,
        ),
        strokeWidth = 1.2.dp.toPx(),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPips(
    value: Int,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    val radius = min(width, height) * 0.075f

    val x1 = left + width * 0.28f
    val x2 = left + width * 0.50f
    val x3 = left + width * 0.72f

    val y1 = top + height * 0.28f
    val y2 = top + height * 0.50f
    val y3 = top + height * 0.72f

    fun pip(
        x: Float,
        y: Float,
    ) {
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(
                x = x,
                y = y,
            ),
        )
    }

    when (value) {
        0 -> Unit

        1 -> {
            pip(x2, y2)
        }

        2 -> {
            pip(x1, y1)
            pip(x3, y3)
        }

        3 -> {
            pip(x1, y1)
            pip(x2, y2)
            pip(x3, y3)
        }

        4 -> {
            pip(x1, y1)
            pip(x3, y1)
            pip(x1, y3)
            pip(x3, y3)
        }

        5 -> {
            pip(x1, y1)
            pip(x3, y1)
            pip(x2, y2)
            pip(x1, y3)
            pip(x3, y3)
        }

        6 -> {
            pip(x1, y1)
            pip(x3, y1)
            pip(x1, y2)
            pip(x3, y2)
            pip(x1, y3)
            pip(x3, y3)
        }
    }
}