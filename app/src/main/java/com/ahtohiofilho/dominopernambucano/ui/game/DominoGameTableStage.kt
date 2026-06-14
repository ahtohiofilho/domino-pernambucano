package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
    animatedPlayableMove: PlayableMove?,
    onDropTargetsChanged: (List<DominoDropTargetInWindow>) -> Unit,
    onAnimatedMoveTargetChanged: (DominoMoveTargetInWindow?) -> Unit,
    onPlayerSeatBoundsChanged: (Int, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val isCompactWidth = maxWidth < 430.dp
        val isCompactHeight = maxHeight < 430.dp
        val useCompactSeats = isCompactWidth || isCompactHeight

        val verticalGap = if (isCompactHeight) {
            2.dp
        } else {
            6.dp
        }

        val horizontalGap = if (isCompactWidth) {
            2.dp
        } else {
            6.dp
        }

        val tableHorizontalPadding = if (isCompactWidth) {
            0.dp
        } else {
            4.dp
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalGap),
        ) {
            DominoPlayerSeat(
                name = gameState.players.getOrNull(2)?.name ?: "Jogador 3",
                pieces = gameState.players.getOrNull(2)?.hand.orEmpty(),
                isCurrent = gameState.currentPlayerIndex == 2,
                orientation = DominoPlayerSeatOrientation.HORIZONTAL,
                compact = useCompactSeats,
                faceUp = false,
                onBoundsChanged = { bounds ->
                    onPlayerSeatBoundsChanged(
                        2,
                        bounds,
                    )
                },
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
            ) {
                DominoPlayerSeat(
                    name = gameState.players.getOrNull(1)?.name ?: "Jogador 2",
                    pieces = gameState.players.getOrNull(1)?.hand.orEmpty(),
                    isCurrent = gameState.currentPlayerIndex == 1,
                    orientation = DominoPlayerSeatOrientation.VERTICAL,
                    compact = true,
                    faceUp = false,
                    onBoundsChanged = { bounds ->
                        onPlayerSeatBoundsChanged(
                            1,
                            bounds,
                        )
                    },
                )

                DominoTableArea(
                    gameState = gameState,
                    localPlayableMoves = localPlayableMoves,
                    showDropTargets = showDropTargets,
                    highlightedDropSide = highlightedDropSide,
                    animatedPlayableMove = animatedPlayableMove,
                    onDropTargetsChanged = onDropTargetsChanged,
                    onAnimatedMoveTargetChanged = onAnimatedMoveTargetChanged,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = tableHorizontalPadding),
                )

                DominoPlayerSeat(
                    name = gameState.players.getOrNull(3)?.name ?: "Jogador 4",
                    pieces = gameState.players.getOrNull(3)?.hand.orEmpty(),
                    isCurrent = gameState.currentPlayerIndex == 3,
                    orientation = DominoPlayerSeatOrientation.VERTICAL,
                    compact = true,
                    faceUp = false,
                    onBoundsChanged = { bounds ->
                        onPlayerSeatBoundsChanged(
                            3,
                            bounds,
                        )
                    },
                )
            }
        }
    }
}