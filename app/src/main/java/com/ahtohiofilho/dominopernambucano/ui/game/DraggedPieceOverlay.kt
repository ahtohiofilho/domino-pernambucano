package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun DraggedPieceOverlay(
    draggedPieceState: LocalDraggedPieceState?,
    modifier: Modifier = Modifier,
) {
    val dragState = draggedPieceState ?: return
    val density = LocalDensity.current

    val pieceWidthPx = with(density) {
        LOCAL_HAND_PIECE_WIDTH.toPx()
    }

    val pieceHeightPx = with(density) {
        LOCAL_HAND_PIECE_HEIGHT.toPx()
    }

    DominoPieceView(
        piece = dragState.piece,
        faceUp = true,
        width = LOCAL_HAND_PIECE_WIDTH,
        height = LOCAL_HAND_PIECE_HEIGHT,
        isPlayable = true,
        modifier = modifier
            .offset {
                IntOffset(
                    x = (dragState.positionInWindow.x - pieceWidthPx / 2f).roundToInt(),
                    y = (dragState.positionInWindow.y - pieceHeightPx / 2f).roundToInt(),
                )
            }
            .graphicsLayer {
                alpha = if (dragState.isOverLocalHand) {
                    DominoGameVisualTokens.DraggedPieceOverHandAlpha
                } else {
                    DominoGameVisualTokens.DraggedPieceAlpha
                }

                shadowElevation = DominoGameVisualTokens.DraggedPieceShadowElevation
                scaleX = DominoGameVisualTokens.DraggedPieceScale
                scaleY = DominoGameVisualTokens.DraggedPieceScale
            },
        onClick = null,
    )
}