package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.getTeamIndexForPlayer
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ROUND_INTRO_ENTER_MILLIS = 620
private const val ROUND_INTRO_SCORE_MILLIS = 520
private const val ROUND_INTRO_HOLD_MILLIS = 1_050L
private const val ROUND_INTRO_EXIT_MILLIS = 460

private val ROUND_INTRO_PANEL_HORIZONTAL_PADDING = 22.dp
private val ROUND_INTRO_PANEL_VERTICAL_PADDING = 24.dp

data class RoundIntroPresentationTeams(
    val topTeam: RoundIntroTeamPresentation,
    val bottomTeam: RoundIntroTeamPresentation,
)

data class RoundIntroTeamPresentation(
    val playerNames: List<String>,
    val score: Int,
    val isLocalTeam: Boolean,
)

fun buildRoundIntroTeamPresentations(
    players: List<DominoPlayer>,
    teamScores: List<Int>,
    localPlayerIndex: Int,
): RoundIntroPresentationTeams {
    val localTeamIndex = getTeamIndexForPlayer(localPlayerIndex)
    val opponentTeamIndex = if (localTeamIndex == 0) {
        1
    } else {
        0
    }

    return RoundIntroPresentationTeams(
        topTeam = buildRoundIntroTeamPresentation(
            players = players,
            teamScores = teamScores,
            teamIndex = localTeamIndex,
            isLocalTeam = true,
        ),
        bottomTeam = buildRoundIntroTeamPresentation(
            players = players,
            teamScores = teamScores,
            teamIndex = opponentTeamIndex,
            isLocalTeam = false,
        ),
    )
}

private fun buildRoundIntroTeamPresentation(
    players: List<DominoPlayer>,
    teamScores: List<Int>,
    teamIndex: Int,
    isLocalTeam: Boolean,
): RoundIntroTeamPresentation {
    val names = players
        .filterIndexed { playerIndex, _ ->
            getTeamIndexForPlayer(playerIndex) == teamIndex
        }
        .map { player ->
            player.name
        }

    return RoundIntroTeamPresentation(
        playerNames = names,
        score = teamScores.getOrNull(teamIndex) ?: 0,
        isLocalTeam = isLocalTeam,
    )
}

@Composable
fun RoundIntroPresentationOverlay(
    roundNumber: Int,
    teams: RoundIntroPresentationTeams,
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit,
) {
    val titleAlpha = remember(roundNumber) {
        Animatable(0f)
    }

    val topProgress = remember(roundNumber) {
        Animatable(-1.15f)
    }

    val bottomProgress = remember(roundNumber) {
        Animatable(1.15f)
    }

    val centerAlpha = remember(roundNumber) {
        Animatable(0f)
    }

    val centerScale = remember(roundNumber) {
        Animatable(0.88f)
    }

    LaunchedEffect(roundNumber, teams) {
        titleAlpha.snapTo(0f)
        topProgress.snapTo(-1.15f)
        bottomProgress.snapTo(1.15f)
        centerAlpha.snapTo(0f)
        centerScale.snapTo(0.88f)

        titleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ROUND_INTRO_SCORE_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )

        launch {
            topProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = ROUND_INTRO_ENTER_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }

        bottomProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = ROUND_INTRO_ENTER_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )

        launch {
            centerAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ROUND_INTRO_SCORE_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }

        centerScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ROUND_INTRO_SCORE_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )

        delay(ROUND_INTRO_HOLD_MILLIS)

        launch {
            titleAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = ROUND_INTRO_EXIT_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }

        launch {
            centerAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = ROUND_INTRO_EXIT_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }

        launch {
            topProgress.animateTo(
                targetValue = 1.15f,
                animationSpec = tween(
                    durationMillis = ROUND_INTRO_EXIT_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }

        bottomProgress.animateTo(
            targetValue = -1.15f,
            animationSpec = tween(
                durationMillis = ROUND_INTRO_EXIT_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )

        onAnimationFinished()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DominoColorTokens.InkBlue.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current

        val screenWidthPx = with(density) {
            maxWidth.toPx()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROUND_INTRO_PANEL_HORIZONTAL_PADDING)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DominoColorTokens.PernambucoBlueDark.copy(alpha = 0.98f),
                            DominoColorTokens.PernambucoBlue.copy(alpha = 0.96f),
                        ),
                    ),
                    shape = RoundedCornerShape(32.dp),
                )
                .border(
                    width = 1.dp,
                    color = DominoColorTokens.PureWhite.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(32.dp),
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = ROUND_INTRO_PANEL_VERTICAL_PADDING,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "RODADA $roundNumber",
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                },
                color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.90f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(
                modifier = Modifier.height(22.dp),
            )

            RoundIntroPlayerPair(
                team = teams.topTeam,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (topProgress.value * screenWidthPx).roundToInt(),
                        y = 0,
                    )
                },
            )

            Spacer(
                modifier = Modifier.height(12.dp),
            )

            RoundIntroScoreNumber(
                score = teams.topTeam.score,
                modifier = Modifier.graphicsLayer {
                    alpha = centerAlpha.value
                    scaleX = centerScale.value
                    scaleY = centerScale.value
                },
            )

            RoundIntroVersusMark(
                modifier = Modifier.graphicsLayer {
                    alpha = centerAlpha.value
                    scaleX = centerScale.value
                    scaleY = centerScale.value
                },
            )

            RoundIntroScoreNumber(
                score = teams.bottomTeam.score,
                modifier = Modifier.graphicsLayer {
                    alpha = centerAlpha.value
                    scaleX = centerScale.value
                    scaleY = centerScale.value
                },
            )

            Spacer(
                modifier = Modifier.height(12.dp),
            )

            RoundIntroPlayerPair(
                team = teams.bottomTeam,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (bottomProgress.value * screenWidthPx).roundToInt(),
                        y = 0,
                    )
                },
            )
        }
    }
}

@Composable
private fun RoundIntroPlayerPair(
    team: RoundIntroTeamPresentation,
    modifier: Modifier = Modifier,
) {
    val names = team.playerNames.joinToString(
        separator = "   •   ",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (team.isLocalTeam) {
                    DominoColorTokens.PureWhite.copy(alpha = 0.16f)
                } else {
                    DominoColorTokens.PureWhite.copy(alpha = 0.09f)
                },
                shape = RoundedCornerShape(20.dp),
            )
            .border(
                width = 1.dp,
                color = if (team.isLocalTeam) {
                    DominoSemanticColors.scoreHighlight.copy(alpha = 0.52f)
                } else {
                    DominoColorTokens.PureWhite.copy(alpha = 0.14f)
                },
                shape = RoundedCornerShape(20.dp),
            )
            .padding(
                horizontal = 14.dp,
                vertical = 12.dp,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = names.ifBlank { "Dupla" },
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoundIntroScoreNumber(
    score: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = score.toString(),
        modifier = modifier,
        color = DominoSemanticColors.scoreHighlight,
        fontSize = 58.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RoundIntroVersusMark(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "×",
        modifier = modifier,
        color = DominoColorTokens.PureWhite.copy(alpha = 0.78f),
        fontSize = 34.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
}