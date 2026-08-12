package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
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

internal fun resolveDominoMatchHeaderCode(
    name: String,
    participantType: DominoParticipantType,
    playerIndex: Int,
): String {
    if (participantType == DominoParticipantType.APPLICATION) {
        return "BOT"
    }

    val normalized = name
        .trim()
        .uppercase(Locale.ROOT)

    if (normalized.matches(Regex("^[A-Z0-9]{3}$"))) {
        return normalized
    }

    return "P" + (playerIndex + 1)
        .toString()
        .padStart(
            length = 2,
            padChar = '0',
        )
}

internal fun buildDominoMatchHeaderTeamLabels(
    playerCodes: List<String>,
    localPlayerIndex: Int,
): DominoMatchHeaderTeamLabels {
    fun codeAt(index: Int): String {
        return playerCodes.getOrNull(index) ?: "P00"
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                DominoColorTokens.PernambucoBlueDark.copy(alpha = 0.98f),
            contentColor = DominoSemanticColors.primaryTextOnDark,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = 7.dp,
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

            Text(
                text = "#${uiState.roundNumber}",
                modifier = Modifier
                    .weight(0.38f)
                    .widthIn(min = 48.dp),
                color = DominoSemanticColors.primaryTextOnDark.copy(
                    alpha = 0.76f,
                ),
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                .size(32.dp)
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
                modifier = Modifier.size(29.dp),
            )
        }
    }
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
            fontSize = 13.sp,
            lineHeight = 15.sp,
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
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
