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
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.createOnlineTraceStateFingerprint
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceStateSummary
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
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
    private val traceLogger: OnlineTraceLogger =
        OnlineTraceLogger(),
) : DominoMatchCoordinator, OnlineGameUiTraceReporter {
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

    /*
     * Uma interação humana só pode gerar uma ação por vez. A UI permanece
     * orientada pelo snapshot autoritativo; a trava evita que múltiplos drags
     * reutilizem a mesma revisão enquanto a confirmação ainda está em trânsito.
     */
    private var inFlightAction: OnlinePlayerActionDto? = null

    override val state: StateFlow<DominoMatchRuntimeState> =
        mutableState.asStateFlow()

    override val currentState: DominoMatchRuntimeState
        get() = mutableState.value

    override fun currentUiTraceContext(): OnlineUiTraceContext {
        val activeRuntimeState = activePresentationRuntimeState

        return OnlineUiTraceContext(
            presentationId = activeRuntimeState?.let { queuedRuntimeState ->
                createPresentationId(
                    queuedRuntimeState = queuedRuntimeState,
                )
            },
            snapshotRevision = activeRuntimeState?.revision ?: stableRevision,
        )
    }

    override fun traceUiEvent(
        type: OnlineTraceType,
        traceContext: OnlineUiTraceContext,
        attributes: Map<String, String>,
    ) {
        val activeRuntimeState = activePresentationRuntimeState
        val resolvedTraceContext = if (
            traceContext.snapshotRevision == null &&
            traceContext.presentationId == null
        ) {
            currentUiTraceContext()
        } else {
            traceContext
        }

        val traceAttributes = buildMap {
            putAll(attributes)
            resolvedTraceContext.presentationId?.let { presentationId ->
                put("presentationId", presentationId)
            }
        }

        trace(
            level = traceLevelForUiEvent(type),
            source = OnlineTraceSource.CLIENT_UI,
            type = type,
            snapshotRevision = resolvedTraceContext.snapshotRevision
                ?: activeRuntimeState?.revision
                ?: stableRevision,
            runtimeState = currentState,
            automaticIndexes = activeRuntimeState?.automaticPlayerIndexes
                ?: automaticPlayerIndexes,
            attributes = traceAttributes,
        )
    }

    init {
        coordinatorScope.launch {
            repository.matchSnapshotEvents.collect { snapshot ->
                if (snapshot.roomId != roomId || snapshot.matchId != matchId) {
                    trace(
                        level = OnlineTraceLevel.WARN,
                        type = OnlineTraceType.SNAPSHOT_IGNORED,
                        snapshotRevision = snapshot.revision,
                        attributes = mapOf(
                            "reason" to "snapshot_from_different_match",
                            "receivedRoomId" to snapshot.roomId,
                            "receivedMatchId" to snapshot.matchId,
                        ),
                    )

                    return@collect
                }

                if (snapshot.revision <= lastReceivedRevision) {
                    if (snapshot.revision < lastReceivedRevision) {
                        trace(
                            level = OnlineTraceLevel.DEBUG,
                            type = OnlineTraceType.SNAPSHOT_IGNORED,
                            snapshotRevision = snapshot.revision,
                            runtimeState = stableRuntimeState,
                            attributes = mapOf(
                                "reason" to "stale_revision",
                                "lastReceivedRevision" to
                                        lastReceivedRevision.toString(),
                            ),
                        )
                    }

                    return@collect
                }

                val remoteRuntimeState = snapshot.toRuntimeState(
                    localPlayerIndex = localPlayerIndex,
                )

                trace(
                    level = OnlineTraceLevel.INFO,
                    type = OnlineTraceType.SNAPSHOT_RECEIVED,
                    snapshotRevision = snapshot.revision,
                    runtimeState = remoteRuntimeState,
                    automaticIndexes = snapshot.automaticPlayerIndexes.toSet(),
                    attributes = mapOf(
                        "previousReceivedRevision" to
                                lastReceivedRevision.toString(),
                    ),
                )

                lastReceivedRevision = snapshot.revision

                handleRemoteSnapshot(
                    remoteRuntimeState = remoteRuntimeState,
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

            /*
             * O servidor agenda bots e timeout no ticker autoritativo.
             */
            DominoMatchCommand.BotDecisionReady -> Unit

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
    }

    private fun handleRemoteSnapshot(
        remoteRuntimeState: DominoMatchRuntimeState,
        revision: Long,
        automaticPlayerIndexes: Set<Int>,
    ) {
        clearInFlightActionIfConfirmed(
            revision = revision,
        )

        pendingRemoteRuntimeStates.addLast(
            QueuedOnlineRuntimeState(
                runtimeState = remoteRuntimeState,
                revision = revision,
                automaticPlayerIndexes = automaticPlayerIndexes,
            ),
        )

        trace(
            level = OnlineTraceLevel.DEBUG,
            type = OnlineTraceType.SNAPSHOT_ENQUEUED,
            snapshotRevision = revision,
            runtimeState = remoteRuntimeState,
            automaticIndexes = automaticPlayerIndexes,
            attributes = mapOf(
                "queueDepth" to pendingRemoteRuntimeStates.size.toString(),
                "stableRevision" to stableRevision.toString(),
                "hasActivePresentation" to
                        (activePresentationRuntimeState != null).toString(),
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
            is DominoMatchPhase.PresentingMove -> Unit

            /*
             * O passe obrigatÃ³rio Ã© reduzido pelo servidor no ticker
             * autoritativo. O cliente encerra apenas a apresentaÃ§Ã£o visual.
             */
            is DominoMatchPhase.PresentingPass -> Unit

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

            trace(
                level = OnlineTraceLevel.INFO,
                type = OnlineTraceType.PRESENTATION_STARTED,
                snapshotRevision = queuedRuntimeState.revision,
                runtimeState = presentation.presentationRuntimeState,
                automaticIndexes = queuedRuntimeState.automaticPlayerIndexes,
                attributes = mapOf(
                    "queueDepth" to pendingRemoteRuntimeStates.size.toString(),
                    "stableRevision" to stableRevision.toString(),
                    "presentationPhase" to
                            presentation.presentationRuntimeState.phase.traceName(),
                ),
            )

            return
        }
    }

    private fun completeActivePresentation(): Boolean {
        val activeRuntimeState = activePresentationRuntimeState
            ?: return false

        val nextQueuedRuntimeState = pendingRemoteRuntimeStates.firstOrNull()

        if (nextQueuedRuntimeState != activeRuntimeState) {
            activePresentationRuntimeState = null

            trace(
                level = OnlineTraceLevel.WARN,
                type = OnlineTraceType.INVARIANT_VIOLATION,
                snapshotRevision = activeRuntimeState.revision,
                runtimeState = currentState,
                automaticIndexes = activeRuntimeState.automaticPlayerIndexes,
                attributes = mapOf(
                    "reason" to "active_presentation_not_at_queue_head",
                    "queueDepth" to pendingRemoteRuntimeStates.size.toString(),
                ),
            )

            return false
        }

        trace(
            level = OnlineTraceLevel.INFO,
            type = OnlineTraceType.PRESENTATION_FINISHED,
            snapshotRevision = activeRuntimeState.revision,
            runtimeState = currentState,
            automaticIndexes = activeRuntimeState.automaticPlayerIndexes,
            attributes = mapOf(
                "queueDepthBeforePromotion" to
                        pendingRemoteRuntimeStates.size.toString(),
            ),
        )

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

        trace(
            level = OnlineTraceLevel.INFO,
            type = OnlineTraceType.STABLE_STATE_PROMOTED,
            snapshotRevision = queuedRuntimeState.revision,
            runtimeState = queuedRuntimeState.runtimeState,
            automaticIndexes = queuedRuntimeState.automaticPlayerIndexes,
            attributes = mapOf(
                "queueDepthAfterPromotion" to
                        pendingRemoteRuntimeStates.size.toString(),
            ),
        )
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
            traceActionSuppressed(
                actionType = OnlinePlayerActionTypeDto.PLAY_MOVE,
                reason = "automatic_player",
                move = command.move,
            )
            return
        }

        if (inFlightAction != null) {
            traceActionSuppressed(
                actionType = OnlinePlayerActionTypeDto.PLAY_MOVE,
                reason = "in_flight_action",
                move = command.move,
            )
            return
        }

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            traceActionSuppressed(
                actionType = OnlinePlayerActionTypeDto.PLAY_MOVE,
                reason = "wrong_phase",
                move = command.move,
            )
            return
        }

        if (runtimeState.gameState.currentPlayerIndex != localPlayerIndex) {
            traceActionSuppressed(
                actionType = OnlinePlayerActionTypeDto.PLAY_MOVE,
                reason = "wrong_turn",
                move = command.move,
            )
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

    private fun submitStartNextRound() {
        if (inFlightAction != null) {
            traceActionSuppressed(
                actionType = OnlinePlayerActionTypeDto.START_NEXT_ROUND,
                reason = "in_flight_action",
            )
            return
        }

        val action = createOnlineStartNextRoundAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = stableRevision,
        )

        submitAction(action)
    }

    private fun submitStartNewMatch() {
        if (inFlightAction != null) {
            traceActionSuppressed(
                actionType = OnlinePlayerActionTypeDto.START_NEW_MATCH,
                reason = "in_flight_action",
            )
            return
        }

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

    private fun traceActionSuppressed(
        actionType: OnlinePlayerActionTypeDto,
        reason: String,
        move: PlayableMove? = null,
    ) {
        val attributes = buildMap {
            put("actionType", actionType.name)
            put("reason", reason)
            put("stableRevision", stableRevision.toString())
            put("stablePhase", stableRuntimeState.phase.traceName())
            put(
                "currentPlayerIndex",
                stableRuntimeState.gameState.currentPlayerIndex.toString(),
            )
            inFlightAction?.let { action ->
                put("inFlightActionId", action.actionId)
                put("inFlightActionRevision", action.revision.toString())
                put("inFlightActionType", action.type.name)
            }
            move?.let { playableMove ->
                put("piece", "${playableMove.piece.left}-${playableMove.piece.right}")
                put("boardSide", playableMove.side.name)
                put("flipped", playableMove.flipped.toString())
            }
        }

        trace(
            level = OnlineTraceLevel.WARN,
            type = OnlineTraceType.ACTION_SUPPRESSED,
            snapshotRevision = stableRevision,
            runtimeState = stableRuntimeState,
            attributes = attributes,
        )
    }

    private fun clearInFlightActionIfConfirmed(
        revision: Long,
    ) {
        val action = inFlightAction ?: return

        if (revision > action.revision) {
            inFlightAction = null
        }
    }

    private fun submitAction(
        action: OnlinePlayerActionDto,
    ) {
        if (inFlightAction != null) {
            traceActionSuppressed(
                actionType = action.type,
                reason = "in_flight_action",
            )
            return
        }

        trace(
            level = OnlineTraceLevel.DEBUG,
            type = OnlineTraceType.ACTION_PREPARED,
            action = action,
            runtimeState = stableRuntimeState,
            attributes = action.traceAttributes(),
        )

        inFlightAction = action

        trace(
            level = OnlineTraceLevel.INFO,
            type = OnlineTraceType.ACTION_SUBMITTED,
            action = action,
            runtimeState = stableRuntimeState,
            attributes = action.traceAttributes(),
        )

        coordinatorScope.launch {
            val result = repository.submitAction(
                action = action,
            )

            if (result.accepted) {
                trace(
                    level = OnlineTraceLevel.INFO,
                    type = OnlineTraceType.ACTION_ACCEPTED,
                    action = action,
                    snapshotRevision = result.revision,
                    runtimeState = stableRuntimeState,
                    attributes = action.traceAttributes() + mapOf(
                        "resultRevision" to
                                (result.revision?.toString() ?: "null"),
                    ),
                )

                return@launch
            }

            trace(
                level = OnlineTraceLevel.WARN,
                type = OnlineTraceType.ACTION_REJECTED,
                action = action,
                snapshotRevision = result.revision,
                runtimeState = stableRuntimeState,
                attributes = action.traceAttributes() + mapOf(
                    "reason" to result.reason.orEmpty().take(180),
                    "resultRevision" to
                            (result.revision?.toString() ?: "null"),
                ),
            )

            clearInFlightActionAfterRejection(
                action = action,
            )
        }
    }

    private fun clearInFlightActionAfterRejection(
        action: OnlinePlayerActionDto,
    ) {
        if (inFlightAction?.actionId == action.actionId) {
            inFlightAction = null
        }
    }

    private fun trace(
        level: OnlineTraceLevel,
        type: OnlineTraceType,
        source: OnlineTraceSource = OnlineTraceSource.CLIENT_COORDINATOR,
        action: OnlinePlayerActionDto? = null,
        snapshotRevision: Long? = null,
        runtimeState: DominoMatchRuntimeState? = null,
        automaticIndexes: Set<Int> = automaticPlayerIndexes,
        attributes: Map<String, String> = emptyMap(),
    ) {
        traceLogger.log(
            level = level,
            source = source,
            type = type,
            context = OnlineTraceContext(
                roomId = roomId,
                matchId = matchId,
                playerId = localPlayerId,
                localSeatIndex = localPlayerIndex,
                actionId = action?.actionId,
                actionRevision = action?.revision,
                snapshotRevision = snapshotRevision,
            ),
            state = runtimeState?.toTraceStateSummary(
                automaticIndexes = automaticIndexes,
            ),
            attributes = attributes,
        )
    }

    private fun DominoMatchRuntimeState.toTraceStateSummary(
        automaticIndexes: Set<Int>,
    ): OnlineTraceStateSummary {
        return OnlineTraceStateSummary(
            roundNumber = roundNumber,
            phase = phase.traceName(),
            currentPlayerIndex = gameState.currentPlayerIndex,
            boardPieceCount = gameState.board.size,
            teamScores = gameState.teamScores,
            playerClockMillis = playerClockMillis,
            automaticPlayerIndexes = automaticIndexes.sorted(),
            stateFingerprint = createOnlineTraceStateFingerprint(
                runtimeState = this,
                automaticPlayerIndexes = automaticIndexes,
            ),
        )
    }

    private fun createPresentationId(
        queuedRuntimeState: QueuedOnlineRuntimeState,
    ): String {
        /*
         * A revisão autoritativa é o identificador estável da apresentação.
         * A fase do snapshot de destino pode já ser WaitingForLocalMove, pois
         * a UI apresenta a transição entre o estado estável anterior e ele.
         */
        return "r${queuedRuntimeState.revision}"
    }

    private fun traceLevelForUiEvent(
        type: OnlineTraceType,
    ): OnlineTraceLevel {
        return when (type) {
            OnlineTraceType.UI_MOVE_INTENT_REJECTED,
            OnlineTraceType.ANIMATION_CANCELLED,
            OnlineTraceType.ANIMATION_FALLBACK_USED -> OnlineTraceLevel.WARN

            else -> OnlineTraceLevel.DEBUG
        }
    }

    private fun DominoMatchPhase.traceName(): String {
        return when (this) {
            DominoMatchPhase.RoundIntro -> "ROUND_INTRO"

            DominoMatchPhase.WaitingForLocalMove ->
                "WAITING_FOR_LOCAL_MOVE"

            is DominoMatchPhase.PresentingMove ->
                "PRESENTING_MOVE"

            is DominoMatchPhase.PresentingPass ->
                "PRESENTING_PASS"

            DominoMatchPhase.RoundSummary -> "ROUND_SUMMARY"

            DominoMatchPhase.MatchFinished -> "MATCH_FINISHED"
        }
    }

    private fun OnlinePlayerActionDto.traceAttributes(): Map<String, String> {
        val attributes = mutableMapOf(
            "actionType" to type.name,
        )

        move?.let { onlineMove ->
            attributes["piece"] =
                "${onlineMove.piece.left}-${onlineMove.piece.right}"
            attributes["boardSide"] = onlineMove.side.name
            attributes["flipped"] = onlineMove.flipped.toString()
        }

        return attributes
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