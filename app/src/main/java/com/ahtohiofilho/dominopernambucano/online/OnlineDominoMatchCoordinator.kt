package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.decrementPlayerClockMillis
import com.ahtohiofilho.dominopernambucano.match.isPlayerClockExpired
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnlineDominoMatchCoordinator(
    private val repository: OnlineRoomRepository,
    private val roomId: String,
    private val matchId: String,
    private val localPlayerId: String,
    private val localPlayerIndex: Int,
    initialSnapshot: OnlineMatchSnapshotDto,
) : DominoMatchCoordinator {
    private val coordinatorScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )

    private val initialRuntimeState = initialSnapshot.toRuntimeState(
        localPlayerIndex = localPlayerIndex,
    )

    private val mutableState = MutableStateFlow(
        initialRuntimeState.copy(
            phase = DominoMatchPhase.RoundIntro,
        )
    )

    private var stableRuntimeState = initialRuntimeState

    private var pendingRuntimeStateAfterPresentation: DominoMatchRuntimeState? =
        initialRuntimeState

    private var latestRevision = initialSnapshot.revision
    private var lastConsumedRevision = initialSnapshot.revision

    override val state: StateFlow<DominoMatchRuntimeState> =
        mutableState.asStateFlow()

    override val currentState: DominoMatchRuntimeState
        get() = mutableState.value

    init {
        coordinatorScope.launch {
            repository.matchSnapshot.collect { snapshot ->
                if (snapshot == null) {
                    return@collect
                }

                if (snapshot.roomId != roomId || snapshot.matchId != matchId) {
                    return@collect
                }

                if (snapshot.revision <= lastConsumedRevision) {
                    return@collect
                }

                lastConsumedRevision = snapshot.revision
                latestRevision = snapshot.revision

                handleRemoteSnapshot(
                    remoteRuntimeState = snapshot.toRuntimeState(
                        localPlayerIndex = localPlayerIndex,
                    ),
                )
            }
        }
    }

    override fun dispatch(
        command: DominoMatchCommand,
    ) {
        when (command) {
            DominoMatchCommand.RoundIntroFinished -> {
                handleRoundIntroFinished()
            }

            is DominoMatchCommand.LocalMoveSelected -> {
                submitMove(command)
            }

            is DominoMatchCommand.TurnClockTick -> {
                handleTurnClockTick(command)
            }

            DominoMatchCommand.BotDecisionReady -> {
                requestSnapshot()
            }

            DominoMatchCommand.PresentationFinished -> {
                handlePresentationFinished()
            }

            DominoMatchCommand.StartNextRound -> {
                submitStartNextRound()
            }

            DominoMatchCommand.StartNewMatch -> {
                submitStartNewMatch()
            }
        }
    }

    fun dispose() {
        coordinatorScope.cancel()
    }

    private fun handleTurnClockTick(
        command: DominoMatchCommand.TurnClockTick,
    ) {
        if (command.elapsedMillis <= 0L) {
            return
        }

        val runtimeState = mutableState.value

        if (!runtimeState.clockPolicy.enabled) {
            return
        }

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return
        }

        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        if (
            isPlayerClockExpired(
                clocks = runtimeState.playerClockMillis,
                playerIndex = currentPlayerIndex,
            )
        ) {
            return
        }

        val updatedClocks = decrementPlayerClockMillis(
            clocks = runtimeState.playerClockMillis,
            playerIndex = currentPlayerIndex,
            elapsedMillis = command.elapsedMillis,
        )

        mutableState.value = runtimeState.copy(
            playerClockMillis = updatedClocks,
        )

        if (
            isPlayerClockExpired(
                clocks = updatedClocks,
                playerIndex = currentPlayerIndex,
            )
        ) {
            requestSnapshot()
        }
    }

    private fun handleRemoteSnapshot(
        remoteRuntimeState: DominoMatchRuntimeState,
    ) {
        val presentation = buildPresentationBridge(
            previousRuntimeState = stableRuntimeState,
            remoteRuntimeState = remoteRuntimeState,
        )

        if (presentation == null) {
            promoteRuntimeState(remoteRuntimeState)
            return
        }

        pendingRuntimeStateAfterPresentation = presentation.finalRuntimeState

        mutableState.value = presentation.presentationRuntimeState
    }

    private fun handleRoundIntroFinished() {
        val pendingRuntimeState = pendingRuntimeStateAfterPresentation

        if (pendingRuntimeState != null) {
            promoteRuntimeState(pendingRuntimeState)
            return
        }

        requestSnapshot()
    }

    private fun handlePresentationFinished() {
        val phase = currentState.phase
        val pendingRuntimeState = pendingRuntimeStateAfterPresentation

        when (phase) {
            is DominoMatchPhase.PresentingMove -> {
                if (pendingRuntimeState != null) {
                    promoteRuntimeState(pendingRuntimeState)
                } else {
                    requestSnapshot()
                }
            }

            is DominoMatchPhase.PresentingPass -> {
                if (pendingRuntimeState != null) {
                    promoteRuntimeState(pendingRuntimeState)
                    return
                }

                if (phase.playerIndex == localPlayerIndex) {
                    submitPassTurn()
                } else {
                    requestSnapshot()
                }
            }

            else -> {
                if (pendingRuntimeState != null) {
                    promoteRuntimeState(pendingRuntimeState)
                }
            }
        }
    }

    private fun promoteRuntimeState(
        runtimeState: DominoMatchRuntimeState,
    ) {
        stableRuntimeState = runtimeState
        pendingRuntimeStateAfterPresentation = null
        mutableState.value = runtimeState
    }

    private fun submitMove(
        command: DominoMatchCommand.LocalMoveSelected,
    ) {
        val runtimeState = stableRuntimeState

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return
        }

        if (runtimeState.gameState.currentPlayerIndex != localPlayerIndex) {
            return
        }

        val action = createOnlinePlayMoveAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
            move = command.move,
        )

        submitAction(action)
    }

    private fun submitPassTurn() {
        val action = createOnlinePassTurnAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        submitAction(action)
    }

    private fun submitStartNextRound() {
        val action = createOnlineStartNextRoundAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        submitAction(action)
    }

    private fun submitStartNewMatch() {
        val action = createOnlineStartNewMatchAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        submitAction(action)
    }

    private fun requestSnapshot() {
        val action = createOnlineSnapshotRequestAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        submitAction(action)
    }

    private fun submitAction(
        action: OnlinePlayerActionDto,
    ) {
        coordinatorScope.launch {
            repository.submitAction(
                action = action,
            )
        }
    }

    private fun buildPresentationBridge(
        previousRuntimeState: DominoMatchRuntimeState,
        remoteRuntimeState: DominoMatchRuntimeState,
    ): OnlinePresentationBridge? {
        if (remoteRuntimeState.roundNumber > previousRuntimeState.roundNumber) {
            return OnlinePresentationBridge(
                presentationRuntimeState = remoteRuntimeState.copy(
                    phase = DominoMatchPhase.RoundIntro,
                ),
                finalRuntimeState = remoteRuntimeState,
            )
        }

        val detectedMove = detectAddedMove(
            previousGameState = previousRuntimeState.gameState,
            remoteGameState = remoteRuntimeState.gameState,
        )

        if (detectedMove != null) {
            return OnlinePresentationBridge(
                presentationRuntimeState = previousRuntimeState.copy(
                    phase = DominoMatchPhase.PresentingMove(
                        playerIndex = detectedMove.playerIndex,
                        move = detectedMove.move,
                    ),
                ),
                finalRuntimeState = remoteRuntimeState,
            )
        }

        val detectedPassPlayerIndex = detectPassPlayerIndex(
            previousRuntimeState = previousRuntimeState,
            remoteRuntimeState = remoteRuntimeState,
        )

        if (detectedPassPlayerIndex != null) {
            return OnlinePresentationBridge(
                presentationRuntimeState = previousRuntimeState.copy(
                    phase = DominoMatchPhase.PresentingPass(
                        playerIndex = detectedPassPlayerIndex,
                    ),
                ),
                finalRuntimeState = remoteRuntimeState,
            )
        }

        return null
    }

    private fun detectAddedMove(
        previousGameState: DominoGameState,
        remoteGameState: DominoGameState,
    ): DetectedOnlineMove? {
        val previousBoard = previousGameState.board
        val remoteBoard = remoteGameState.board

        if (remoteBoard.size <= previousBoard.size) {
            return null
        }

        val addedBoardPiece = findFirstAddedBoardPiece(
            previousBoard = previousBoard,
            remoteBoard = remoteBoard,
        ) ?: return null

        val playerIndex = previousGameState.currentPlayerIndex

        val handPiece = findMatchingHandPiece(
            gameState = previousGameState,
            playerIndex = playerIndex,
            boardPiece = addedBoardPiece.piece,
        ) ?: addedBoardPiece.piece

        val flipped = handPiece != addedBoardPiece.piece &&
                handPiece.flipped() == addedBoardPiece.piece

        return DetectedOnlineMove(
            playerIndex = playerIndex,
            move = PlayableMove(
                piece = handPiece,
                side = addedBoardPiece.side,
                flipped = flipped,
            ),
        )
    }

    private fun findFirstAddedBoardPiece(
        previousBoard: List<DominoPiece>,
        remoteBoard: List<DominoPiece>,
    ): AddedBoardPiece? {
        if (previousBoard.isEmpty()) {
            val piece = remoteBoard.firstOrNull() ?: return null

            return AddedBoardPiece(
                side = BoardSide.RIGHT,
                piece = piece,
            )
        }

        if (remoteBoard.startsWithPieces(previousBoard)) {
            val piece = remoteBoard.getOrNull(previousBoard.size)
                ?: return null

            return AddedBoardPiece(
                side = BoardSide.RIGHT,
                piece = piece,
            )
        }

        if (remoteBoard.endsWithPieces(previousBoard)) {
            val addedIndex = remoteBoard.size - previousBoard.size - 1
            val piece = remoteBoard.getOrNull(addedIndex)
                ?: return null

            return AddedBoardPiece(
                side = BoardSide.LEFT,
                piece = piece,
            )
        }

        val previousStartIndex = remoteBoard.indexOfSubList(previousBoard)

        if (previousStartIndex == -1) {
            return null
        }

        if (previousStartIndex > 0) {
            val piece = remoteBoard.getOrNull(previousStartIndex - 1)
                ?: return null

            return AddedBoardPiece(
                side = BoardSide.LEFT,
                piece = piece,
            )
        }

        val rightIndex = previousStartIndex + previousBoard.size
        val piece = remoteBoard.getOrNull(rightIndex)
            ?: return null

        return AddedBoardPiece(
            side = BoardSide.RIGHT,
            piece = piece,
        )
    }

    private fun findMatchingHandPiece(
        gameState: DominoGameState,
        playerIndex: Int,
        boardPiece: DominoPiece,
    ): DominoPiece? {
        return gameState.players
            .getOrNull(playerIndex)
            ?.hand
            ?.firstOrNull { handPiece ->
                handPiece == boardPiece || handPiece.flipped() == boardPiece
            }
    }

    private fun detectPassPlayerIndex(
        previousRuntimeState: DominoMatchRuntimeState,
        remoteRuntimeState: DominoMatchRuntimeState,
    ): Int? {
        val previousGameState = previousRuntimeState.gameState
        val remoteGameState = remoteRuntimeState.gameState

        if (previousGameState.board != remoteGameState.board) {
            return null
        }

        if (previousGameState.currentPlayerIndex == remoteGameState.currentPlayerIndex) {
            return null
        }

        if (previousRuntimeState.phase is DominoMatchPhase.PresentingPass) {
            return null
        }

        if (
            previousRuntimeState.phase != DominoMatchPhase.WaitingForLocalMove &&
            previousRuntimeState.phase !is DominoMatchPhase.PresentingPass
        ) {
            return null
        }

        return previousGameState.currentPlayerIndex
    }
}

