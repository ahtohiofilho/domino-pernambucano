package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBackNavigationButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoHomeNavigationButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun MatchFinishedActionOverlay(
    uiState: DominoGameUiState,
    onStartNewMatch: () -> Unit,
    onBackToMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDetails by remember(uiState.gameState, uiState.postMatchStatistics) {
        mutableStateOf(false)
    }

    if (showDetails) {
        PostMatchStatisticsOverlay(
            uiState = uiState,
            onStartNewMatch = onStartNewMatch,
            onBackToSummary = { showDetails = false },
            onBackToMenuClick = onBackToMenuClick,
            modifier = modifier,
        )
    } else {
        PostMatchSummaryOverlay(
            uiState = uiState,
            onViewDetails = { showDetails = true },
            onStartNewMatch = onStartNewMatch,
            onBackToMenuClick = onBackToMenuClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun PostMatchSummaryOverlay(
    uiState: DominoGameUiState,
    onViewDetails: () -> Unit,
    onStartNewMatch: () -> Unit,
    onBackToMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localTeamIndex = uiState.localPlayerIndex % 2
    val opponentTeamIndex = if (localTeamIndex == 0) 1 else 0
    val localScore = uiState.gameState.teamScores.getOrElse(localTeamIndex) { 0 }
    val opponentScore = uiState.gameState.teamScores.getOrElse(opponentTeamIndex) { 0 }
    val completedRounds = uiState.postMatchStatistics?.completedRounds
        ?.takeIf { rounds -> rounds > 0 }
        ?: uiState.roundNumber
    val outcomeText = when (uiState.gameState.gameWinnerTeamIndex) {
        localTeamIndex -> stringResource(R.string.post_match_victory)
        opponentTeamIndex -> stringResource(R.string.post_match_defeat)
        else -> stringResource(R.string.post_match_draw)
    }
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DominoColorTokens.InkBlue.copy(alpha = 0.72f))
            .padding(horizontal = 18.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(DominoColorTokens.PernambucoBlueDark.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    color = DominoColorTokens.PureWhite.copy(alpha = 0.18f),
                    shape = shape,
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.post_match_complete),
                color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.66f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )

            Text(
                text = outcomeText,
                color = DominoSemanticColors.scoreHighlight,
                fontSize = 36.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "$localScore × $opponentScore",
                color = DominoSemanticColors.primaryTextOnDark,
                fontSize = 48.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            PostMatchPill(
                text = stringResource(
                    if (uiState.onlinePresentationId != null) {
                        R.string.post_match_online_rounds
                    } else {
                        R.string.post_match_local_rounds
                    },
                    completedRounds,
                ),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onViewDetails,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.post_match_action_details),
                    fontWeight = FontWeight.Black,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onStartNewMatch,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = DominoColorTokens.PureWhite.copy(alpha = 0.72f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DominoSemanticColors.primaryTextOnDark,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.post_match_action_play_again),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                DominoHomeNavigationButton(
                    onClick = onBackToMenuClick,
                    contentDescription = stringResource(
                        R.string.post_match_action_home,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PostMatchStatisticsOverlay(
    uiState: DominoGameUiState,
    onStartNewMatch: () -> Unit,
    onBackToSummary: () -> Unit,
    onBackToMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gameState = uiState.gameState
    val statistics = uiState.postMatchStatistics
    val localTeamIndex = uiState.localPlayerIndex % 2
    val opponentTeamIndex = if (localTeamIndex == 0) 1 else 0
    val localScore = gameState.teamScores.getOrElse(localTeamIndex) { 0 }
    val opponentScore = gameState.teamScores.getOrElse(opponentTeamIndex) { 0 }
    val completedRounds = statistics?.completedRounds
        ?.takeIf { rounds -> rounds > 0 }
        ?: uiState.roundNumber
    val outcomeText = when (gameState.gameWinnerTeamIndex) {
        localTeamIndex -> stringResource(R.string.post_match_victory)
        opponentTeamIndex -> stringResource(R.string.post_match_defeat)
        else -> stringResource(R.string.post_match_draw)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DominoColorTokens.PernambucoBlueDark,
                        DominoColorTokens.InkBlue,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DominoBackNavigationButton(
                    onClick = onBackToSummary,
                    contentDescription = stringResource(
                        R.string.post_match_action_back_to_summary,
                    ),
                )

                Spacer(modifier = Modifier.weight(1f))

                DominoHomeNavigationButton(
                    onClick = onBackToMenuClick,
                    contentDescription = stringResource(
                        R.string.post_match_action_home,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.post_match_details_title),
                        color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp,
                    )
                    Text(
                        text = outcomeText,
                        color = DominoSemanticColors.scoreHighlight,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "$localScore × $opponentScore",
                        color = DominoSemanticColors.primaryTextOnDark,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                }


            }

            PostMatchPill(
                text = stringResource(
                    if (uiState.onlinePresentationId != null) {
                        R.string.post_match_online_rounds
                    } else {
                        R.string.post_match_local_rounds
                    },
                    completedRounds,
                ),
            )

            LegendPanel()

            CompactTeamTable(
                title = stringResource(R.string.post_match_your_team),
                teamBalanceDelta = statistics?.teamBalanceDeltaByTeam
                    ?.getOrElse(localTeamIndex) { 0 } ?: 0,
                players = gameState.players.withIndex()
                    .filter { (index, _) -> index % 2 == localTeamIndex },
                statistics = statistics,
                localPlayerIndex = uiState.localPlayerIndex,
                mvpSeatIndex = statistics?.mvpSeatIndex,
                accentColor = DominoSemanticColors.scoreHighlight,
            )

            CompactTeamTable(
                title = stringResource(R.string.post_match_opponents),
                teamBalanceDelta = statistics?.teamBalanceDeltaByTeam
                    ?.getOrElse(opponentTeamIndex) { 0 } ?: 0,
                players = gameState.players.withIndex()
                    .filter { (index, _) -> index % 2 == opponentTeamIndex },
                statistics = statistics,
                localPlayerIndex = uiState.localPlayerIndex,
                mvpSeatIndex = statistics?.mvpSeatIndex,
                accentColor = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.84f),
            )
            Spacer(modifier = Modifier.height(4.dp))

            SecondaryMenuButton(
                text = stringResource(R.string.post_match_action_play_again),
                onClick = onStartNewMatch,
            )


            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LegendPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DominoColorTokens.PernambucoBlueDark.copy(alpha = 0.82f))
            .border(
                width = 1.dp,
                color = DominoColorTokens.PureWhite.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.post_match_competitive_metrics),
            color = DominoSemanticColors.scoreHighlight,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            LegendItem(
                label = stringResource(R.string.post_match_points_scored),
                kind = MetricKind.POINTS,
            )
            LegendItem(
                label = stringResource(R.string.post_match_touches_given),
                kind = MetricKind.OUTGOING,
            )
            LegendItem(
                label = stringResource(R.string.post_match_automatic_rounds),
                kind = MetricKind.AUTOMATIC,
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    kind: MetricKind,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.widthIn(min = 78.dp),
    ) {
        MetricGlyph(kind = kind, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.86f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun CompactTeamTable(
    title: String,
    teamBalanceDelta: Int,
    players: List<IndexedValue<DominoPlayer>>,
    statistics: PostMatchStatistics?,
    localPlayerIndex: Int,
    mvpSeatIndex: Int?,
    accentColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DominoColorTokens.PernambucoBlueDark.copy(alpha = 0.84f))
            .border(
                width = 1.dp,
                color = DominoColorTokens.PureWhite.copy(alpha = 0.14f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = accentColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = "${stringResource(R.string.post_match_team_balance)} " +
                        formatSignedRankingValue(teamBalanceDelta),
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
        }

        PlayerTableHeader()

        players.forEach { indexedPlayer ->
            val playerStats = statistics?.players?.firstOrNull { stats ->
                stats.seatIndex == indexedPlayer.index
            }

            CompactPlayerRow(
                player = indexedPlayer.value,
                stats = playerStats,
                isLocalPlayer = indexedPlayer.index == localPlayerIndex,
                isMvp = indexedPlayer.index == mvpSeatIndex,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun PlayerTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        HeaderGlyph(kind = MetricKind.POINTS)
        Spacer(modifier = Modifier.width(8.dp))
        HeaderGlyph(kind = MetricKind.OUTGOING)
        Spacer(modifier = Modifier.width(8.dp))
        HeaderGlyph(kind = MetricKind.AUTOMATIC)
    }
}

@Composable
private fun HeaderGlyph(kind: MetricKind) {
    Box(
        modifier = Modifier.width(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetricGlyph(kind = kind, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CompactPlayerRow(
    player: DominoPlayer,
    stats: PostMatchPlayerStatistics?,
    isLocalPlayer: Boolean,
    isMvp: Boolean,
    accentColor: Color,
) {
    val rowShape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isMvp) {
                    DominoSemanticColors.scoreHighlight.copy(alpha = 0.10f)
                } else {
                    DominoColorTokens.InkBlue.copy(alpha = 0.42f)
                },
            )
            .then(
                if (isMvp) {
                    Modifier.border(
                        width = 1.dp,
                        color = DominoSemanticColors.scoreHighlight.copy(alpha = 0.62f),
                        shape = rowShape,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = player.name,
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (isMvp) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.post_match_mvp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            DominoSemanticColors.scoreHighlight.copy(alpha = 0.18f),
                        )
                        .border(
                            width = 1.dp,
                            color = DominoSemanticColors.scoreHighlight.copy(alpha = 0.52f),
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    color = DominoSemanticColors.scoreHighlight,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }

            if (isLocalPlayer) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.post_match_you),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.20f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        ValueCell(value = stats?.pointsScored?.toString() ?: "—")
        Spacer(modifier = Modifier.width(8.dp))
        ValueCell(value = stats?.touchesGiven?.toString() ?: "—")
        Spacer(modifier = Modifier.width(8.dp))
        ValueCell(value = stats?.automaticRounds?.toString() ?: "—")
    }
}

private fun formatSignedRankingValue(value: Int): String {
    return when {
        value > 0 -> "+$value"
        else -> value.toString()
    }
}
@Composable
private fun ValueCell(value: String) {
    Box(
        modifier = Modifier.width(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private enum class MetricKind {
    POINTS,
    OUTGOING,
    AUTOMATIC,
}

@Composable
private fun MetricGlyph(
    kind: MetricKind,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = size.minDimension * 0.10f
        val color = DominoSemanticColors.scoreHighlight

        when (kind) {
            MetricKind.POINTS -> {
                drawCircle(
                    color = color.copy(alpha = 0.18f),
                    radius = size.minDimension * 0.44f,
                    center = Offset(w * 0.50f, h * 0.50f),
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.16f,
                    center = Offset(w * 0.50f, h * 0.50f),
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.08f),
                    end = Offset(w * 0.50f, h * 0.28f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.72f),
                    end = Offset(w * 0.50f, h * 0.92f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.08f, h * 0.50f),
                    end = Offset(w * 0.28f, h * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.72f, h * 0.50f),
                    end = Offset(w * 0.92f, h * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            MetricKind.OUTGOING -> {
                drawLine(
                    color = color,
                    start = Offset(w * 0.14f, h * 0.50f),
                    end = Offset(w * 0.80f, h * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.80f, h * 0.50f),
                    end = Offset(w * 0.58f, h * 0.28f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.80f, h * 0.50f),
                    end = Offset(w * 0.58f, h * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            MetricKind.AUTOMATIC -> {
                drawRect(
                    color = color.copy(alpha = 0.18f),
                    topLeft = Offset(w * 0.16f, h * 0.28f),
                    size = Size(w * 0.68f, h * 0.56f),
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.28f),
                    end = Offset(w * 0.50f, h * 0.12f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.07f,
                    center = Offset(w * 0.50f, h * 0.09f),
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.08f,
                    center = Offset(w * 0.36f, h * 0.50f),
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.08f,
                    center = Offset(w * 0.64f, h * 0.50f),
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.34f, h * 0.70f),
                    end = Offset(w * 0.66f, h * 0.70f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun PostMatchPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DominoColorTokens.PureWhite.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = DominoColorTokens.PureWhite.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.78f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}