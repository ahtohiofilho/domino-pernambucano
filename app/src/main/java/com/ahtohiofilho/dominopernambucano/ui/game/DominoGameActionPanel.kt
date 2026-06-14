package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoGameActionPanel(
    uiState: DominoGameUiState,
    onBackToMenuClick: () -> Unit,
    onRoundIntroFinished: () -> Unit,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onPresentationFinished: () -> Unit,
    onStartNextRound: () -> Unit,
    onStartNewMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = DominoSemanticColors.primarySurface,
            contentColor = DominoSemanticColors.primaryTextOnLight,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                    text = "Nenhuma jogada disponível.",
                    textAlign = TextAlign.Center,
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
                text = "Jogador ${phase.playerIndex + 1} jogou ${phase.move.piece.left}-${phase.move.piece.right}.",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Colocando peça na mesa...",
                textAlign = TextAlign.Center,
            )
        }

        is DominoMatchPhase.PresentingPass -> {
            Text(
                text = "Jogador ${phase.playerIndex + 1} tocou.",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )

            PrimaryMenuButton(
                text = "Continuar",
                onClick = onPresentationFinished,
            )
        }

        DominoMatchPhase.RoundSummary -> {
            Text(
                text = "Rodada concluída.",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )

            PrimaryMenuButton(
                text = "Próxima rodada",
                onClick = onStartNextRound,
            )
        }

        DominoMatchPhase.MatchFinished -> {
            Text(
                text = "Partida encerrada.",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
            )

            PrimaryMenuButton(
                text = "Nova partida",
                onClick = onStartNewMatch,
            )
        }
    }
}