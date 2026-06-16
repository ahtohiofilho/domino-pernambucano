package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens

@Composable
fun DominoTableArea(
    gameState: DominoGameState,
    localPlayableMoves: List<PlayableMove>,
    showDropTargets: Boolean,
    highlightedDropSide: BoardSide?,
    animatedPlayableMove: PlayableMove?,
    onDropTargetsChanged: (List<DominoDropTargetInWindow>) -> Unit,
    onAnimatedMoveTargetChanged: (DominoMoveTargetInWindow?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shouldShowBoard =
        !gameState.boardChain.isEmpty() ||
                localPlayableMoves.isNotEmpty() ||
                animatedPlayableMove != null

    val tableShape = RoundedCornerShape(
        DominoGameVisualTokens.TableAreaCornerRadius,
    )

    Box(
        modifier = modifier
            .clip(tableShape)
            .background(
                color = DominoColorTokens.PernambucoBlue.copy(alpha = 0.30f),
            )
            .border(
                border = BorderStroke(
                    width = DominoGameVisualTokens.TableAreaBorderWidth,
                    color = DominoColorTokens.PureWhite.copy(alpha = 0.18f),
                ),
                shape = tableShape,
            )
            .padding(DominoGameVisualTokens.TableAreaContentPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldShowBoard) {
            DominoBoard(
                boardChain = gameState.boardChain,
                playableMoves = localPlayableMoves,
                showDropTargets = showDropTargets,
                highlightedDropSide = highlightedDropSide,
                animatedPlayableMove = animatedPlayableMove,
                onDropTargetsChanged = onDropTargetsChanged,
                onAnimatedMoveTargetChanged = onAnimatedMoveTargetChanged,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            EmptyTableOpeningPiece(
                openingPiece = gameState.openingPiece,
            )
        }
    }
}

@Composable
private fun EmptyTableOpeningPiece(
    openingPiece: DominoPiece?,
) {
    if (openingPiece == null) {
        return
    }

    DominoPieceView(
        piece = openingPiece,
        faceUp = true,
        width = DominoGameVisualTokens.TablePieceReferenceWidth,
        height = DominoGameVisualTokens.TablePieceReferenceHeight,
        isPlayable = true,
    )
}