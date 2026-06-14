package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoTableArea(
    gameState: DominoGameState,
    localPlayableMoves: List<PlayableMove>,
    showDropTargets: Boolean,
    highlightedDropSide: BoardSide?,
    onDropTargetsChanged: (List<DominoDropTargetInWindow>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shouldShowBoard =
        !gameState.boardChain.isEmpty() || localPlayableMoves.isNotEmpty()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(34.dp))
            .background(
                color = DominoColorTokens.PernambucoBlue.copy(alpha = 0.34f),
            )
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = DominoColorTokens.PureWhite.copy(alpha = 0.20f),
                ),
                shape = RoundedCornerShape(34.dp),
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldShowBoard) {
            DominoBoard(
                boardChain = gameState.boardChain,
                playableMoves = localPlayableMoves,
                showDropTargets = showDropTargets,
                highlightedDropSide = highlightedDropSide,
                onDropTargetsChanged = onDropTargetsChanged,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            EmptyTableMessage(
                openingPiece = gameState.openingPiece,
            )
        }
    }
}

@Composable
private fun EmptyTableMessage(
    openingPiece: DominoPiece?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Mesa aguardando abertura",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.76f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        if (openingPiece != null) {
            DominoPieceView(
                piece = openingPiece,
                faceUp = true,
                width = 68.dp,
                height = 40.dp,
                isPlayable = true,
            )

            Text(
                text = "Peça de saída",
                color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}