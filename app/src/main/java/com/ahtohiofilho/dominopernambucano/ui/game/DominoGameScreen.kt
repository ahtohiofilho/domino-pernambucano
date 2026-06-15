package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    var localMoveSourcePositionInWindow by remember {
        mutableStateOf<Offset?>(null)
    }

    val presentingMovePhase = uiState.phase as? DominoMatchPhase.PresentingMove
    val presentingPassPhase = uiState.phase as? DominoMatchPhase.PresentingPass
    val isRoundIntroPhase = uiState.phase == DominoMatchPhase.RoundIntro
    val isRoundSummaryPhase = uiState.phase == DominoMatchPhase.RoundSummary
    val isMatchFinishedPhase = uiState.phase == DominoMatchPhase.MatchFinished

    LaunchedEffect(uiState.phase) {
        if (uiState.phase !is DominoMatchPhase.PresentingMove) {
            localMoveSourcePositionInWindow = null
        }
    }

    val roundIntroTeams = remember(
        gameState.players,
        gameState.teamScores,
        uiState.localPlayerIndex,
    ) {
        buildRoundIntroTeamPresentations(
            players = gameState.players,
            teamScores = gameState.teamScores,
            localPlayerIndex = uiState.localPlayerIndex,
        )
    }

    val hiddenLocalAnimatedPiece = if (
        presentingMovePhase != null &&
        presentingMovePhase.playerIndex == uiState.localPlayerIndex
    ) {
        presentingMovePhase.move.piece
    } else {
        null
    }

    val dropTargetHitRadiusPx = with(density) {
        DominoGameVisualTokens.DropTargetHitRadius.toPx()
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
        if (playerIndex == uiState.localPlayerIndex) {
            localMoveSourcePositionInWindow?.let { sourcePosition ->
                return sourcePosition
            }

            return localHandBoundsInWindow?.centerOffset()
        }

        return playerSeatBoundsInWindow[playerIndex]?.centerOffset()
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
                    horizontal = 10.dp,
                    vertical = 8.dp,
                ),
        ) {
            DominoMatchHeader(
                uiState = uiState,
            )

            DominoGameTableStage(
                gameState = gameState,
                localPlayableMoves = uiState.localPlayableMoves,
                showDropTargets = draggedPieceState != null &&
                        !isRoundIntroPhase &&
                        !isRoundSummaryPhase &&
                        !isMatchFinishedPhase,
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
                    .padding(vertical = 4.dp),
            )

            DominoLocalHand(
                uiState = uiState,
                hiddenPiece = hiddenLocalAnimatedPiece,
                onLocalMoveSelected = { move ->
                    localMoveSourcePositionInWindow =
                        localHandBoundsInWindow?.centerOffset()

                    onLocalMoveSelected(move)
                },
                onLocalHandBoundsChanged = { bounds ->
                    localHandBoundsInWindow = bounds
                },
                onPieceDragStart = { piece, positionInWindow ->
                    if (uiState.phase != DominoMatchPhase.WaitingForLocalMove) {
                        clearDragState()
                        return@DominoLocalHand
                    }

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

                    val shouldCancelMove = currentDragState.isOverLocalHand ||
                            isPositionInsideRect(
                                positionInWindow = currentDragState.positionInWindow,
                                rect = localHandBoundsInWindow,
                            )

                    val selectedSide = if (shouldCancelMove) {
                        null
                    } else {
                        currentDragState.highlightedSide
                            ?: findNearestDropSide(
                                positionInWindow = currentDragState.positionInWindow,
                                playableMoves = currentDragState.playableMoves,
                                dropTargets = dropTargetsInWindow,
                            )
                    }

                    val selectedMove = findPlayableMoveForDropSide(
                        playableMoves = currentDragState.playableMoves,
                        dropSide = selectedSide,
                    )

                    if (selectedMove != null) {
                        localMoveSourcePositionInWindow =
                            currentDragState.positionInWindow
                    }

                    clearDragState()

                    if (selectedMove != null) {
                        onLocalMoveSelected(selectedMove)
                    }
                },
                onPieceDragCancel = {
                    clearDragState()
                },
            )
        }

        DraggedPieceOverlay(
            draggedPieceState = if (
                presentingMovePhase == null &&
                presentingPassPhase == null &&
                !isRoundIntroPhase &&
                !isRoundSummaryPhase &&
                !isMatchFinishedPhase
            ) {
                draggedPieceState
            } else {
                null
            },
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

        if (presentingPassPhase != null) {
            PassTurnKnockAnimationOverlay(
                playerIndex = presentingPassPhase.playerIndex,
                onAnimationFinished = onPresentationFinished,
            )
        }

        if (isRoundIntroPhase) {
            RoundIntroPresentationOverlay(
                roundNumber = uiState.roundNumber,
                teams = roundIntroTeams,
                onAnimationFinished = onRoundIntroFinished,
            )
        }

        if (isRoundSummaryPhase) {
            RoundSummaryRevealOverlay(
                gameState = gameState,
                localPlayerIndex = uiState.localPlayerIndex,
                onStartNextRound = onStartNextRound,
            )
        }

        if (isMatchFinishedPhase) {
            MatchFinishedActionOverlay(
                uiState = uiState,
                onStartNewMatch = onStartNewMatch,
                onBackToMenuClick = onBackToMenuClick,
            )
        }
    }
}

private fun Rect.centerOffset(): Offset {
    return Offset(
        x = left + width / 2f,
        y = top + height / 2f,
    )
}