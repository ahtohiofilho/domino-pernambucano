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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.online.createDefaultOnlineTableName

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
    onTableBoundsChanged: (Rect?) -> Unit = {},
    onAnimatedMoveTargetChanged: (DominoMoveTargetInWindow) -> Unit,
    onPlayerSeatBoundsChanged: (Int, Rect?) -> Unit,
    magnificationEnabled: Boolean = false,
    onMagnificationChanged: (Boolean) -> Unit = {},
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
                    name = resolveTablePlayerName(
                        gameState = gameState,
                        playerIndex = topPlayerIndex,
                    ),
                    participantType = gameState.players
                        .getOrNull(topPlayerIndex)
                        ?.participantType
                        ?: DominoParticipantType.HUMAN,
                    pieces = visualPiecesForPlayer(topPlayerIndex),
                    isCurrent = !revealOpponentHands &&
                            gameState.currentPlayerIndex == topPlayerIndex,
                    orientation = DominoPlayerSeatOrientation.HORIZONTAL,
                    identityPlacement = DominoPlayerIdentityPlacement.AFTER_HAND,
                    modifier = Modifier.fillMaxSize(),
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
                        name = resolveTablePlayerName(
                            gameState = gameState,
                            playerIndex = leftPlayerIndex,
                        ),
                        participantType = gameState.players
                            .getOrNull(leftPlayerIndex)
                            ?.participantType
                            ?: DominoParticipantType.HUMAN,
                        pieces = visualPiecesForPlayer(leftPlayerIndex),
                        isCurrent = !revealOpponentHands &&
                                gameState.currentPlayerIndex == leftPlayerIndex,
                        orientation = DominoPlayerSeatOrientation.VERTICAL,
                        identityPlacement = DominoPlayerIdentityPlacement.BEFORE_HAND,
                        modifier = Modifier.fillMaxSize(),
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
                    magnificationEnabled = magnificationEnabled,
                    onMagnificationChanged = onMagnificationChanged,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            horizontal = DominoGameVisualTokens.TableStageInnerHorizontalPadding,
                        )
                        .onGloballyPositioned { coordinates ->
                            onTableBoundsChanged(
                                coordinates.boundsInWindow(),
                            )
                        },
                )

                Box(
                    modifier = Modifier
                        .width(DominoGameVisualTokens.OpponentSideSeatSlotWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    DominoPlayerSeat(
                        name = resolveTablePlayerName(
                            gameState = gameState,
                            playerIndex = rightPlayerIndex,
                        ),
                        participantType = gameState.players
                            .getOrNull(rightPlayerIndex)
                            ?.participantType
                            ?: DominoParticipantType.HUMAN,
                        pieces = visualPiecesForPlayer(rightPlayerIndex),
                        isCurrent = !revealOpponentHands &&
                                gameState.currentPlayerIndex == rightPlayerIndex,
                        orientation = DominoPlayerSeatOrientation.VERTICAL,
                        identityPlacement = DominoPlayerIdentityPlacement.AFTER_HAND,
                        modifier = Modifier.fillMaxSize(),
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

@Composable
private fun resolveTablePlayerName(
    gameState: DominoGameState,
    playerIndex: Int,
): String {
    val displayName = gameState.players
        .getOrNull(playerIndex)
        ?.name
        ?: stringResource(
            R.string.game_player_fallback,
            playerIndex + 1,
        )

    return createDefaultOnlineTableName(
        displayName = displayName,
    )
}
