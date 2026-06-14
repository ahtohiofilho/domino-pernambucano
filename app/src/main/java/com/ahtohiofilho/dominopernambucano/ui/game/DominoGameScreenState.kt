package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.geometry.Offset
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove

val LOCAL_HAND_PIECE_WIDTH = DominoGameVisualTokens.LocalHandPieceWidth
val LOCAL_HAND_PIECE_HEIGHT = DominoGameVisualTokens.LocalHandPieceHeight

data class DominoDropTargetInWindow(
    val side: BoardSide,
    val positionInWindow: Offset,
)

data class DominoMoveTargetInWindow(
    val positionInWindow: Offset,
    val rotationDegrees: Float,
)

data class LocalDraggedPieceState(
    val piece: DominoPiece,
    val positionInWindow: Offset,
    val playableMoves: List<PlayableMove>,
    val highlightedSide: BoardSide?,
    val isOverLocalHand: Boolean,
)