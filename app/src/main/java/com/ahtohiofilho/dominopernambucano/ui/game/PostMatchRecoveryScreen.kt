package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

/**
 * Genuine finished-result presentation after the match binding is already
 * released. Only the user's next explicit navigation requests an interstitial.
 * No synthetic game, server reconnect or auto-ad-on-startup is involved.
 */
@Composable
fun PostMatchRecoveryScreen(
    receipt: PostMatchRecoverySnapshot,
    onContinue: () -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onContinue)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        DominoColorTokens.PernambucoBlueDark,
                        DominoColorTokens.InkBlue,
                    ),
                ),
            )
            .padding(horizontal = 18.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(
                    DominoColorTokens.PernambucoBlueDark,
                    RoundedCornerShape(28.dp),
                )
                .border(
                    1.dp,
                    DominoColorTokens.PureWhite.copy(alpha = 0.2f),
                    RoundedCornerShape(28.dp),
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.post_match_complete),
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when (receipt.localTeamWon) {
                    true -> stringResource(R.string.post_match_victory)
                    false -> stringResource(R.string.post_match_defeat)
                    null -> stringResource(R.string.post_match_draw)
                },
                color = DominoSemanticColors.scoreHighlight,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${receipt.localScore} × ${receipt.opponentScore}",
                color = DominoSemanticColors.primaryTextOnDark,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(
                    postMatchRoundsLabelResource(receipt.matchMode),
                    receipt.completedRounds,
                ),
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (showDetails) {
                RecoveryTeamDetails(
                    title = stringResource(R.string.post_match_your_team),
                    players = receipt.players.filter { it.seatIndex % 2 == receipt.localTeamIndex },
                    receipt = receipt,
                )
                RecoveryTeamDetails(
                    title = stringResource(R.string.post_match_opponents),
                    players = receipt.players.filter { it.seatIndex % 2 != receipt.localTeamIndex },
                    receipt = receipt,
                )
            }
            Button(
                onClick = { showDetails = !showDetails },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (showDetails) {
                        stringResource(R.string.post_match_action_back_to_summary)
                    } else {
                        stringResource(R.string.post_match_action_details)
                    },
                )
            }
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.post_match_action_home),
                    color = DominoSemanticColors.primaryTextOnDark,
                )
            }
        }
    }
}

@Composable
private fun RecoveryTeamDetails(
    title: String,
    players: List<PostMatchRecoveryPlayer>,
    receipt: PostMatchRecoverySnapshot,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = DominoSemanticColors.scoreHighlight,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        val teamIndex = players.firstOrNull()?.seatIndex?.rem(2)
        if (teamIndex != null) {
            val balance = receipt.teamBalanceDeltaByTeam.getOrElse(teamIndex) { 0 }
            Text(
                text = stringResource(R.string.post_match_team_balance) +
                    ": " + (if (balance > 0) "+" else "") + balance,
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        players.forEach { player ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = player.name +
                        if (receipt.mvpSeatIndex == player.seatIndex) " ★" else "",
                    modifier = Modifier.weight(1f),
                    color = DominoSemanticColors.primaryTextOnDark,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${player.pointsScored} / ${player.touchesGiven} / ${player.automaticRounds}",
                    color = DominoSemanticColors.primaryTextOnDark,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            text = stringResource(R.string.post_match_competitive_metrics) +
                ": " +
                stringResource(R.string.post_match_points_scored) + " / " +
                stringResource(R.string.post_match_touches_given) + " / " +
                stringResource(R.string.post_match_automatic_rounds),
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
