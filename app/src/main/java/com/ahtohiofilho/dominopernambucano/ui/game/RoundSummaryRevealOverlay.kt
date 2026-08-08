package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlinx.coroutines.delay

private const val ROUND_SUMMARY_ENTER_MILLIS = 260

internal const val RoundSummaryOverlayTag = "round_summary_overlay"
internal const val RoundSummaryTitleTag = "round_summary_title"
internal const val RoundSummaryWinnerTag = "round_summary_winner"
internal const val RoundSummaryScoreTag = "round_summary_score"
internal const val RoundSummaryMicroHintTag = "round_summary_micro_hint"
internal const val RoundSummarySleepingPiecesTag = "round_summary_sleeping_pieces"

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
        Animatable(0.98f)
    }

    LaunchedEffect(
        gameState.roundWinKind,
        gameState.teamScores,
    ) {
        alphaAnim.snapTo(0f)
        scaleAnim.snapTo(0.98f)

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

        delay(DominoMatchTiming.RoundSummaryAutoAdvanceMillis)

        onStartNextRound()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(RoundSummaryOverlayTag)
            .background(DominoColorTokens.InkBlue.copy(alpha = 0.18f))
            .padding(12.dp),
    ) {
        RoundSummaryTableReadBadge(
            gameState = gameState,
            localPlayerIndex = localPlayerIndex,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = alphaAnim.value
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                },
        )

        SleepingPiecesReveal(
            pieces = gameState.sleepingPieces,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .testTag(RoundSummarySleepingPiecesTag)
                .padding(
                    end = 4.dp,
                    bottom = 72.dp,
                )
                .graphicsLayer {
                    alpha = alphaAnim.value
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                },
        )
    }
}

@Composable
private fun RoundSummaryTableReadBadge(
    gameState: DominoGameState,
    localPlayerIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = DominoColorTokens.PernambucoBlueDark.copy(alpha = 0.86f),
                shape = RoundedCornerShape(22.dp),
            )
            .border(
                width = 1.dp,
                color = DominoColorTokens.PureWhite.copy(alpha = 0.18f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            modifier = Modifier
                .testTag(RoundSummaryTitleTag)
                .semantics {
                    heading()
                },
            text = getRoundSummaryTitle(
                winKind = gameState.roundWinKind,
            ),
            color = DominoSemanticColors.scoreHighlight,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        RoundSummaryWinnerLine(
            gameState = gameState,
            modifier = Modifier
                .testTag(RoundSummaryWinnerTag),
        )

        RoundSummaryScoreLine(
            gameState = gameState,
            localPlayerIndex = localPlayerIndex,
            modifier = Modifier
                .testTag(RoundSummaryScoreTag),
        )

        RoundSummaryMicroHint(
            gameState = gameState,
            modifier = Modifier
                .testTag(RoundSummaryMicroHintTag),
        )
    }
}

@Composable
private fun RoundSummaryWinnerLine(
    gameState: DominoGameState,
    modifier: Modifier = Modifier,
) {
    val winnerPlayerIndex = gameState.roundWinnerPlayerIndex
        ?: return

    val winnerName = gameState.players
        .getOrNull(winnerPlayerIndex)
        ?.name
        ?.ifBlank { null }
        ?: stringResource(
            R.string.game_player_fallback,
            winnerPlayerIndex + 1,
        )

    Text(
        modifier = modifier,
        text = stringResource(
            R.string.game_round_winner_format,
            winnerName,
        ),
        color = DominoSemanticColors.primaryTextOnDark,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun RoundSummaryScoreLine(
    gameState: DominoGameState,
    localPlayerIndex: Int,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
            color = DominoColorTokens.PureWhite.copy(alpha = 0.70f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = "$opponentScore",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.86f),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun RoundSummaryMicroHint(
    gameState: DominoGameState,
    modifier: Modifier = Modifier,
) {
    val hint = getRoundSummaryMicroHint(
        gameState = gameState,
    ) ?: return

    Text(
        modifier = modifier,
        text = hint,
        color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.68f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun SleepingPiecesReveal(
    pieces: List<DominoPiece>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
                horizontal = 9.dp,
                vertical = 8.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.game_blocked_label),
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pieces.forEach { piece ->
                DominoPieceView(
                    piece = piece,
                    faceUp = true,
                    width = 22.dp,
                    height = 40.dp,
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun getRoundSummaryTitle(
    winKind: RoundWinKind?,
): String {
    return when (winKind) {
        RoundWinKind.COMMON -> stringResource(R.string.game_round_common)
        RoundWinKind.DOUBLE -> stringResource(R.string.game_round_double)
        RoundWinKind.LA_E_LO -> stringResource(R.string.game_round_la_e_lo)
        RoundWinKind.CRUZADA -> stringResource(R.string.game_round_crossed)
        RoundWinKind.CLOSED -> stringResource(R.string.game_round_points)
        RoundWinKind.CLOSED_TIE -> stringResource(R.string.game_round_tie)
        null -> stringResource(R.string.game_round_label)
    }
}

@Composable
private fun getRoundSummaryMicroHint(
    gameState: DominoGameState,
): String? {
    if (
        gameState.scoreMultiplier > 1 &&
        gameState.roundWinKind != RoundWinKind.CLOSED_TIE
    ) {
        return "x${gameState.scoreMultiplier}"
    }

    return when (gameState.roundWinKind) {
        RoundWinKind.CLOSED_TIE -> stringResource(
            R.string.game_next_multiplier_format,
            gameState.scoreMultiplier + 1,
        )
        RoundWinKind.CLOSED -> stringResource(R.string.game_lowest_hand)
        else -> null
    }
}
