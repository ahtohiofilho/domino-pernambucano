package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.animateFloatAsState
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
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoLocalHand(
    uiState: DominoGameUiState,
    pieces: List<DominoPiece>,
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

    val canInteractWithHand =
        uiState.phase == DominoMatchPhase.WaitingForLocalMove &&
                gameState.currentPlayerIndex == uiState.localPlayerIndex

    val playableMovesByPiece = uiState.localPlayableMoves.groupBy { move ->
        move.piece
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onLocalHandBoundsChanged(coordinates.boundsInWindow())
            },
        shape = RoundedCornerShape(
            DominoGameVisualTokens.LocalHandCardCornerRadius,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DominoColorTokens.PureWhite.copy(alpha = 0.10f),
            contentColor = DominoSemanticColors.primaryTextOnDark,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                DominoGameVisualTokens.LocalHandCardPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(
                DominoGameVisualTokens.LocalHandHeaderBottomGap,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = localPlayer?.name ?: "Você",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = DominoSemanticColors.primaryTextOnDark,
                )

                CurrentPlayerBadge(
                    isCurrent = gameState.currentPlayerIndex == uiState.localPlayerIndex,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(
                    DominoGameVisualTokens.LocalHandPieceSpacing,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pieces.forEach { piece ->
                    val playableMoves = if (canInteractWithHand) {
                        playableMovesByPiece[piece].orEmpty()
                    } else {
                        emptyList()
                    }

                    val playableMove = playableMoves.firstOrNull()

                    LocalHandPiece(
                        piece = piece,
                        isPlayable = playableMoves.isNotEmpty(),
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

    val targetAlpha = if (isPlayable) {
        DominoGameVisualTokens.LocalDefaultPieceAlpha
    } else {
        DominoGameVisualTokens.LocalUnavailablePieceAlpha
    }

    val pieceAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        label = "localHandPieceAlpha",
    )

    val pieceScale by animateFloatAsState(
        targetValue = if (isPlayable) {
            DominoGameVisualTokens.LocalPlayablePieceScale
        } else {
            1f
        },
        label = "localHandPieceScale",
    )

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

    Box(
        modifier = Modifier
            .size(
                width = DominoGameVisualTokens.LocalHandPieceSlotWidth,
                height = DominoGameVisualTokens.LocalHandPieceSlotHeight,
            )
            .onGloballyPositioned { coordinates ->
                pieceBoundsInWindow = coordinates.boundsInWindow()
            }
            .then(dragModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = pieceAlpha
                scaleX = pieceScale
                scaleY = pieceScale
                shadowElevation = if (isPlayable) {
                    DominoGameVisualTokens.LocalPlayableShadowElevation
                } else {
                    0f
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            DominoPieceView(
                piece = piece,
                faceUp = true,
                width = DominoGameVisualTokens.LocalHandPieceWidth,
                height = DominoGameVisualTokens.LocalHandPieceHeight,
                isPlayable = false,
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

@Composable
private fun CurrentPlayerBadge(
    isCurrent: Boolean,
) {
    Box(
        modifier = Modifier
            .size(
                size = if (isCurrent) {
                    DominoGameVisualTokens.LocalCurrentTurnIndicatorSize
                } else {
                    DominoGameVisualTokens.LocalWaitingTurnIndicatorSize
                },
            )
            .clip(CircleShape)
            .background(
                color = if (isCurrent) {
                    DominoSemanticColors.playableMove
                } else {
                    DominoColorTokens.PureWhite.copy(alpha = 0.28f)
                },
            ),
    )
}