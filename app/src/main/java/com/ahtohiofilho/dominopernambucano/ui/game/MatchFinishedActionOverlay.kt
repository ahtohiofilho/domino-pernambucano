package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.components.DominoPrimaryButton
import com.ahtohiofilho.dominopernambucano.ui.components.DominoSecondaryButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors


internal const val MatchFinishedOverlayTag = "match_finished_overlay"
internal const val MatchFinishedTitleTag = "match_finished_title"
internal const val MatchFinishedScoreTag = "match_finished_score"
internal const val MatchFinishedPrimaryActionTag =
    "match_finished_primary_action"
internal const val MatchFinishedSecondaryActionTag =
    "match_finished_secondary_action"

@Composable
fun MatchFinishedActionOverlay(
    uiState: DominoGameUiState,
    onStartNewMatch: () -> Unit,
    onBackToMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localTeamIndex = uiState.localPlayerIndex % 2
    val opponentTeamIndex = if (localTeamIndex == 0) {
        1
    } else {
        0
    }

    val localScore = uiState.gameState.teamScores.getOrElse(localTeamIndex) { 0 }
    val opponentScore = uiState.gameState.teamScores.getOrElse(opponentTeamIndex) { 0 }

    val didLocalTeamWin = uiState.gameState.gameWinnerTeamIndex == localTeamIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(MatchFinishedOverlayTag)
            .background(DominoColorTokens.InkBlue.copy(alpha = 0.68f))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = DominoColorTokens.PernambucoBlueDark.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(28.dp),
                )
                .border(
                    width = 1.dp,
                    color = DominoColorTokens.PureWhite.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                modifier = Modifier
                    .testTag(MatchFinishedTitleTag)
                    .semantics {
                        heading()
                    },
                text = if (didLocalTeamWin) {
                    stringResource(R.string.game_victory_label)
                } else {
                    stringResource(R.string.game_defeat_label)
                },
                color = DominoSemanticColors.scoreHighlight,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Text(
                modifier = Modifier
                    .testTag(MatchFinishedScoreTag),
                text = "$localScore × $opponentScore",
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            DominoPrimaryButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MatchFinishedPrimaryActionTag),
                text = stringResource(R.string.game_new_match),
                onClick = onStartNewMatch,
            )

            DominoSecondaryButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MatchFinishedSecondaryActionTag),
                text = stringResource(R.string.game_return_to_menu),
                onClick = onBackToMenuClick,
            )
        }
    }
}
