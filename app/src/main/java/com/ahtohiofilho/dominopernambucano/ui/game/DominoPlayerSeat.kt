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
) {
    Card(
        modifier = modifier.widthIn(
            min = 104.dp,
            max = 138.dp,
        ),
        shape = RoundedCornerShape(18.dp),
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
                horizontal = 12.dp,
                vertical = 10.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy((-5).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(piecesCount.coerceIn(0, 6)) {
                    DominoPieceBackMini()
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
private fun DominoPieceBackMini() {
    Box(
        modifier = Modifier
            .size(
                width = 18.dp,
                height = 28.dp,
            )
            .clip(RoundedCornerShape(4.dp))
            .background(DominoColorTokens.PernambucoBlue)
            .border(
                width = 1.dp,
                color = DominoColorTokens.PureWhite.copy(alpha = 0.26f),
                shape = RoundedCornerShape(4.dp),
            ),
    )
}