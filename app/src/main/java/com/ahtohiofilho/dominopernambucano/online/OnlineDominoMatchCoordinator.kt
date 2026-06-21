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
import kotlinx.coroutines.CoroutineDispatcher
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
    private val coroutineDispatcher: CoroutineDispatcher =
        Dispatchers.Main.immediate,
) : DominoMatchCoordinator {
    private val coordinatorScope = CoroutineScope(
        SupervisorJob() + coroutineDispatcher,
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
    private var stableRevision = initialSnapshot.revision

    /*
     * Cada revisão remota precisa atravessar a camada de apresentação na mesma
     * ordem em que foi recebida. Manter apenas um estado pendente fazia uma
     * revisão nova substituir a anterior durante uma animação, pulando jogadas
     * intermediárias na mesa.
     */
    private val pendingRemoteRuntimeStates =
        ArrayDeque<QueuedOnlineRuntimeState>()

    private var activePresentationRuntimeState: QueuedOnlineRuntimeState? = null

    private var lastReceivedRevision = initialSnapshot.revision

    private var automaticPlayerIndexes: Set<Int> =
        initialSnapshot.automaticPlayerIndexes.toSet()

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

                if (snapshot.revision <= lastReceivedRevision) {
                    return@collect
                }

                lastReceivedRevision = snapshot.revision

                handleRemoteSnapshot(
                    remoteRuntimeState = snapshot.toRuntimeState(
                        localPlayerIndex = localPlayerIndex,
                    ),
                    revision = snapshot.revision,
                    automaticPlayerIndexes = snapshot.automaticPlayerIndexes.toSet(),
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

        if (shouldRequestAutomaticLocalTurn(runtimeState)) {
            requestSnapshot()
            return
        }

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
        revision: Long,
        automaticPlayerIndexes: Set<Int>,
    ) {
        pendingRemoteRuntimeStates.addLast(
            QueuedOnlineRuntimeState(
                runtimeState = remoteRuntimeState,
                revision = revision,
                automaticPlayerIndexes = automaticPlayerIndexes,
            ),
        )

        advancePresentationQueue()
    }

    private fun handleRoundIntroFinished() {
        if (completeActivePresentation()) {
            advancePresentationQueue()
            return
        }

        mutableState.value = stableRuntimeState

        advancePresentationQueue()
    }

    private fun handlePresentationFinished() {
        if (completeActivePresentation()) {
            advancePresentationQueue()
            return
        }

        /*
         * Uma passagem pode ser apresentada a partir de um snapshot já estável.
         * Se a resolução autoritativa chegou durante essa animação, consuma-a
         * antes de enviar uma nova ação ou pedir outro snapshot.
         */
        if (pendingRemoteRuntimeStates.isNotEmpty()) {
            advancePresentationQueue(
                allowCurrentPresentationCompletion = true,
            )
            return
        }

        when (val phase = currentState.phase) {
            is DominoMatchPhase.PresentingMove -> {
                requestSnapshot()
            }

            is DominoMatchPhase.PresentingPass -> {
                if (phase.playerIndex == localPlayerIndex) {
                    if (
                        shouldRequestAutomaticLocalPassResolution(
                            runtimeState = currentState,
                        )
                    ) {
                        requestSnapshot()
                    } else {
                        submitPassTurn()
                    }
                } else {
                    requestSnapshot()
                }
            }

            else -> Unit
        }
    }

    private fun advancePresentationQueue(
        allowCurrentPresentationCompletion: Boolean = false,
    ) {
        if (activePresentationRuntimeState != null) {
            return
        }

        if (
            !allowCurrentPresentationCompletion &&
            isPresentationInProgress(
                phase = currentState.phase,
            )
        ) {
            return
        }

        while (pendingRemoteRuntimeStates.isNotEmpty()) {
            val queuedRuntimeState = pendingRemoteRuntimeStates.first()

            val presentation = buildPresentationBridge(
                previousRuntimeState = stableRuntimeState,
                remoteRuntimeState = queuedRuntimeState.runtimeState,
            )

            if (presentation == null) {
                pendingRemoteRuntimeStates.removeFirst()

                promoteRuntimeState(
                    queuedRuntimeState = queuedRuntimeState,
                )

                continue
            }

            activePresentationRuntimeState = queuedRuntimeState

            mutableState.value = presentation.presentationRuntimeState

            return
        }

        requestSnapshotIfLocalAutomaticTurn(
            runtimeState = stableRuntimeState,
        )
    }

    private fun completeActivePresentation(): Boolean {
        val activeRuntimeState = activePresentationRuntimeState
            ?: return false

        val nextQueuedRuntimeState = pendingRemoteRuntimeStates.firstOrNull()

        if (nextQueuedRuntimeState != activeRuntimeState) {
            activePresentationRuntimeState = null
            return false
        }

        pendingRemoteRuntimeStates.removeFirst()
        activePresentationRuntimeState = null

        promoteRuntimeState(
            queuedRuntimeState = activeRuntimeState,
        )

        return true
    }

    private fun promoteRuntimeState(
        queuedRuntimeState: QueuedOnlineRuntimeState,
    ) {
        stableRuntimeState = queuedRuntimeState.runtimeState
        stableRevision = queuedRuntimeState.revision
        automaticPlayerIndexes = queuedRuntimeState.automaticPlayerIndexes
        mutableState.value = queuedRuntimeState.runtimeState
    }

    private fun isPresentationInProgress(
        phase: DominoMatchPhase,
    ): Boolean {
        return phase == DominoMatchPhase.RoundIntro ||
                phase is DominoMatchPhase.PresentingMove ||
                phase is DominoMatchPhase.PresentingPass
    }

    private fun submitMove(
        command: DominoMatchCommand.LocalMoveSelected,
    ) {
        val runtimeState = stableRuntimeState

        if (isLocalPlayerAutomatic()) {
            requestSnapshotIfLocalAutomaticTurn(
                runtimeState = runtimeState,
            )
            return
        }

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
            revision = stableRevision,
            move = command.move,
        )

        submitAction(action)
    }

    private fun submitPassTurn() {
        val runtimeState = stableRuntimeState

        if (isLocalPlayerAutomatic()) {
            requestSnapshotIfLocalAutomaticTurn(
                runtimeState = runtimeState,
            )
            return
        }

        val action = createOnlinePassTurnAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = stableRevision,
        )

        submitAction(action)
    }

    private fun submitStartNextRound() {
        val action = createOnlineStartNextRoundAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = stableRevision,
        )

        submitAction(action)
    }

    private fun submitStartNewMatch() {
        val action = createOnlineStartNewMatchAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = stableRevision,
        )

        submitAction(action)
    }

    private fun isLocalPlayerAutomatic(): Boolean {
        return localPlayerIndex in automaticPlayerIndexes
    }

    private fun shouldRequestAutomaticLocalTurn(
        runtimeState: DominoMatchRuntimeState,
    ): Boolean {
        if (!isLocalPlayerAutomatic()) {
            return false
        }

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return false
        }

        return runtimeState.gameState.currentPlayerIndex == localPlayerIndex
    }

    private fun shouldRequestAutomaticLocalPassResolution(
        runtimeState: DominoMatchRuntimeState,
    ): Boolean {
        if (!isLocalPlayerAutomatic()) {
            return false
        }

        val phase = runtimeState.phase

        if (phase !is DominoMatchPhase.PresentingPass) {
            return false
        }

        return phase.playerIndex == localPlayerIndex &&
                runtimeState.gameState.currentPlayerIndex == localPlayerIndex
    }

    private fun requestSnapshotIfLocalAutomaticTurn(
        runtimeState: DominoMatchRuntimeState,
    ) {
        if (!shouldRequestAutomaticLocalTurn(runtimeState)) {
            return
        }

        requestSnapshot()
    }

    private fun requestSnapshot() {
        val action = createOnlineSnapshotRequestAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = stableRevision,
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

private data class QueuedOnlineRuntimeState(
    val runtimeState: DominoMatchRuntimeState,
    val revision: Long,
    val automaticPlayerIndexes: Set<Int>,
)

private data class OnlinePresentationBridge(
    val presentationRuntimeState: DominoMatchRuntimeState,
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
