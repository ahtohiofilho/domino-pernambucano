package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoLocalHand(
    uiState: DominoGameUiState,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gameState = uiState.gameState
    val localPlayer = gameState.players.getOrNull(uiState.localPlayerIndex)
    val playableMovesByPiece = uiState.localPlayableMoves.groupBy { move ->
        move.piece
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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