package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import java.util.Locale

internal data class DominoMatchHeaderTeamLabels(
    val localTeam: String,
    val opponentTeam: String,
)

internal fun resolveDominoVisiblePlayerCode(
    name: String,
): String {
    val normalized = name
        .trim()
        .uppercase(Locale.ROOT)

    return if (normalized.matches(Regex("^[A-Z0-9]{3}$"))) {
        normalized
    } else {
        "\u2014"
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveDominoMatchHeaderCode(
    name: String,
    participantType: DominoParticipantType,
    playerIndex: Int,
): String {
    return resolveDominoVisiblePlayerCode(
        name = name,
    )
}

internal fun buildDominoMatchHeaderTeamLabels(
    playerCodes: List<String>,
    localPlayerIndex: Int,
): DominoMatchHeaderTeamLabels {
    fun codeAt(index: Int): String {
        return playerCodes.getOrNull(index) ?: "\u2014"
    }

    val localIndex = localPlayerIndex.mod(4)
    val leftIndex = (localIndex + 1) % 4
    val partnerIndex = (localIndex + 2) % 4
    val rightIndex = (localIndex + 3) % 4

    return DominoMatchHeaderTeamLabels(
        localTeam =
            codeAt(localIndex) + "·" + codeAt(partnerIndex),
        opponentTeam =
            codeAt(leftIndex) + "·" + codeAt(rightIndex),
    )
}

@Composable
fun DominoMatchHeader(
    uiState: DominoGameUiState,
    onExitMatchClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val gameState = uiState.gameState
    val localTeamIndex = uiState.localPlayerIndex % 2
    val opponentTeamIndex = if (localTeamIndex == 0) {
        1
    } else {
        0
    }

    val localScore = gameState.teamScores.getOrElse(localTeamIndex) { 0 }
    val opponentScore = gameState.teamScores.getOrElse(opponentTeamIndex) { 0 }

    val playerCodes = gameState.players.mapIndexed { index, player ->
        resolveDominoMatchHeaderCode(
            name = player.name,
            participantType = player.participantType,
            playerIndex = index,
        )
    }

    val teamLabels = buildDominoMatchHeaderTeamLabels(
        playerCodes = playerCodes,
        localPlayerIndex = uiState.localPlayerIndex,
    )

    val headerShape = RoundedCornerShape(
        DominoGameVisualTokens.HeaderCornerRadius,
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = headerShape,
        border = BorderStroke(
            width = DominoGameVisualTokens.HeaderBorderWidth,
            color = DominoSemanticColors.brandBorder.copy(alpha = 0.72f),
        ),
        colors = CardDefaults.cardColors(
            containerColor =
                DominoSemanticColors.brandSurfaceElevated.copy(alpha = 0.96f),
            contentColor = DominoSemanticColors.primaryTextOnDark,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = DominoGameVisualTokens.HeaderElevation,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = DominoGameVisualTokens.HeaderHorizontalPadding,
                    vertical = DominoGameVisualTokens.HeaderVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DominoMatchBrand(
                modifier = Modifier.weight(0.38f),
            )

            DominoMatchScoreboard(
                localTeamLabel = teamLabels.localTeam,
                opponentTeamLabel = teamLabels.opponentTeam,
                localScore = localScore,
                opponentScore = opponentScore,
                modifier = Modifier.weight(2.46f),
            )

            DominoRoundBadge(
                roundNumber = uiState.roundNumber,
            )

            if (onExitMatchClick != null) {
                IconButton(
                    onClick = onExitMatchClick,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            id = R.drawable.ic_exit_match,
                        ),
                        contentDescription = stringResource(
                            R.string.game_return_to_menu,
                        ),
                        tint = DominoSemanticColors.scoreHighlight,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DominoMatchBrand(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(DominoGameVisualTokens.HeaderBrandDiscSize)
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape,
                    clip = false,
                )
                .clip(CircleShape)
                .background(
                    DominoColorTokens.PernambucoBlue.copy(
                        alpha = 0.42f,
                    ),
                )
                .border(
                    width = 1.dp,
                    color = DominoSemanticColors.scoreHighlight.copy(
                        alpha = 0.86f,
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.ic_launcher_foreground,
                ),
                contentDescription = null,
                modifier = Modifier.size(31.dp),
            )
        }
    }
}

@Composable
private fun DominoRoundBadge(
    roundNumber: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "#$roundNumber",
        modifier = modifier,
        color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.90f),
        fontSize = 21.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun DominoMatchScoreboard(
    localTeamLabel: String,
    opponentTeamLabel: String,
    localScore: Int,
    opponentScore: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = localTeamLabel,
            modifier = Modifier.weight(1f),
            color = DominoSemanticColors.primaryTextOnDark.copy(
                alpha = 0.82f,
            ),
            fontSize = 21.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )

        Text(
            text = "$localScore × $opponentScore",
            modifier = Modifier.padding(
                horizontal = 7.dp,
            ),
            color = DominoSemanticColors.scoreHighlight,
            fontSize = 24.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Text(
            text = opponentTeamLabel,
            modifier = Modifier.weight(1f),
            color = DominoSemanticColors.primaryTextOnDark.copy(
                alpha = 0.82f,
            ),
            fontSize = 21.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
