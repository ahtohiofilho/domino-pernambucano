package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoPlayerSeat(
    name: String,
    piecesCount: Int,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onBoundsChanged: (Rect?) -> Unit = {},
) {
    val minWidth = if (compact) 70.dp else 104.dp
    val maxWidth = if (compact) 86.dp else 138.dp

    val horizontalPadding = if (compact) 7.dp else 12.dp
    val verticalPadding = if (compact) 7.dp else 10.dp

    Card(
        modifier = modifier
            .widthIn(
                min = minWidth,
                max = maxWidth,
            )
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInWindow())
            },
        shape = RoundedCornerShape(
            if (compact) 14.dp else 18.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                DominoSemanticColors.scoreHighlight.copy(alpha = 0.94f)
            } else {
                DominoColorTokens.PureWhite.copy(alpha = 0.12f)
            },
            contentColor = if (isCurrent) {
                DominoColorTokens.InkBlue
            } else {
                DominoSemanticColors.primaryTextOnDark
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                if (compact) 4.dp else 6.dp,
            ),
        ) {
            Text(
                text = name,
                style = if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelLarge
                },
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    if (compact) (-6).dp else (-5).dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(piecesCount.coerceIn(0, 6)) {
                    DominoPieceBackMini(
                        compact = compact,
                    )
                }
            }

            if (isCurrent) {
                Text(
                    text = "vez",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun DominoPieceBackMini(
    compact: Boolean,
) {
    Box(
        modifier = Modifier
            .size(
                width = if (compact) 13.dp else 18.dp,
                height = if (compact) 21.dp else 28.dp,
            )
            .clip(RoundedCornerShape(if (compact) 3.dp else 4.dp))
            .background(DominoColorTokens.PernambucoBlue)
            .border(
                width = 1.dp,
                color = DominoColorTokens.PureWhite.copy(alpha = 0.26f),
                shape = RoundedCornerShape(if (compact) 3.dp else 4.dp),
            ),
    )
}