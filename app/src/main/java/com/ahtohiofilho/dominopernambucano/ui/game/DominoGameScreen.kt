package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoGameScreen(
    uiState: DominoGameUiState,
    onBackToMenuClick: () -> Unit,
    onRoundIntroFinished: () -> Unit,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onPresentationFinished: () -> Unit,
    onStartNextRound: () -> Unit,
    onStartNewMatch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DominoSemanticColors.appBackground)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            colors = CardDefaults.cardColors(
                containerColor = DominoSemanticColors.primarySurface,
                contentColor = DominoSemanticColors.primaryTextOnLight,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Rodada ${uiState.roundNumber}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )

                Text(
                    text = getPhaseLabel(uiState.phase),
                    style = MaterialTheme.typography.titleMedium,
                )

                ScoreRow(
                    teamScores = uiState.gameState.teamScores,
                )

                Text(
                    text = "Jogador atual: ${uiState.gameState.currentPlayerIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = "Peças na mesa: ${uiState.gameState.board.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                PhaseActions(
                    uiState = uiState,
                    onRoundIntroFinished = onRoundIntroFinished,
                    onLocalMoveSelected = onLocalMoveSelected,
                    onPresentationFinished = onPresentationFinished,
                    onStartNextRound = onStartNextRound,
                    onStartNewMatch = onStartNewMatch,
                )

                SecondaryMenuButton(
                    text = "Voltar ao menu",
                    onClick = onBackToMenuClick,
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(
    teamScores: List<Int>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(
            text = "Dupla 1: ${teamScores.getOrElse(0) { 0 }}",
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Dupla 2: ${teamScores.getOrElse(1) { 0 }}",
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PhaseActions(
    uiState: DominoGameUiState,
    onRoundIntroFinished: () -> Unit,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onPresentationFinished: () -> Unit,
    onStartNextRound: () -> Unit,
    onStartNewMatch: () -> Unit,
) {
    when (val phase = uiState.phase) {
        DominoMatchPhase.RoundIntro -> {
            PrimaryMenuButton(
                text = "Iniciar rodada",
                onClick = onRoundIntroFinished,
            )
        }

        DominoMatchPhase.WaitingForLocalMove -> {
            val firstMove = uiState.localPlayableMoves.firstOrNull()

            if (firstMove == null) {
                Text(
                    text = "Nenhuma jogada local disponível.",
                )
            } else {
                PrimaryMenuButton(
                    text = "Jogar ${firstMove.piece.left}-${firstMove.piece.right}",
                    onClick = {
                        onLocalMoveSelected(firstMove)
                    },
                )
            }
        }

        is DominoMatchPhase.PresentingMove -> {
            Text(
                text = "Jogador ${phase.playerIndex + 1} apresenta ${phase.move.piece.left}-${phase.move.piece.right}.",
            )

            PrimaryMenuButton(
                text = "Concluir apresentação da jogada",
                onClick = onPresentationFinished,
            )
        }

        is DominoMatchPhase.PresentingPass -> {
            Text(
                text = "Jogador ${phase.playerIndex + 1} tocou.",
            )

            PrimaryMenuButton(
                text = "Concluir apresentação do toque",
                onClick = onPresentationFinished,
            )
        }

        DominoMatchPhase.RoundSummary -> {
            Text(
                text = "Resumo da rodada.",
            )

            PrimaryMenuButton(
                text = "Próxima rodada",
                onClick = onStartNextRound,
            )
        }

        DominoMatchPhase.MatchFinished -> {
            Text(
                text = "Partida encerrada.",
                fontWeight = FontWeight.Bold,
            )

            PrimaryMenuButton(
                text = "Nova partida",
                onClick = onStartNewMatch,
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
        is DominoMatchPhase.PresentingMove -> "Apresentando jogada"
        is DominoMatchPhase.PresentingPass -> "Apresentando toque"
        DominoMatchPhase.RoundSummary -> "Resumo da rodada"
        DominoMatchPhase.MatchFinished -> "Partida finalizada"
    }
}