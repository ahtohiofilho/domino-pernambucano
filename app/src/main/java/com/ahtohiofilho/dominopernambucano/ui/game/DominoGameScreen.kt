package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlinx.coroutines.delay

@Composable
fun DominoGameScreen(
    uiState: DominoGameUiState,
    onBackToMenuClick: () -> Unit,
    onOnlineTrace: (
        OnlineTraceType,
        String?,
        Long?,
        Map<String, String>,
    ) -> Unit = { _, _, _, _ -> },
    onRoundIntroFinished: () -> Unit,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onTurnClockTick: (Long) -> Unit,
    onBotDecisionReady: () -> Unit,
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

    val shouldRevealRoundContext = isRoundSummaryPhase || isMatchFinishedPhase

    fun traceUi(
        type: OnlineTraceType,
        attributes: Map<String, String> = emptyMap(),
    ) {
        onOnlineTrace(
            type,
            uiState.onlinePresentationId,
            uiState.onlineSnapshotRevision,
            attributes,
        )
    }

    val localVisualHandPieces = getVisualHandPieces(
        playerIndex = uiState.localPlayerIndex,
        pieces = gameState.players
            .getOrNull(uiState.localPlayerIndex)
            ?.hand
            .orEmpty(),
        presentingMovePhase = presentingMovePhase,
    )

    LaunchedEffect(uiState.phase) {
        if (uiState.phase !is DominoMatchPhase.PresentingMove) {
            localMoveSourcePositionInWindow = null
        }
    }

    LaunchedEffect(
        uiState.isTurnClockEnabled,
        uiState.phase,
        gameState.currentPlayerIndex,
    ) {
        if (!uiState.isTurnClockEnabled) {
            return@LaunchedEffect
        }

        if (uiState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return@LaunchedEffect
        }

        while (true) {
            delay(DominoMatchTiming.ClockTickMillis)

            onTurnClockTick(
                DominoMatchTiming.ClockTickMillis,
            )
        }
    }

    LaunchedEffect(
        uiState.phase,
        gameState.currentPlayerIndex,
        uiState.localPlayerIndex,
    ) {
        val isBotDecisionTurn =
            uiState.phase == DominoMatchPhase.WaitingForLocalMove &&
                    gameState.currentPlayerIndex != uiState.localPlayerIndex

        if (isBotDecisionTurn) {
            delay(DominoMatchTiming.BotDecisionDelayMillis)

            onBotDecisionReady()
        }
    }

    LaunchedEffect(
        uiState.onlinePresentationId,
        presentingMovePhase,
        animatedMoveTargetInWindow,
        localMoveSourcePositionInWindow,
        localHandBoundsInWindow,
        playerSeatBoundsInWindow,
    ) {
        val movePhase = presentingMovePhase ?: return@LaunchedEffect
        val source = if (movePhase.playerIndex == uiState.localPlayerIndex) {
            localMoveSourcePositionInWindow
                ?: localHandBoundsInWindow?.centerOffset()
        } else {
            playerSeatBoundsInWindow[movePhase.playerIndex]?.centerOffset()
        }
        val target = animatedMoveTargetInWindow
        val attributes = movePhase.move.toUiMoveTraceAttributes() + mapOf(
            "animationKind" to "move",
            "sourceAvailable" to (source != null).toString(),
            "targetAvailable" to (target != null).toString(),
        )

        if (source == null || target == null) {
            traceUi(
                OnlineTraceType.ANIMATION_TARGET_PENDING,
                attributes,
            )
        } else {
            traceUi(
                OnlineTraceType.ANIMATION_TARGET_READY,
                attributes,
            )
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

    fun getVisualPiecesForPlayer(
        playerIndex: Int,
    ): List<DominoPiece> {
        return getVisualHandPieces(
            playerIndex = playerIndex,
            pieces = gameState.players
                .getOrNull(playerIndex)
                ?.hand
                .orEmpty(),
            presentingMovePhase = presentingMovePhase,
        )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DominoGameVisualTokens.HeaderSlotHeight),
                contentAlignment = Alignment.Center,
            ) {
                DominoMatchHeader(
                    uiState = uiState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            DominoGameTableStage(
                gameState = gameState,
                localPlayerIndex = uiState.localPlayerIndex,
                localPlayableMoves = uiState.localPlayableMoves,
                showDropTargets = draggedPieceState != null &&
                        !isRoundIntroPhase &&
                        !isRoundSummaryPhase &&
                        !isMatchFinishedPhase,
                highlightedDropSide = draggedPieceState?.highlightedSide,
                animatedPlayableMove = presentingMovePhase?.move,
                revealOpponentHands = shouldRevealRoundContext,
                roundWinnerPlayerIndex = if (shouldRevealRoundContext) {
                    gameState.roundWinnerPlayerIndex
                } else {
                    null
                },
                visualPiecesForPlayer = { playerIndex ->
                    getVisualPiecesForPlayer(
                        playerIndex = playerIndex,
                    )
                },
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
                    .padding(
                        vertical = DominoGameVisualTokens.TableStageVerticalPadding,
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DominoGameVisualTokens.LocalHandSlotHeight),
                contentAlignment = Alignment.BottomCenter,
            ) {
                DominoLocalHand(
                    uiState = uiState,
                    pieces = localVisualHandPieces,
                    isWinner = shouldRevealRoundContext &&
                            gameState.roundWinnerPlayerIndex == uiState.localPlayerIndex,
                    onLocalMoveSelected = { move ->
                        traceUi(
                            OnlineTraceType.UI_MOVE_INTENT_RECEIVED,
                            move.toUiMoveTraceAttributes() + mapOf(
                                "inputMethod" to "tap",
                                "phase" to uiState.phase.uiTracePhaseName(),
                                "currentPlayerIndex" to
                                        gameState.currentPlayerIndex.toString(),
                            ),
                        )

                        localMoveSourcePositionInWindow =
                            localHandBoundsInWindow?.centerOffset()

                        onLocalMoveSelected(move)
                    },
                    onLocalHandBoundsChanged = { bounds ->
                        localHandBoundsInWindow = bounds
                    },
                    onPieceDragStart = { piece, positionInWindow ->
                        if (uiState.phase != DominoMatchPhase.WaitingForLocalMove) {
                            traceUi(
                                OnlineTraceType.UI_MOVE_INTENT_REJECTED,
                                mapOf(
                                    "inputMethod" to "drag",
                                    "reason" to "wrong_phase",
                                    "piece" to "${piece.left}-${piece.right}",
                                    "phase" to uiState.phase.uiTracePhaseName(),
                                ),
                            )
                            clearDragState()
                            return@DominoLocalHand
                        }

                        val playableMoves = getPlayableMovesForPiece(piece)

                        if (playableMoves.isEmpty()) {
                            traceUi(
                                OnlineTraceType.UI_MOVE_INTENT_REJECTED,
                                mapOf(
                                    "inputMethod" to "drag",
                                    "reason" to "not_playable",
                                    "piece" to "${piece.left}-${piece.right}",
                                ),
                            )
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
                            traceUi(
                                OnlineTraceType.UI_MOVE_INTENT_RECEIVED,
                                selectedMove.toUiMoveTraceAttributes() + mapOf(
                                    "inputMethod" to "drag",
                                    "phase" to uiState.phase.uiTracePhaseName(),
                                    "currentPlayerIndex" to
                                            gameState.currentPlayerIndex.toString(),
                                ),
                            )
                            localMoveSourcePositionInWindow =
                                currentDragState.positionInWindow
                        } else {
                            traceUi(
                                OnlineTraceType.UI_MOVE_INTENT_REJECTED,
                                mapOf(
                                    "inputMethod" to "drag",
                                    "reason" to if (shouldCancelMove) {
                                        "drag_cancelled_over_local_hand"
                                    } else {
                                        "drag_no_target"
                                    },
                                    "piece" to
                                            "${currentDragState.piece.left}-${currentDragState.piece.right}",
                                    "highlightedSide" to
                                            (currentDragState.highlightedSide?.name
                                                ?: "null"),
                                ),
                            )
                        }

                        clearDragState()

                        if (selectedMove != null) {
                            onLocalMoveSelected(selectedMove)
                        }
                    },
                    onPieceDragCancel = {
                        draggedPieceState?.let { currentDragState ->
                            traceUi(
                                OnlineTraceType.UI_MOVE_INTENT_REJECTED,
                                mapOf(
                                    "inputMethod" to "drag",
                                    "reason" to "drag_cancelled_by_system",
                                    "piece" to
                                            "${currentDragState.piece.left}-${currentDragState.piece.right}",
                                ),
                            )
                        }
                        clearDragState()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        DominoTurnCountdownHud(
            uiState = uiState,
            modifier = Modifier.fillMaxSize(),
        )

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
                presentationId = uiState.onlinePresentationId,
                onAnimationTrace = { type, attributes ->
                    traceUi(
                        type = type,
                        attributes = attributes,
                    )
                },
                onAnimationFinished = onPresentationFinished,
            )
        }

        if (presentingPassPhase != null) {
            PassTurnKnockAnimationOverlay(
                playerIndex = presentingPassPhase.playerIndex,
                presentationId = uiState.onlinePresentationId,
                onAnimationTrace = { type, attributes ->
                    traceUi(
                        type = type,
                        attributes = attributes,
                    )
                },
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

private fun PlayableMove.toUiMoveTraceAttributes(): Map<String, String> {
    return mapOf(
        "piece" to "${piece.left}-${piece.right}",
        "boardSide" to side.name,
        "flipped" to flipped.toString(),
    )
}

private fun DominoMatchPhase.uiTracePhaseName(): String {
    return when (this) {
        DominoMatchPhase.RoundIntro -> "ROUND_INTRO"
        DominoMatchPhase.WaitingForLocalMove -> "WAITING_FOR_LOCAL_MOVE"
        is DominoMatchPhase.PresentingMove -> "PRESENTING_MOVE"
        is DominoMatchPhase.PresentingPass -> "PRESENTING_PASS"
        DominoMatchPhase.RoundSummary -> "ROUND_SUMMARY"
        DominoMatchPhase.MatchFinished -> "MATCH_FINISHED"
    }
}

private fun getVisualHandPieces(
    playerIndex: Int,
    pieces: List<DominoPiece>,
    presentingMovePhase: DominoMatchPhase.PresentingMove?,
): List<DominoPiece> {
    if (presentingMovePhase?.playerIndex != playerIndex) {
        return pieces
    }

    return pieces.withoutFirst(
        pieceToRemove = presentingMovePhase.move.piece,
    )
}

private fun List<DominoPiece>.withoutFirst(
    pieceToRemove: DominoPiece,
): List<DominoPiece> {
    var wasRemoved = false

    return filter { piece ->
        if (!wasRemoved && piece == pieceToRemove) {
            wasRemoved = true
            false
        } else {
            true
        }
    }
}

private fun Rect.centerOffset(): Offset {
    return Offset(
        x = left + width / 2f,
        y = top + height / 2f,
    )
}