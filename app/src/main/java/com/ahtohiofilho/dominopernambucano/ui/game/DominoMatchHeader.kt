package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoMatchHeader(
    uiState: DominoGameUiState,
    modifier: Modifier = Modifier,
) {
    val gameState = uiState.gameState
    val localTeamIndex = uiState.localPlayerIndex % 2
    val opponentTeamIndex = if (localTeamIndex == 0) 1 else 0

    val localScore = gameState.teamScores.getOrElse(localTeamIndex) { 0 }
    val opponentScore = gameState.teamScores.getOrElse(opponentTeamIndex) { 0 }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = DominoSemanticColors.primarySurface,
            contentColor = DominoSemanticColors.primaryTextOnLight,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Rodada ${uiState.roundNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DominoSemanticColors.primaryTextOnLight.copy(alpha = 0.72f),
                )

                Text(
                    text = getPhaseLabel(uiState.phase),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
            }

            Text(
                text = "$localScore x $opponentScore",
                fontSize = 34.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                color = DominoColorTokens.PernambucoBlue,
            )
        }
    }
}

private fun getPhaseLabel(
    phase: DominoMatchPhase,
): String {
    return when (phase) {
        DominoMatchPhase.RoundIntro -> "Apresentação da rodada"
        DominoMatchPhase.WaitingForLocalMove -> "Sua vez"
        is DominoMatchPhase.PresentingMove -> "Jogada em andamento"
        is DominoMatchPhase.PresentingPass -> "Toque em andamento"
        DominoMatchPhase.RoundSummary -> "Resumo da rodada"
        DominoMatchPhase.MatchFinished -> "Partida finalizada"
    }
}