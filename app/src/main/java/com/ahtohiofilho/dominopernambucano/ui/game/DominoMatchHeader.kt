package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoMatchHeader(
    uiState: DominoGameUiState,
    modifier: Modifier = Modifier,
) {
    val gameState = uiState.gameState
    val localTeamIndex = uiState.localPlayerIndex % 2
    val opponentTeamIndex = if (localTeamIndex == 0) {
        1
    } else {
        0
    }

    val localScore = gameState.teamScores.getOrElse(localTeamIndex) { 0 }
    val opponentScore = gameState.teamScores.getOrElse(opponentTeamIndex) { 0 }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = DominoSemanticColors.primarySurface.copy(alpha = 0.96f),
            contentColor = DominoSemanticColors.primaryTextOnLight,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.game_round_number,
                    uiState.roundNumber,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = DominoSemanticColors.primaryTextOnLight.copy(alpha = 0.72f),
            )

            Text(
                text = "$localScore x $opponentScore",
                fontSize = 32.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                color = DominoColorTokens.PernambucoBlue,
            )
        }
    }
}
