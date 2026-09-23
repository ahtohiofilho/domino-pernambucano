package com.ahtohiofilho.dominopernambucano.online
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchMetricAccumulator

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnlineActiveMatchResourceLossResolution(
    val binding: OnlineParticipationBinding,
    val reason: OnlineActiveMatchResourceLossReason,
)

data class OnlineActiveMatchParticipationAuthorizationLossResolution(
    val binding: OnlineParticipationBinding,
    val reason: OnlineActiveMatchParticipationAuthorizationLossReason,
)

data class OnlinePresentationCatchUpPolicy(
    val maxQueuedGameplayPresentations: Int = 4,
    val retainedGameplayPresentations: Int = 1,
) {
    init {
        require(maxQueuedGameplayPresentations >= 1) {
            "O limite de apresentações pendentes deve ser positivo."
        }

        require(
            retainedGameplayPresentations in
                    1..maxQueuedGameplayPresentations
        ) {
            "A cauda preservada deve caber no orçamento normal."
        }
    }
}

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
    private val catchUpPolicy: OnlinePresentationCatchUpPolicy =
        OnlinePresentationCatchUpPolicy(),
    private val presentationWatchdogMillis: Long = 2_500L,
    /*
     * Production clock interpolation uses elapsed monotonic time instead of
     * summing UI heartbeat intervals. Tests can inject a deterministic source.
     */
    private val monotonicNowMillis: () -> Long = {
        System.nanoTime() / 1_000_000L
    },
    val matchMode: DominoMatchMode =
        DominoMatchMode.PRIVATE_UNRANKED,
    private val onMatchFinished: () -> Unit = {},
) : DominoMatchCoordinator, OnlineGameUiTraceReporter {
    private val coordinatorScope = CoroutineScope(
        SupervisorJob() + coroutineDispatcher,
    )

    private val initialRuntimeState = initialSnapshot.toRuntimeState(
        localPlayerIndex = localPlayerIndex,
    )

    private val mutableState = MutableStateFlow(
        initialOnlineMatchPresentationState(
            runtimeState = initialRuntimeState,
        )
    )

    private var stableRuntimeState = initialRuntimeState
    private var stableRevision = initialSnapshot.revision

    /*
     * RoundIntro continua sendo a fase autoritativa ate o servidor publicar
     * a revisao seguinte. Este flag registra somente que a animacao local ja
     * terminou, permitindo consumir essa revisao quando ela chegar.
     */
    private var roundIntroPresentationCompleted = false

    /*
     * Every visible online countdown is derived from the last promoted
     * authoritative snapshot. The UI heartbeat only asks for a re-projection;
     * it is not itself the source of elapsed time.
     */
    private var stableClockAnchor =
        OnlineAuthoritativeClockAnchor(
            runtimeState = initialRuntimeState,
            receivedAtMonotonicMillis = monotonicNowMillis(),
        )

    /*
     * The fake repository has no autonomous server ticker, so its historical
     * client-driven progression keeps a deterministic elapsed accumulator.
     * Remote/production repositories never use this value.
     */
    private var fakeClientDrivenElapsedSinceAnchorMillis = 0L

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

    private var rankedMetricAccumulator: RankedMatchMetricAccumulator? =
        initialSnapshot.rankedMetricAccumulator

    /*
     * Uma interação humana só pode gerar uma ação por vez. A UI permanece
     * orientada pelo snapshot autoritativo; a trava evita que múltiplos drags
     * reutilizem a mesma revisão enquanto a confirmação ainda está em trânsito.
     */
    private var inFlightAction: OnlinePlayerActionDto? = null

    private val mutableActiveSessionInvalidation =
        MutableStateFlow<OnlineParticipationBinding?>(null)

    private val mutableActiveResourceLoss =
        MutableStateFlow<OnlineActiveMatchResourceLossResolution?>(null)

    private val mutableActiveParticipationAuthorizationLoss =
        MutableStateFlow<OnlineActiveMatchParticipationAuthorizationLossResolution?>(null)

    private var matchFinishedCallbackDispatched = false
    private var completedMatchPresentationAcknowledged = false

    override val state: StateFlow<DominoMatchRuntimeState> =
        mutableState.asStateFlow()

    val activeSessionInvalidation:
        StateFlow<OnlineParticipationBinding?> =
        mutableActiveSessionInvalidation.asStateFlow()

    val activeResourceLoss:
        StateFlow<OnlineActiveMatchResourceLossResolution?> =
        mutableActiveResourceLoss.asStateFlow()

    val activeParticipationAuthorizationLoss:
        StateFlow<OnlineActiveMatchParticipationAuthorizationLossResolution?> =
        mutableActiveParticipationAuthorizationLoss.asStateFlow()

    override val currentState: DominoMatchRuntimeState
        get() = mutableState.value
    override fun currentRankedMetricAccumulator(): RankedMatchMetricAccumulator? {
        return activePresentationRuntimeState?.rankedMetricAccumulator
            ?: rankedMetricAccumulator
    }

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

                val previousReceivedRevision = lastReceivedRevision
                val remoteRuntimeState = snapshot.toRuntimeState(
                    localPlayerIndex = localPlayerIndex,
                )
                val hasRevisionGap =
                    snapshot.revision > previousReceivedRevision + 1L

                trace(
                    level = OnlineTraceLevel.INFO,
                    type = OnlineTraceType.SNAPSHOT_RECEIVED,
                    snapshotRevision = snapshot.revision,
                    runtimeState = remoteRuntimeState,
                    automaticIndexes = snapshot.automaticPlayerIndexes.toSet(),
                    attributes = mapOf(
                        "previousReceivedRevision" to
                                previousReceivedRevision.toString(),
                        "hasRevisionGap" to hasRevisionGap.toString(),
                    ),
                )

                lastReceivedRevision = snapshot.revision

                handleRemoteSnapshot(
                    remoteRuntimeState = remoteRuntimeState,
                    revision = snapshot.revision,
                    automaticPlayerIndexes = snapshot.automaticPlayerIndexes.toSet(),
                    rankedMetricAccumulator = snapshot.rankedMetricAccumulator,
                    hasRevisionGap = hasRevisionGap,
                    receivedAtMonotonicMillis = monotonicNowMillis(),
                )
            }
        }

        coordinatorScope.launch {
            repository.activeMatchSessionInvalidationEvents.collect {
                    invalidation ->
                if (
                    invalidation.roomId != roomId ||
                    invalidation.matchId != matchId ||
                    invalidation.playerId != localPlayerId
                ) {
                    return@collect
                }

                inFlightAction = null

                mutableActiveSessionInvalidation.value =
                    OnlineParticipationBinding(
                        roomId = roomId,
                        matchId = matchId,
                        playerId = localPlayerId,
                        localSeatIndex = localPlayerIndex,
                    )
            }
        }

        coordinatorScope.launch {
            repository.activeMatchResourceLossEvents.collect {
                    resourceLoss ->
                if (
                    resourceLoss.roomId != roomId ||
                    resourceLoss.matchId != matchId ||
                    resourceLoss.playerId != localPlayerId
                ) {
                    return@collect
                }

                inFlightAction = null

                mutableActiveResourceLoss.value =
                    OnlineActiveMatchResourceLossResolution(
                        binding = OnlineParticipationBinding(
                            roomId = roomId,
                            matchId = matchId,
                            playerId = localPlayerId,
                            localSeatIndex = localPlayerIndex,
                        ),
                        reason = resourceLoss.reason,
                    )
            }
        }

        coordinatorScope.launch {
            repository.activeMatchParticipationAuthorizationLossEvents.collect {
                    authorizationLoss ->
                if (
                    authorizationLoss.roomId != roomId ||
                    authorizationLoss.matchId != matchId ||
                    authorizationLoss.playerId != localPlayerId
                ) {
                    return@collect
                }

                inFlightAction = null

                mutableActiveParticipationAuthorizationLoss.value =
                    OnlineActiveMatchParticipationAuthorizationLossResolution(
                        binding = OnlineParticipationBinding(
                            roomId = roomId,
                            matchId = matchId,
                            playerId = localPlayerId,
                            localSeatIndex = localPlayerIndex,
                        ),
                        reason = authorizationLoss.reason,
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
             * O servidor remoto continua responsavel pela progressao
             * autoritativa. Somente o backend fake aceita gatilhos conduzidos
             * pelo cliente para APPLICATION, passe obrigatorio e timeout.
             */            DominoMatchCommand.BotDecisionReady -> {
                submitClientDrivenApplicationTurnProgression()
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

    fun acknowledgeCompletedMatchPresented() {
        if (
            completedMatchPresentationAcknowledged ||
            currentState.phase != DominoMatchPhase.MatchFinished
        ) {
            return
        }

        repository.acknowledgeCompletedMatchPresentedLocally(
            binding = OnlineParticipationBinding(
                roomId = roomId,
                matchId = matchId,
                playerId = localPlayerId,
                localSeatIndex = localPlayerIndex,
            ),
        )

        completedMatchPresentationAcknowledged = true
    }

    fun dispose() {
        if (currentState.phase == DominoMatchPhase.MatchFinished) {
            repository.releaseCompletedMatchLocally()
        }

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

        if (supportsClientDrivenFakeProgression()) {
            fakeClientDrivenElapsedSinceAnchorMillis +=
                command.elapsedMillis
        }

        /*
         * Do not subtract command.elapsedMillis from the already displayed
         * value. A delayed Compose coroutine would make fixed 250 ms ticks
         * accumulate less elapsed time than the authoritative server clock.
         * Recompute from the immutable authoritative snapshot baseline instead.
         */
        val projectedRuntimeState =
            currentAuthoritativeDisplayRuntimeState()

        mutableState.value = projectedRuntimeState

        val currentPlayerIndex =
            projectedRuntimeState.gameState.currentPlayerIndex

        if (
            isPlayerClockExpired(
                clocks = projectedRuntimeState.playerClockMillis,
                playerIndex = currentPlayerIndex,
            )
        ) {
            /*
             * Remote production progression remains server-authoritative.
             * This method submits only for OnlineClientDrivenFakeProgression.
             */
            submitClientDrivenTimeoutProgression()
        }
    }

    private fun handleRemoteSnapshot(
        remoteRuntimeState: DominoMatchRuntimeState,
        revision: Long,
        automaticPlayerIndexes: Set<Int>,
        rankedMetricAccumulator: RankedMatchMetricAccumulator?,
        hasRevisionGap: Boolean,
        receivedAtMonotonicMillis: Long,
    ) {
        clearInFlightActionIfConfirmed(
            revision = revision,
        )

        if (hasRevisionGap) {
            hardResyncToRemoteSnapshot(
                remoteRuntimeState = remoteRuntimeState,
                revision = revision,
                automaticPlayerIndexes = automaticPlayerIndexes,
                rankedMetricAccumulator = rankedMetricAccumulator,
                receivedAtMonotonicMillis = receivedAtMonotonicMillis,
                reason = "missing_revision_history",
            )
            return
        }

        pendingRemoteRuntimeStates.addLast(
            QueuedOnlineRuntimeState(
                runtimeState = remoteRuntimeState,
                revision = revision,
                automaticPlayerIndexes = automaticPlayerIndexes,
                rankedMetricAccumulator = rankedMetricAccumulator,
                receivedAtMonotonicMillis = receivedAtMonotonicMillis,
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

    /*
     * Uma lacuna de revisões é diferente de um lote contínuo: não existe
     * baseline confiável para reproduzir ou compactar a narrativa visual.
     * Somente nesse caso uma apresentação em curso pode ser cancelada para
     * convergir imediatamente ao estado autoritativo mais recente.
     */
    private fun hardResyncToRemoteSnapshot(
        remoteRuntimeState: DominoMatchRuntimeState,
        revision: Long,
        automaticPlayerIndexes: Set<Int>,
        rankedMetricAccumulator: RankedMatchMetricAccumulator?,
        receivedAtMonotonicMillis: Long,
        reason: String,
    ) {
        val discardedQueueDepth = pendingRemoteRuntimeStates.size
        val cancelledPresentationRevision =
            activePresentationRuntimeState?.revision
        val previousStableRevision = stableRevision

        pendingRemoteRuntimeStates.clear()
        activePresentationRuntimeState = null

        trace(
            level = OnlineTraceLevel.WARN,
            type = OnlineTraceType.PRESENTATION_HARD_RESYNC,
            snapshotRevision = revision,
            runtimeState = remoteRuntimeState,
            automaticIndexes = automaticPlayerIndexes,
            attributes = buildMap {
                put("reason", reason)
                put("previousStableRevision", previousStableRevision.toString())
                put("discardedQueueDepth", discardedQueueDepth.toString())
                cancelledPresentationRevision?.let { activeRevision ->
                    put("cancelledPresentationRevision", activeRevision.toString())
                }
            },
        )

        promoteRuntimeState(
            queuedRuntimeState = QueuedOnlineRuntimeState(
                runtimeState = remoteRuntimeState,
                revision = revision,
                automaticPlayerIndexes = automaticPlayerIndexes,
                rankedMetricAccumulator = rankedMetricAccumulator,
                receivedAtMonotonicMillis = receivedAtMonotonicMillis,
            ),
        )
    }

    private fun handleRoundIntroFinished() {
        if (completeActivePresentation()) {
            advancePresentationQueue()
        }

        /*
         * A UI terminou a intro, mas o estado autoritativo continua RoundIntro
         * ate a revisao de liberacao chegar. A partir daqui, snapshots novos
         * podem atravessar a fila sem serem bloqueados pela propria intro que
         * acabou de terminar.
         */
        roundIntroPresentationCompleted = true
        advancePresentationQueue()

        /*
         * Somente a autoridade visual envia REQUEST_SNAPSHOT. Os demais
         * clientes apenas aguardam a mesma revisao autoritativa.
         */
        if (stableRuntimeState.phase == DominoMatchPhase.RoundIntro) {
            if (shouldReleaseAuthoritativeRoundIntro()) {
                submitClientDrivenSnapshotRequest()
            }
            return
        }

        mutableState.value = currentAuthoritativeDisplayRuntimeState()

        advancePresentationQueue()
        submitClientDrivenMandatoryPassProgression()
    }

    private fun shouldReleaseAuthoritativeRoundIntro(): Boolean {
        val players = stableRuntimeState.gameState.players
        val currentPlayerIndex =
            stableRuntimeState.gameState.currentPlayerIndex
        val currentPlayer =
            players.getOrNull(currentPlayerIndex)
                ?: return false

        val authorityIndex =
            if (
                currentPlayer.participantType ==
                DominoParticipantType.HUMAN
            ) {
                currentPlayerIndex
            } else {
                players.indexOfFirst { player ->
                    player.participantType ==
                        DominoParticipantType.HUMAN
                }.takeIf { index -> index >= 0 }
                    ?: return false
            }

        return localPlayerIndex == authorityIndex
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
             * O passe obrigatório é reduzido pelo servidor no ticker
             * autoritativo. O cliente encerra apenas a apresentação visual.
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
            ) &&
            !(
                currentState.phase == DominoMatchPhase.RoundIntro &&
                roundIntroPresentationCompleted
            )
        ) {
            return
        }

        compactGameplayBacklogIfNeeded()

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

            if (
                presentation.presentationRuntimeState.phase ==
                DominoMatchPhase.RoundIntro
            ) {
                roundIntroPresentationCompleted = false
            }

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

            if (presentation.isGameplayPresentation()) {
                schedulePresentationWatchdog(
                    queuedRuntimeState = queuedRuntimeState,
                )
            }

            return
        }
    }

    /*
     * A presentation callback is a UI convenience, never an authority boundary.
     * If Compose fails to start or finish an animation, the online coordinator
     * must still converge to the authoritative snapshot instead of keeping an
     * active presentation forever and accumulating every later server revision.
     */
    private fun schedulePresentationWatchdog(
        queuedRuntimeState: QueuedOnlineRuntimeState,
    ) {
        val watchedRevision = queuedRuntimeState.revision

        coordinatorScope.launch {
            delay(presentationWatchdogMillis)

            val activeRuntimeState =
                activePresentationRuntimeState
                    ?: return@launch

            if (activeRuntimeState.revision != watchedRevision) {
                return@launch
            }

            trace(
                level = OnlineTraceLevel.WARN,
                type = OnlineTraceType.INVARIANT_VIOLATION,
                snapshotRevision = watchedRevision,
                runtimeState = currentState,
                automaticIndexes =
                    activeRuntimeState.automaticPlayerIndexes,
                attributes = mapOf(
                    "reason" to "presentation_watchdog_timeout",
                    "watchdogMillis" to
                        presentationWatchdogMillis.toString(),
                    "queueDepth" to
                        pendingRemoteRuntimeStates.size.toString(),
                    "stableRevision" to stableRevision.toString(),
                ),
            )

            if (completeActivePresentation()) {
                advancePresentationQueue()
            }
        }
    }
    /*
     * Só apresentações de jogada e toque consomem o orçamento visual.
     * Enquanto a linha de revisões for contínua, mesmo uma dívida visual alta
     * é compactada para uma baseline silenciosa e uma cauda animável. O
     * resync duro fica reservado à ausência de histórico confiável.
     */
    private fun compactGameplayBacklogIfNeeded() {
        if (activePresentationRuntimeState != null) {
            return
        }

        val gameplayPresentationIndexes =
            findQueuedGameplayPresentationIndexes()

        val gameplayPresentationCount =
            gameplayPresentationIndexes.size

        if (
            gameplayPresentationCount <=
            catchUpPolicy.maxQueuedGameplayPresentations
        ) {
            return
        }

        val firstRetainedPresentationIndex =
            gameplayPresentationIndexes[
                gameplayPresentationIndexes.size -
                        catchUpPolicy.retainedGameplayPresentations
            ]

        val baselineIndex = firstRetainedPresentationIndex - 1

        /*
         * O estado estável atual já é a base da primeira apresentação
         * relevante. Sem uma revisão predecessora na fila não há nada seguro
         * para compactar.
         */
        if (baselineIndex < 0) {
            return
        }

        val queueDepthBeforeCompaction = pendingRemoteRuntimeStates.size
        val previousStableRevision = stableRevision
        val baselineRuntimeState =
            pendingRemoteRuntimeStates.elementAt(baselineIndex)
        val skippedSnapshotCount = baselineIndex

        repeat(baselineIndex + 1) {
            pendingRemoteRuntimeStates.removeFirst()
        }

        trace(
            level = OnlineTraceLevel.INFO,
            type = OnlineTraceType.PRESENTATION_BACKLOG_COMPACTED,
            snapshotRevision = baselineRuntimeState.revision,
            runtimeState = baselineRuntimeState.runtimeState,
            automaticIndexes = baselineRuntimeState.automaticPlayerIndexes,
            attributes = mapOf(
                "previousStableRevision" to previousStableRevision.toString(),
                "baselineRevision" to baselineRuntimeState.revision.toString(),
                "skippedSnapshotCount" to skippedSnapshotCount.toString(),
                "gameplayPresentationCount" to
                        gameplayPresentationCount.toString(),
                "retainedGameplayPresentations" to
                        catchUpPolicy.retainedGameplayPresentations.toString(),
                "queueDepthBeforeCompaction" to
                        queueDepthBeforeCompaction.toString(),
                "queueDepthAfterCompaction" to
                        pendingRemoteRuntimeStates.size.toString(),
            ),
        )

        /*
         * A baseline não é apresentada como animação. Ela torna a mesa
         * coerente imediatamente antes da cauda preservada, permitindo que a
         * última jogada ou toque seja mostrado em vez de teleportado.
         */
        promoteRuntimeState(
            queuedRuntimeState = baselineRuntimeState,
        )
    }

    private fun findQueuedGameplayPresentationIndexes(): List<Int> {
        var previousRuntimeState = stableRuntimeState

        return buildList {
            pendingRemoteRuntimeStates.forEachIndexed { index, queuedRuntimeState ->
                val presentation = buildPresentationBridge(
                    previousRuntimeState = previousRuntimeState,
                    remoteRuntimeState = queuedRuntimeState.runtimeState,
                )

                if (presentation?.isGameplayPresentation() == true) {
                    add(index)
                }

                previousRuntimeState = queuedRuntimeState.runtimeState
            }
        }
    }

    private fun OnlinePresentationBridge.isGameplayPresentation(): Boolean {
        return when (presentationRuntimeState.phase) {
            is DominoMatchPhase.PresentingMove,
            is DominoMatchPhase.PresentingPass -> true

            else -> false
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

        if (
            queuedRuntimeState.runtimeState.phase !=
            DominoMatchPhase.RoundIntro
        ) {
            roundIntroPresentationCompleted = false
        }

        automaticPlayerIndexes = queuedRuntimeState.automaticPlayerIndexes
        rankedMetricAccumulator = queuedRuntimeState.rankedMetricAccumulator

        stableClockAnchor =
            OnlineAuthoritativeClockAnchor(
                runtimeState = queuedRuntimeState.runtimeState,
                receivedAtMonotonicMillis =
                    queuedRuntimeState.receivedAtMonotonicMillis,
            )
        fakeClientDrivenElapsedSinceAnchorMillis = 0L

        mutableState.value = currentAuthoritativeDisplayRuntimeState()

        if (
            !matchFinishedCallbackDispatched &&
            queuedRuntimeState.runtimeState.phase ==
                DominoMatchPhase.MatchFinished
        ) {
            matchFinishedCallbackDispatched = true
            onMatchFinished()
        }

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

        submitClientDrivenMandatoryPassProgression()
    }

    private fun isPresentationInProgress(
        phase: DominoMatchPhase,
    ): Boolean {
        return phase == DominoMatchPhase.RoundIntro ||
                phase is DominoMatchPhase.PresentingMove ||
                phase is DominoMatchPhase.PresentingPass
    }

    private fun currentAuthoritativeDisplayRuntimeState():
        DominoMatchRuntimeState {
        val stableDisplayState =
            stableRuntimeState.toStableDisplayRuntimeState()

        if (!stableDisplayState.clockPolicy.enabled) {
            return stableDisplayState
        }

        if (
            stableDisplayState.phase !=
            DominoMatchPhase.WaitingForLocalMove
        ) {
            return stableDisplayState
        }

        val monotonicElapsedMillis = (
            monotonicNowMillis() -
                stableClockAnchor.receivedAtMonotonicMillis
        ).coerceAtLeast(0L)

        val elapsedSinceAnchorMillis =
            if (supportsClientDrivenFakeProgression()) {
                maxOf(
                    monotonicElapsedMillis,
                    fakeClientDrivenElapsedSinceAnchorMillis,
                )
            } else {
                monotonicElapsedMillis
            }

        return projectOnlineAuthoritativeClock(
            runtimeState = stableDisplayState.copy(
                playerClockMillis =
                    stableClockAnchor.runtimeState.playerClockMillis,
            ),
            elapsedSinceSnapshotMillis =
                elapsedSinceAnchorMillis,
        )
    }

    private fun submitClientDrivenApplicationTurnProgression() {
        if (!supportsClientDrivenFakeProgression()) {
            return
        }

        val runtimeState = stableRuntimeState

        if (
            runtimeState.phase !=
            DominoMatchPhase.WaitingForLocalMove
        ) {
            return
        }

        val currentPlayerIndex =
            runtimeState.gameState.currentPlayerIndex

        val currentParticipant =
            runtimeState.gameState.players
                .getOrNull(currentPlayerIndex)
                ?: return

        if (
            currentParticipant.participantType !=
            DominoParticipantType.APPLICATION
        ) {
            return
        }

        submitClientDrivenSnapshotRequest()
    }

    private fun submitClientDrivenMandatoryPassProgression() {
        if (!supportsClientDrivenFakeProgression()) {
            return
        }

        if (
            activePresentationRuntimeState != null ||
            pendingRemoteRuntimeStates.isNotEmpty()
        ) {
            return
        }

        val runtimeState = stableRuntimeState
        val passPhase =
            runtimeState.phase as?
                    DominoMatchPhase.PresentingPass
                ?: return

        if (
            passPhase.playerIndex !=
            runtimeState.gameState.currentPlayerIndex
        ) {
            return
        }

        submitClientDrivenSnapshotRequest()
    }

    private fun submitClientDrivenTimeoutProgression() {
        if (!supportsClientDrivenFakeProgression()) {
            return
        }

        if (
            stableRuntimeState.phase !=
            DominoMatchPhase.WaitingForLocalMove
        ) {
            return
        }

        val displayedRuntimeState = mutableState.value
        val currentPlayerIndex =
            displayedRuntimeState.gameState.currentPlayerIndex

        if (
            !isPlayerClockExpired(
                clocks =
                    displayedRuntimeState.playerClockMillis,
                playerIndex = currentPlayerIndex,
            )
        ) {
            return
        }

        submitClientDrivenSnapshotRequest()
    }

    private fun supportsClientDrivenFakeProgression(): Boolean {
        return repository is OnlineClientDrivenFakeProgression
    }

    private fun submitClientDrivenSnapshotRequest() {
        if (inFlightAction != null) {
            return
        }

        val action = createOnlineSnapshotRequestAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = stableRevision,
        )

        submitAction(action)
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
                    /*
                     * A animacao precisa manter o tabuleiro anterior para
                     * mostrar a peca saindo da mao e chegando a mesa. Os
                     * relogios, porem, ja pertencem ao snapshot autoritativo
                     * aceito pelo servidor. Usar os relogios do estado anterior
                     * fazia a UI exibir o tempo gasto durante PresentingMove,
                     * mesmo depois da recarga pela reserva.
                     */
                    playerClockMillis =
                        remoteRuntimeState.playerClockMillis,
                    playerClockReserveMillis =
                        remoteRuntimeState.playerClockReserveMillis,
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

        return when (val previousPhase = previousRuntimeState.phase) {
            DominoMatchPhase.WaitingForLocalMove -> {
                previousGameState.currentPlayerIndex
            }

            is DominoMatchPhase.PresentingPass -> {
                previousPhase.playerIndex
            }

            else -> null
        }
    }
}

private data class OnlineAuthoritativeClockAnchor(
    val runtimeState: DominoMatchRuntimeState,
    val receivedAtMonotonicMillis: Long,
)

private data class QueuedOnlineRuntimeState(
    val runtimeState: DominoMatchRuntimeState,
    val revision: Long,
    val automaticPlayerIndexes: Set<Int>,
    val rankedMetricAccumulator: RankedMatchMetricAccumulator?,
    val receivedAtMonotonicMillis: Long,
)

internal fun initialOnlineMatchPresentationState(
    runtimeState: DominoMatchRuntimeState,
): DominoMatchRuntimeState {
    /*
     * O servidor passa a dizer explicitamente quando existe RoundIntro.
     * Assim, retomar uma partida em WaitingForLocalMove nao fabrica uma nova
     * introducao enquanto o relogio real ja esta correndo.
     */
    return runtimeState
}

internal fun projectOnlineAuthoritativeClock(
    runtimeState: DominoMatchRuntimeState,
    elapsedSinceSnapshotMillis: Long,
): DominoMatchRuntimeState {
    if (!runtimeState.clockPolicy.enabled) {
        return runtimeState
    }

    if (
        runtimeState.phase !=
        DominoMatchPhase.WaitingForLocalMove
    ) {
        return runtimeState
    }

    val currentPlayerIndex =
        runtimeState.gameState.currentPlayerIndex

    return runtimeState.copy(
        playerClockMillis =
            decrementPlayerClockMillis(
                clocks = runtimeState.playerClockMillis,
                playerIndex = currentPlayerIndex,
                elapsedMillis =
                    elapsedSinceSnapshotMillis
                        .coerceAtLeast(0L),
            ),
    )
}

private data class OnlinePresentationBridge(
    val presentationRuntimeState: DominoMatchRuntimeState,
)

/*
 * Um snapshot autoritativo em PRESENTING_PASS anuncia um passe que o servidor
 * ainda vai confirmar no tick seguinte. A UI só deve exibir esse toque quando
 * a transição para a revisão seguinte permitir convertê-lo em uma apresentação
 * FIFO explícita. O estado bruto continua armazenado em stableRuntimeState para
 * preservar o playerIndex do passe pendente.
 */
private fun DominoMatchRuntimeState.toStableDisplayRuntimeState(): DominoMatchRuntimeState {
    return if (phase is DominoMatchPhase.PresentingPass) {
        copy(
            phase = DominoMatchPhase.WaitingForLocalMove,
        )
    } else {
        this
    }
}

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