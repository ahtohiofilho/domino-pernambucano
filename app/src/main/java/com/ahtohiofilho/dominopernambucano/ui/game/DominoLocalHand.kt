package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoLocalHand(
    uiState: DominoGameUiState,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onLocalHandBoundsChanged: (Rect?) -> Unit,
    onPieceDragStart: (DominoPiece, Offset) -> Unit,
    onPieceDrag: (Offset) -> Unit,
    onPieceDragEnd: () -> Unit,
    onPieceDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gameState = uiState.gameState
    val localPlayer = gameState.players.getOrNull(uiState.localPlayerIndex)
    val playableMovesByPiece = uiState.localPlayableMoves.groupBy { move ->
        move.piece
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onLocalHandBoundsChanged(coordinates.boundsInWindow())
            },
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

                    LocalHandPiece(
                        piece = piece,
                        isPlayable = isPlayable,
                        playableMove = playableMove,
                        onLocalMoveSelected = onLocalMoveSelected,
                        onPieceDragStart = onPieceDragStart,
                        onPieceDrag = onPieceDrag,
                        onPieceDragEnd = onPieceDragEnd,
                        onPieceDragCancel = onPieceDragCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalHandPiece(
    piece: DominoPiece,
    isPlayable: Boolean,
    playableMove: PlayableMove?,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onPieceDragStart: (DominoPiece, Offset) -> Unit,
    onPieceDrag: (Offset) -> Unit,
    onPieceDragEnd: () -> Unit,
    onPieceDragCancel: () -> Unit,
) {
    var pieceBoundsInWindow by remember(piece) {
        mutableStateOf<Rect?>(null)
    }

    val dragModifier = if (isPlayable) {
        Modifier.pointerInput(piece) {
            detectDragGestures(
                onDragStart = { startOffset ->
                    val bounds = pieceBoundsInWindow
                        ?: return@detectDragGestures

                    onPieceDragStart(
                        piece,
                        bounds.topLeft + startOffset,
                    )
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onPieceDrag(dragAmount)
                },
                onDragEnd = {
                    onPieceDragEnd()
                },
                onDragCancel = {
                    onPieceDragCancel()
                },
            )
        }
    } else {
        Modifier
    }

    val visualModifier = if (isPlayable) {
        Modifier.graphicsLayer {
            shadowElevation = 8f
            scaleX = 1.04f
            scaleY = 1.04f
        }
    } else {
        Modifier.graphicsLayer {
            alpha = 0.58f
        }
    }

    DominoPieceView(
        piece = piece,
        faceUp = true,
        width = LOCAL_HAND_PIECE_WIDTH,
        height = LOCAL_HAND_PIECE_HEIGHT,
        isPlayable = isPlayable,
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                pieceBoundsInWindow = coordinates.boundsInWindow()
            }
            .then(visualModifier)
            .then(dragModifier),
        onClick = if (playableMove != null) {
            {
                onLocalMoveSelected(playableMove)
            }
        } else {
            null
        },
    )
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