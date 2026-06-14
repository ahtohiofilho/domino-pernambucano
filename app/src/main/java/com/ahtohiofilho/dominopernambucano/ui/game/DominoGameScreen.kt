package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoGameScreen(
    uiState: DominoGameUiState,
    onBackToMenuClick: () -> Unit,
    onRoundIntroFinished: () -> Unit,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onPresentationFinished: () -> Unit,
    onStartNextRound: () -> Unit,
    onStartNewMatch: () -> Unit,
) {
    val gameState = uiState.gameState
    val density = LocalDensity.current

    var draggedPieceState by remember {
        mutableStateOf<LocalDraggedPieceState?>(null)
    }

    var dropTargetsInWindow by remember {
        mutableStateOf<List<DominoDropTargetInWindow>>(emptyList())
    }

    var animatedMoveTargetInWindow by remember {
        mutableStateOf<DominoMoveTargetInWindow?>(null)
    }

    var localHandBoundsInWindow by remember {
        mutableStateOf<Rect?>(null)
    }

    var playerSeatBoundsInWindow by remember {
        mutableStateOf<Map<Int, Rect>>(emptyMap())
    }

    val presentingMovePhase = uiState.phase as? DominoMatchPhase.PresentingMove

    val hiddenLocalAnimatedPiece = if (
        presentingMovePhase != null &&
        presentingMovePhase.playerIndex == uiState.localPlayerIndex
    ) {
        presentingMovePhase.move.piece
    } else {
        null
    }

    val dropTargetHitRadiusPx = with(density) {
        86.dp.toPx()
    }

    fun getPlayableMovesForPiece(
        piece: DominoPiece,
    ): List<PlayableMove> {
        return uiState.localPlayableMoves.filter { move ->
            move.piece == piece
        }
    }

    fun buildDraggedPieceState(
        piece: DominoPiece,
        positionInWindow: Offset,
        playableMoves: List<PlayableMove>,
    ): LocalDraggedPieceState {
        val highlightedSide = findHighlightedDropSideOrNull(
            positionInWindow = positionInWindow,
            playableMoves = playableMoves,
            dropTargets = dropTargetsInWindow,
            localHandBoundsInWindow = localHandBoundsInWindow,
            maxDistancePx = dropTargetHitRadiusPx,
        )

        return LocalDraggedPieceState(
            piece = piece,
            positionInWindow = positionInWindow,
            playableMoves = playableMoves,
            highlightedSide = highlightedSide,
            isOverLocalHand = isPositionInsideRect(
                positionInWindow = positionInWindow,
                rect = localHandBoundsInWindow,
            ),
        )
    }

    fun clearDragState() {
        draggedPieceState = null
    }

    fun updatePlayerSeatBounds(
        playerIndex: Int,
        bounds: Rect?,
    ) {
        playerSeatBoundsInWindow = if (bounds == null) {
            playerSeatBoundsInWindow - playerIndex
        } else {
            playerSeatBoundsInWindow + (playerIndex to bounds)
        }
    }

    fun getSourcePositionForPlayer(
        playerIndex: Int,
    ): Offset? {
        val bounds = if (playerIndex == uiState.localPlayerIndex) {
            localHandBoundsInWindow
        } else {
            playerSeatBoundsInWindow[playerIndex]
        }

        return bounds?.let { rect ->
            Offset(
                x = rect.left + rect.width / 2f,
                y = rect.top + rect.height / 2f,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DominoSemanticColors.appBackground,
                        DominoColorTokens.PernambucoBlueDark,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 14.dp,
                    vertical = 12.dp,
                ),
        ) {
            DominoMatchHeader(
                uiState = uiState,
            )

            DominoGameTableStage(
                gameState = gameState,
                localPlayableMoves = uiState.localPlayableMoves,
                showDropTargets = draggedPieceState != null,
                highlightedDropSide = draggedPieceState?.highlightedSide,
                animatedPlayableMove = presentingMovePhase?.move,
                onDropTargetsChanged = { targets ->
                    dropTargetsInWindow = targets
                },
                onAnimatedMoveTargetChanged = { target ->
                    animatedMoveTargetInWindow = target
                },
                onPlayerSeatBoundsChanged = { playerIndex, bounds ->
                    updatePlayerSeatBounds(
                        playerIndex = playerIndex,
                        bounds = bounds,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            DominoLocalHand(
                uiState = uiState,
                hiddenPiece = hiddenLocalAnimatedPiece,
                onLocalMoveSelected = onLocalMoveSelected,
                onLocalHandBoundsChanged = { bounds ->
                    localHandBoundsInWindow = bounds
                },
                onPieceDragStart = { piece, positionInWindow ->
                    val playableMoves = getPlayableMovesForPiece(piece)

                    if (playableMoves.isEmpty()) {
                        clearDragState()
                        return@DominoLocalHand
                    }

                    draggedPieceState = buildDraggedPieceState(
                        piece = piece,
                        positionInWindow = positionInWindow,
                        playableMoves = playableMoves,
                    )
                },
                onPieceDrag = { dragAmount ->
                    val currentDragState = draggedPieceState
                        ?: return@DominoLocalHand

                    val updatedPosition =
                        currentDragState.positionInWindow + dragAmount

                    draggedPieceState = buildDraggedPieceState(
                        piece = currentDragState.piece,
                        positionInWindow = updatedPosition,
                        playableMoves = currentDragState.playableMoves,
                    )
                },
                onPieceDragEnd = {
                    val currentDragState = draggedPieceState
                        ?: return@DominoLocalHand

                    val selectedMove = findPlayableMoveForDropSide(
                        playableMoves = currentDragState.playableMoves,
                        dropSide = currentDragState.highlightedSide,
                    )

                    clearDragState()

                    if (selectedMove != null) {
                        onLocalMoveSelected(selectedMove)
                    }
                },
                onPieceDragCancel = {
                    clearDragState()
                },
            )

            Spacer(
                modifier = Modifier.height(10.dp),
            )

            DominoGameActionPanel(
                uiState = uiState,
                onBackToMenuClick = onBackToMenuClick,
                onRoundIntroFinished = onRoundIntroFinished,
                onLocalMoveSelected = onLocalMoveSelected,
                onPresentationFinished = onPresentationFinished,
                onStartNextRound = onStartNextRound,
                onStartNewMatch = onStartNewMatch,
            )
        }

        DraggedPieceOverlay(
            draggedPieceState = draggedPieceState,
        )

        if (presentingMovePhase != null) {
            PlayedMoveAnimationOverlay(
                move = presentingMovePhase.move,
                sourcePositionInWindow = getSourcePositionForPlayer(
                    playerIndex = presentingMovePhase.playerIndex,
                ),
                target = animatedMoveTargetInWindow,
                onAnimationFinished = onPresentationFinished,
            )
        }
    }
}