private data class OnlinePresentationBridge(
    val presentationRuntimeState: DominoMatchRuntimeState,
    val finalRuntimeState: DominoMatchRuntimeState,
)

private data class DetectedOnlineMove(
    val playerIndex: Int,
    val move: PlayableMove,
)

private data class AddedBoardPiece(
    val side: BoardSide,
    val piece: DominoPiece,
)

private fun List<DominoPiece>.startsWithPieces(
    pieces: List<DominoPiece>,
): Boolean {
    if (pieces.size > size) {
        return false
    }

    return pieces.indices.all { index ->
        this[index] == pieces[index]
    }
}

private fun List<DominoPiece>.endsWithPieces(
    pieces: List<DominoPiece>,
): Boolean {
    if (pieces.size > size) {
        return false
    }

    val offset = size - pieces.size

    return pieces.indices.all { index ->
        this[offset + index] == pieces[index]
    }
}

private fun List<DominoPiece>.indexOfSubList(
    pieces: List<DominoPiece>,
): Int {
    if (pieces.isEmpty()) {
        return 0
    }

    if (pieces.size > size) {
        return -1
    }

    for (startIndex in 0..(size - pieces.size)) {
        val matches = pieces.indices.all { index ->
            this[startIndex + index] == pieces[index]
        }

        if (matches) {
            return startIndex
        }
    }

    return -1
}