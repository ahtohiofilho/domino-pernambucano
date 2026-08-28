package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R

import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.online.createDefaultOnlineTableName
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoLocalHand(
    uiState: DominoGameUiState,
    pieces: List<DominoPiece>,
    isWinner: Boolean = false,
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

    val localPlayerName = localPlayer?.name
        ?: stringResource(R.string.game_local_player_fallback)

    val localPlayerCode = createDefaultOnlineTableName(
        displayName = localPlayerName,
    )

    val isCurrent =
        gameState.currentPlayerIndex == uiState.localPlayerIndex

    val canInteractWithHand =
        uiState.phase == DominoMatchPhase.WaitingForLocalMove &&
                isCurrent

    val playableMovesByPiece = uiState.localPlayableMoves.groupBy { move ->
        move.piece
    }

    val winnerAttention = rememberWinnerAttentionMotion(
        isWinner = isWinner,
    )

    val handShape = RoundedCornerShape(
        DominoGameVisualTokens.LocalHandCardCornerRadius,
    )

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = winnerAttention.scale
                    scaleY = winnerAttention.scale
                }
                .onGloballyPositioned { coordinates ->
                    onLocalHandBoundsChanged(coordinates.boundsInWindow())
                },
            shape = handShape,
            border = if (isWinner) {
                BorderStroke(
                    width = 2.dp,
                    color = DominoSemanticColors.scoreHighlight.copy(
                        alpha = winnerAttention.borderAlpha,
                    ),
                )
            } else {
                BorderStroke(
                    width = DominoGameVisualTokens.LocalHandCardBorderWidth,
                    color = DominoSemanticColors.brandBorder.copy(alpha = 0.72f),
                )
            },
            colors = CardDefaults.cardColors(
                containerColor = if (isWinner) {
                    DominoSemanticColors.scoreHighlight.copy(
                        alpha = winnerAttention.containerAlpha,
                    )
                } else {
                    DominoSemanticColors.brandSurfaceElevated.copy(alpha = 0.92f)
                },
                contentColor = DominoSemanticColors.primaryTextOnDark,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = DominoGameVisualTokens.LocalHandCardElevation,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        DominoGameVisualTokens.LocalHandCardPadding,
                    )
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(
                    space = DominoGameVisualTokens.LocalHandPieceSpacing,
                    alignment = Alignment.CenterHorizontally,
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
                        isDraggable = canInteractWithHand,
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


        DominoPlayerCodeLabel(
            name = localPlayerCode,
            isCurrent = isCurrent,
            isWinner = isWinner,
            compact = false,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = -DominoGameVisualTokens.LocalPlayerCodeLift),
        )
    }
}

@Composable
private fun LocalHandPiece(
    piece: DominoPiece,
    isPlayable: Boolean,
    isDraggable: Boolean,
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

    val dragModifier = if (isDraggable) {
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
