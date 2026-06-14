package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove

@Composable
fun DominoGameTableStage(
    gameState: DominoGameState,
    localPlayableMoves: List<PlayableMove>,
    showDropTargets: Boolean,
    highlightedDropSide: BoardSide?,
    onDropTargetsChanged: (List<DominoDropTargetInWindow>) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val isCompactWidth = maxWidth < 430.dp
        val isCompactHeight = maxHeight < 430.dp
        val useCompactSeats = isCompactWidth || isCompactHeight

        val verticalGap = if (isCompactHeight) {
            4.dp
        } else {
            8.dp
        }

        val tableHorizontalPadding = if (isCompactWidth) {
            4.dp
        } else {
            8.dp
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalGap),
        ) {
            DominoPlayerSeat(
                name = gameState.players.getOrNull(2)?.name ?: "Jogador 3",
                piecesCount = gameState.players.getOrNull(2)?.hand?.size ?: 0,
                isCurrent = gameState.currentPlayerIndex == 2,
                compact = useCompactSeats,
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    space = if (isCompactWidth) 4.dp else 8.dp,
                ),
            ) {
                DominoPlayerSeat(
                    name = gameState.players.getOrNull(1)?.name ?: "Jogador 2",
                    piecesCount = gameState.players.getOrNull(1)?.hand?.size ?: 0,
                    isCurrent = gameState.currentPlayerIndex == 1,
                    compact = true,
                )

                DominoTableArea(
                    gameState = gameState,
                    localPlayableMoves = localPlayableMoves,
                    showDropTargets = showDropTargets,
                    highlightedDropSide = highlightedDropSide,
                    onDropTargetsChanged = onDropTargetsChanged,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = tableHorizontalPadding),
                )

                DominoPlayerSeat(
                    name = gameState.players.getOrNull(3)?.name ?: "Jogador 4",
                    piecesCount = gameState.players.getOrNull(3)?.hand?.size ?: 0,
                    isCurrent = gameState.currentPlayerIndex == 3,
                    compact = true,
                )
            }
        }
    }
}