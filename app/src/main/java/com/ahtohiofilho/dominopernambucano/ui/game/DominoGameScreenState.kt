package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.geometry.Offset
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase

val LOCAL_HAND_PIECE_WIDTH = DominoGameVisualTokens.LocalHandPieceWidth
val LOCAL_HAND_PIECE_HEIGHT = DominoGameVisualTokens.LocalHandPieceHeight

data class DominoDropTargetInWindow(
    val side: BoardSide,
    val positionInWindow: Offset,
)

/**
 * Identidade estável de uma peça que está em trânsito para a mesa.
 *
 * A chave inclui o estado da mesa anterior à jogada. Assim, mesmo fora do
 * modo online, o destino medido para uma apresentação não pode ser
 * reaproveitado por outra apresentação posterior.
 */
data class DominoMovePresentationKey(
    val onlinePresentationId: String?,
    val roundNumber: Int,
    val boardChainBeforeMove: DominoBoardChain,
    val playerIndex: Int,
    val move: PlayableMove,
)

data class DominoMoveTargetInWindow(
    val presentationKey: DominoMovePresentationKey,
    val positionInWindow: Offset,
    val rotationDegrees: Float,
)

internal fun DominoMoveTargetInWindow?.forPresentation(
    presentationKey: DominoMovePresentationKey,
): DominoMoveTargetInWindow? {
    return this?.takeIf { target ->
        target.presentationKey == presentationKey
    }
}

data class LocalDraggedPieceState(
    val piece: DominoPiece,
    val positionInWindow: Offset,
    val playableMoves: List<PlayableMove>,
    val highlightedSide: BoardSide?,
    val isOverLocalHand: Boolean,
)

internal fun shouldAllowTableMagnification(
    phase: DominoMatchPhase,
    boardHasPieces: Boolean,
    dragActive: Boolean,
): Boolean {
    @Suppress("UNUSED_VARIABLE")
    val presentationPhaseDoesNotGateMagnification = phase

    return boardHasPieces &&
        !dragActive
}

internal fun shouldElevateTurnCountdownAboveOverlays(
    tableMagnified: Boolean,
    magnificationEnabled: Boolean,
): Boolean {
    return tableMagnified && magnificationEnabled
}