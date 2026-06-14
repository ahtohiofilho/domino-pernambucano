package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
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
    val gameState = uiState.gameState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DominoSemanticColors.appBackground,
                        DominoColorTokens.PernambucoBlueDark,
                    ),
                ),
            )
            .padding(
                horizontal = 14.dp,
                vertical = 12.dp,
            ),
    ) {
        MatchHeader(
            uiState = uiState,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            PlayerSeat(
                name = gameState.players.getOrNull(2)?.name ?: "Jogador 3",
                piecesCount = gameState.players.getOrNull(2)?.hand?.size ?: 0,
                isCurrent = gameState.currentPlayerIndex == 2,
                modifier = Modifier
                    .align(Alignment.TopCenter),
            )

            PlayerSeat(
                name = gameState.players.getOrNull(1)?.name ?: "Jogador 2",
                piecesCount = gameState.players.getOrNull(1)?.hand?.size ?: 0,
                isCurrent = gameState.currentPlayerIndex == 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 4.dp),
            )

            PlayerSeat(
                name = gameState.players.getOrNull(3)?.name ?: "Jogador 4",
                piecesCount = gameState.players.getOrNull(3)?.hand?.size ?: 0,
                isCurrent = gameState.currentPlayerIndex == 3,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-4).dp),
            )

            TableArea(
                gameState = gameState,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.58f),
            )
        }

        LocalPlayerArea(
            uiState = uiState,
            onLocalMoveSelected = onLocalMoveSelected,
        )

        Spacer(
            modifier = Modifier.height(10.dp),
        )

        GameActionPanel(
            uiState = uiState,
            onBackToMenuClick = onBackToMenuClick,
            onRoundIntroFinished = onRoundIntroFinished,
            onLocalMoveSelected = onLocalMoveSelected,
            onPresentationFinished = onPresentationFinished,
            onStartNextRound = onStartNextRound,
            onStartNewMatch = onStartNewMatch,
        )
    }
}

@Composable
private fun MatchHeader(
    uiState: DominoGameUiState,
) {
    val gameState = uiState.gameState
    val localTeamIndex = uiState.localPlayerIndex % 2
    val opponentTeamIndex = if (localTeamIndex == 0) 1 else 0

    val localScore = gameState.teamScores.getOrElse(localTeamIndex) { 0 }
    val opponentScore = gameState.teamScores.getOrElse(opponentTeamIndex) { 0 }

    Card(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun BoxScope.TableArea(
    gameState: DominoGameState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(34.dp))
            .background(
                color = DominoColorTokens.PernambucoBlue.copy(alpha = 0.34f),
            )
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = DominoColorTokens.PureWhite.copy(alpha = 0.20f),
                ),
                shape = RoundedCornerShape(34.dp),
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (gameState.board.isEmpty()) {
            EmptyTableMessage(
                openingPiece = gameState.openingPiece,
            )
        } else {
            BoardPiecesRow(
                board = gameState.board,
            )
        }
    }
}

@Composable
private fun EmptyTableMessage(
    openingPiece: DominoPiece?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Mesa aguardando abertura",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.76f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        if (openingPiece != null) {
            DominoPieceView(
                piece = openingPiece,
                faceUp = true,
                width = 68.dp,
                height = 40.dp,
                isPlayable = true,
            )

            Text(
                text = "Peça de saída",
                color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BoardPiecesRow(
    board: List<DominoPiece>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        board.forEach { piece ->
            DominoPieceView(
                piece = piece,
                faceUp = true,
                width = 58.dp,
                height = 34.dp,
            )
        }
    }
}

@Composable
private fun PlayerSeat(
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

@Composable
private fun LocalPlayerArea(
    uiState: DominoGameUiState,
    onLocalMoveSelected: (PlayableMove) -> Unit,
) {
    val gameState = uiState.gameState
    val localPlayer = gameState.players.getOrNull(uiState.localPlayerIndex)
    val playableMovesByPiece = uiState.localPlayableMoves.groupBy { move ->
        move.piece
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = DominoColorTokens.PureWhite.copy(alpha = 0.11f),
            contentColor = DominoSemanticColors.primaryTextOnDark,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = localPlayer?.name ?: "Você",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )

                CurrentPlayerBadge(
                    isCurrent = gameState.currentPlayerIndex == uiState.localPlayerIndex,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                localPlayer?.hand.orEmpty().forEach { piece ->
                    val playableMove = playableMovesByPiece[piece]?.firstOrNull()
                    val isPlayable = playableMove != null

                    DominoPieceView(
                        piece = piece,
                        faceUp = true,
                        width = 66.dp,
                        height = 40.dp,
                        isPlayable = isPlayable,
                        onClick = if (playableMove != null) {
                            {
                                onLocalMoveSelected(playableMove)
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentPlayerBadge(
    isCurrent: Boolean,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                color = if (isCurrent) {
                    DominoSemanticColors.scoreHighlight
                } else {
                    DominoColorTokens.PureWhite.copy(alpha = 0.12f)
                },
            )
            .padding(
                horizontal = 10.dp,
                vertical = 5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    color = if (isCurrent) {
                        DominoSemanticColors.playableMove
                    } else {
                        DominoColorTokens.PureWhite.copy(alpha = 0.42f)
                    },
                ),
        )

        Text(
            text = if (isCurrent) "sua vez" else "aguardando",
            color = if (isCurrent) {
                DominoColorTokens.InkBlue
            } else {
                DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.72f)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun GameActionPanel(
    uiState: DominoGameUiState,
    onBackToMenuClick: () -> Unit,
    onRoundIntroFinished: () -> Unit,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onPresentationFinished: () -> Unit,
    onStartNextRound: () -> Unit,
    onStartNewMatch: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

            PrimaryMenuButton(
                text = "Continuar",
                onClick = onPresentationFinished,
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