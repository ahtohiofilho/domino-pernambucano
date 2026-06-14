package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

private const val ROUND_SUMMARY_ENTER_MILLIS = 280

private val ROUND_SUMMARY_PANEL_RADIUS = 28.dp
private val ROUND_SUMMARY_PANEL_PADDING = 16.dp

@Composable
fun RoundSummaryRevealOverlay(
    gameState: DominoGameState,
    localPlayerIndex: Int,
    modifier: Modifier = Modifier,
    onStartNextRound: () -> Unit,
) {
    val alphaAnim = remember(gameState.roundWinKind) {
        Animatable(0f)
    }

    val scaleAnim = remember(gameState.roundWinKind) {
        Animatable(0.96f)
    }

    LaunchedEffect(gameState.roundWinKind) {
        alphaAnim.snapTo(0f)
        scaleAnim.snapTo(0.96f)

        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ROUND_SUMMARY_ENTER_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )

        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ROUND_SUMMARY_ENTER_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DominoColorTokens.InkBlue.copy(alpha = 0.62f))
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = alphaAnim.value
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                }
                .background(
                    color = DominoColorTokens.PernambucoBlueDark.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(ROUND_SUMMARY_PANEL_RADIUS),
                )
                .border(
                    width = 1.dp,
                    color = DominoColorTokens.PureWhite.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(ROUND_SUMMARY_PANEL_RADIUS),
                )
                .padding(ROUND_SUMMARY_PANEL_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundSummaryTitle(
                gameState = gameState,
            )

            RoundSummaryScoreLine(
                gameState = gameState,
                localPlayerIndex = localPlayerIndex,
            )

            SleepingPiecesReveal(
                pieces = gameState.sleepingPieces,
            )

            RoundSummaryPlayersReveal(
                players = gameState.players,
                localPlayerIndex = localPlayerIndex,
                winnerPlayerIndex = gameState.roundWinnerPlayerIndex,
            )

            PrimaryMenuButton(
                text = "Próxima rodada",
                onClick = onStartNextRound,
            )
        }
    }
}

@Composable
private fun RoundSummaryTitle(
    gameState: DominoGameState,
) {
    val title = getRoundSummaryTitle(
        winKind = gameState.roundWinKind,
    )

    val subtitle = getRoundSummarySubtitle(
        gameState = gameState,
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = title,
            color = DominoSemanticColors.scoreHighlight,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = subtitle,
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoundSummaryScoreLine(
    gameState: DominoGameState,
    localPlayerIndex: Int,
) {
    val localTeamIndex = localPlayerIndex % 2
    val opponentTeamIndex = if (localTeamIndex == 0) {
        1
    } else {
        0
    }

    val localScore = gameState.teamScores.getOrElse(localTeamIndex) { 0 }
    val opponentScore = gameState.teamScores.getOrElse(opponentTeamIndex) { 0 }

    Row(
        modifier = Modifier
            .background(
                color = DominoColorTokens.PureWhite.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
            )
            .border(
                width = 1.dp,
                color = DominoColorTokens.PureWhite.copy(alpha = 0.16f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(
                horizontal = 20.dp,
                vertical = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$localScore",
            color = DominoSemanticColors.scoreHighlight,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = "  ×  ",
            color = DominoColorTokens.PureWhite.copy(alpha = 0.72f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = "$opponentScore",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.82f),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun SleepingPiecesReveal(
    pieces: List<DominoPiece>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = DominoColorTokens.PureWhite.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp),
            )
            .border(
                width = 1.dp,
                color = DominoColorTokens.PureWhite.copy(alpha = 0.16f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(
                horizontal = 10.dp,
                vertical = 9.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "DORME",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.76f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pieces.forEach { piece ->
                DominoPieceView(
                    piece = piece,
                    faceUp = true,
                    width = DominoGameVisualTokens.TablePieceReferenceWidth,
                    height = DominoGameVisualTokens.TablePieceReferenceHeight,
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun RoundSummaryPlayersReveal(
    players: List<DominoPlayer>,
    localPlayerIndex: Int,
    winnerPlayerIndex: Int?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        players.forEach { player ->
            RoundSummaryPlayerHandReveal(
                player = player,
                isLocalPlayer = player.id == localPlayerIndex,
                isWinner = player.id == winnerPlayerIndex,
            )
        }
    }
}

@Composable
private fun RoundSummaryPlayerHandReveal(
    player: DominoPlayer,
    isLocalPlayer: Boolean,
    isWinner: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isWinner) {
                    DominoSemanticColors.scoreHighlight.copy(alpha = 0.18f)
                } else {
                    DominoColorTokens.PureWhite.copy(alpha = 0.08f)
                },
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = if (isWinner) 2.dp else 1.dp,
                color = if (isWinner) {
                    DominoSemanticColors.scoreHighlight.copy(alpha = 0.88f)
                } else {
                    DominoColorTokens.PureWhite.copy(alpha = 0.13f)
                },
                shape = RoundedCornerShape(16.dp),
            )
            .padding(
                horizontal = 10.dp,
                vertical = 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = player.name,
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )

            Text(
                text = when {
                    isWinner -> "venceu"
                    isLocalPlayer -> "você"
                    else -> "${player.hand.sumOf { piece -> piece.left + piece.right }} pts na mão"
                },
                color = if (isWinner) {
                    DominoSemanticColors.scoreHighlight
                } else {
                    DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.62f)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            player.hand.forEach { piece ->
                DominoPieceView(
                    piece = piece,
                    faceUp = true,
                    width = 24.dp,
                    height = 44.dp,
                    onClick = null,
                )
            }
        }
    }
}

private fun getRoundSummaryTitle(
    winKind: RoundWinKind?,
): String {
    return when (winKind) {
        RoundWinKind.COMMON -> "BATIDA"
        RoundWinKind.DOUBLE -> "BATIDA DE CARROÇA"
        RoundWinKind.LA_E_LO -> "LÁ E LÔ"
        RoundWinKind.CRUZADA -> "CRUZADA"
        RoundWinKind.CLOSED -> "JOGO FECHADO"
        RoundWinKind.CLOSED_TIE -> "EMPATE NO FECHADO"
        null -> "RODADA CONCLUÍDA"
    }
}

private fun getRoundSummarySubtitle(
    gameState: DominoGameState,
): String {
    val winnerName = gameState.roundWinnerPlayerIndex
        ?.let { playerIndex -> gameState.players.getOrNull(playerIndex)?.name }

    val multiplierText = if (gameState.scoreMultiplier > 1) {
        " · valor x${gameState.scoreMultiplier}"
    } else {
        ""
    }

    return when {
        gameState.roundWinKind == RoundWinKind.CLOSED_TIE -> {
            "Ninguém pontuou. A próxima rodada aumenta o valor."
        }

        winnerName != null -> {
            "$winnerName pontuou$multiplierText."
        }

        else -> {
            "Confira as peças antes da próxima rodada."
        }
    }
}