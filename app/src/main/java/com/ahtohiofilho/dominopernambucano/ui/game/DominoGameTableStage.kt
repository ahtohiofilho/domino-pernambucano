package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove

@Composable
fun DominoGameTableStage(
    gameState: DominoGameState,
    localPlayerIndex: Int,
    localPlayableMoves: List<PlayableMove>,
    showDropTargets: Boolean,
    highlightedDropSide: BoardSide?,
    animatedPlayableMove: PlayableMove?,
    animatedMovePresentationKey: DominoMovePresentationKey?,
    revealOpponentHands: Boolean,
    roundWinnerPlayerIndex: Int?,
    visualPiecesForPlayer: (Int) -> List<DominoPiece>,
    onDropTargetsChanged: (List<DominoDropTargetInWindow>) -> Unit,
    onAnimatedMoveTargetChanged: (DominoMoveTargetInWindow) -> Unit,
    onPlayerSeatBoundsChanged: (Int, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seatLayout = calculateDominoRelativeSeatLayout(
        localPlayerIndex = localPlayerIndex,
        playerCount = gameState.players.size,
    )

    val topPlayerIndex = seatLayout.topPlayerIndex
    val leftPlayerIndex = seatLayout.leftPlayerIndex
    val rightPlayerIndex = seatLayout.rightPlayerIndex

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val compact = maxWidth < 390.dp || maxHeight < 650.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = DominoGameVisualTokens.TableStageHorizontalPadding,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DominoGameVisualTokens.OpponentTopSeatSlotHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                DominoPlayerSeat(
                    name = gameState.players
                        .getOrNull(topPlayerIndex)
                        ?.name
                        ?: "Jogador ${topPlayerIndex + 1}",
                    pieces = visualPiecesForPlayer(topPlayerIndex),
                    isCurrent = !revealOpponentHands &&
                            gameState.currentPlayerIndex == topPlayerIndex,
                    orientation = DominoPlayerSeatOrientation.HORIZONTAL,
                    compact = compact,
                    faceUp = revealOpponentHands,
                    isWinner = roundWinnerPlayerIndex == topPlayerIndex,
                    onBoundsChanged = { bounds ->
                        onPlayerSeatBoundsChanged(
                            topPlayerIndex,
                            bounds,
                        )
                    },
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(DominoGameVisualTokens.OpponentSideSeatSlotWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    DominoPlayerSeat(
                        name = gameState.players
                            .getOrNull(leftPlayerIndex)
                            ?.name
                            ?: "Jogador ${leftPlayerIndex + 1}",
                        pieces = visualPiecesForPlayer(leftPlayerIndex),
                        isCurrent = !revealOpponentHands &&
                                gameState.currentPlayerIndex == leftPlayerIndex,
                        orientation = DominoPlayerSeatOrientation.VERTICAL,
                        compact = true,
                        faceUp = revealOpponentHands,
                        isWinner = roundWinnerPlayerIndex == leftPlayerIndex,
                        onBoundsChanged = { bounds ->
                            onPlayerSeatBoundsChanged(
                                leftPlayerIndex,
                                bounds,
                            )
                        },
                    )
                }

                DominoTableArea(
                    gameState = gameState,
                    localPlayableMoves = localPlayableMoves,
                    showDropTargets = showDropTargets,
                    highlightedDropSide = highlightedDropSide,
                    animatedPlayableMove = animatedPlayableMove,
                    animatedMovePresentationKey = animatedMovePresentationKey,
                    onDropTargetsChanged = onDropTargetsChanged,
                    onAnimatedMoveTargetChanged = onAnimatedMoveTargetChanged,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            horizontal = DominoGameVisualTokens.TableStageInnerHorizontalPadding,
                        ),
                )

                Box(
                    modifier = Modifier
                        .width(DominoGameVisualTokens.OpponentSideSeatSlotWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    DominoPlayerSeat(
                        name = gameState.players
                            .getOrNull(rightPlayerIndex)
                            ?.name
                            ?: "Jogador ${rightPlayerIndex + 1}",
                        pieces = visualPiecesForPlayer(rightPlayerIndex),
                        isCurrent = !revealOpponentHands &&
                                gameState.currentPlayerIndex == rightPlayerIndex,
                        orientation = DominoPlayerSeatOrientation.VERTICAL,
                        compact = true,
                        faceUp = revealOpponentHands,
                        isWinner = roundWinnerPlayerIndex == rightPlayerIndex,
                        onBoundsChanged = { bounds ->
                            onPlayerSeatBoundsChanged(
                                rightPlayerIndex,
                                bounds,
                            )
                        },
                    )
                }
            }
        }
    }
}