package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RemoteOnlineRoomRepository(
    private val config: OnlineBackendConfig,
    private val apiClient: RemoteOnlineApiClient? = createApiClientOrNull(config),
    private val pollingPolicy: OnlineRemotePollingPolicy =
        OnlineRemotePollingPolicy.Disabled,
    private val traceLogger: OnlineTraceLogger =
        OnlineTraceLogger(),
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val anonymousSessionRepository:
        OnlineAnonymousSessionRepository? = null,
    private val onlineParticipationBindingRepository:
        OnlineParticipationBindingRepository? = null,
) : OnlineRoomRepository {
    private val repositoryScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )

    /*
     * Polling e envio de ação atualizam os mesmos StateFlows. Uma única porta
     * serializa o pipeline local e impede que uma resposta de polling mais
     * antiga sobrescreva o snapshot obtido logo após uma ação.
     */
    private val refreshMutex = Mutex()

    private val mutableRoomSnapshot =
        MutableStateFlow<OnlineRoomSnapshotDto?>(null)

    private val mutableMatchSnapshot =
        MutableStateFlow<OnlineMatchSnapshotDto?>(null)

    /*
     * As revisões que precisam ser apresentadas seguem por uma via sequencial.
     * O StateFlow permanece como leitura do último estado autoritativo.
     */
    private val mutableMatchSnapshotEvents =
        MutableSharedFlow<OnlineMatchSnapshotDto>(
            extraBufferCapacity = 256,
        )

    private var pollingJob: Job? = null
    private var pollingRoomId: String? = null
    private var activePlayerId: String? = null
    private var activeAnonymousSession: OnlineAnonymousSessionDto? = null

    private data class PreparedRoomParticipant(
        val playerId: String,
        val anonymousSession: OnlineAnonymousSessionDto? = null,
        val usesDevelopmentAuthentication: Boolean,
    )

    private data class PreparedRoomOperationResult(
        val participant: PreparedRoomParticipant,
        val result: OnlineRoomOperationResultDto,
    )

    override val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?> =
        mutableRoomSnapshot.asStateFlow()

    override val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?> =
        mutableMatchSnapshot.asStateFlow()

    override val matchSnapshotEvents: Flow<OnlineMatchSnapshotDto> =
        mutableMatchSnapshotEvents.asSharedFlow()

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        val client = apiClient
            ?: return rejectedRoomOperation(
                reason = getUnavailableBackendReason(),
            ).also {
                trace(
                    level = OnlineTraceLevel.WARN,
                    type = OnlineTraceType.TRANSPORT_FAILURE,
                    playerId = request.localPlayerId,
                    attributes = mapOf(
                        "operation" to "create_room",
                        "reason" to getUnavailableBackendReason(),
                    ),
                )
            }

        val startedAtEpochMillis = nowEpochMillis()

        return runCatching {
            val participant = prepareRoomParticipant(
                client = client,
                requestedPlayerId = request.localPlayerId,
            )

            try {
                PreparedRoomOperationResult(
                    participant = participant,
                    result = client.createRoom(
                        request.copy(
                            localPlayerId = participant.playerId,
                        ),
                    ),
                )
            } finally {
                if (participant.usesDevelopmentAuthentication) {
                    restoreActiveParticipantAuthentication(
                        client = client,
                    )
                }
            }
        }.fold(
            onSuccess = { operationResult ->
                val participant = operationResult.participant
                val result = operationResult.result

                traceRoomOperationResult(
                    operation = "create_room",
                    result = result,
                    playerId = participant.playerId,
                )

                applyRoomOperationResult(
                    result = result,
                    client = client,
                    operation = "create_room",
                    playerId = participant.playerId,
                    anonymousSession = participant.anonymousSession,
                    activateParticipant = !participant.usesDevelopmentAuthentication,
                )

                result
            },
            onFailure = { error ->
                traceTransportFailure(
                    operation = "create_room",
                    error = error,
                    playerId = request.localPlayerId,
                    durationMillis = elapsedMillisSince(
                        startedAtEpochMillis = startedAtEpochMillis,
                    ),
                )

                rejectedRoomOperation(
                    reason = error.toOnlineFailureReason(
                        fallback = "Falha ao criar sala online remota.",
                    ),
                )
            },
        )
    }

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        val client = apiClient
            ?: return rejectedRoomOperation(
                reason = getUnavailableBackendReason(),
            ).also {
                trace(
                    level = OnlineTraceLevel.WARN,
                    type = OnlineTraceType.TRANSPORT_FAILURE,
                    playerId = request.localPlayerId,
                    attributes = mapOf(
                        "operation" to "join_room",
                        "reason" to getUnavailableBackendReason(),
                    ),
                )
            }

        val startedAtEpochMillis = nowEpochMillis()

        return runCatching {
            val participant = prepareRoomParticipant(
                client = client,
                requestedPlayerId = request.localPlayerId,
            )

            try {
                PreparedRoomOperationResult(
                    participant = participant,
                    result = client.joinRoom(
                        request.copy(
                            localPlayerId = participant.playerId,
                        ),
                    ),
                )
            } finally {
                if (participant.usesDevelopmentAuthentication) {
                    restoreActiveParticipantAuthentication(
                        client = client,
                    )
                }
            }
        }.fold(
            onSuccess = { operationResult ->
                val participant = operationResult.participant
                val result = operationResult.result

                traceRoomOperationResult(
                    operation = "join_room",
                    result = result,
                    playerId = participant.playerId,
                )

                applyRoomOperationResult(
                    result = result,
                    client = client,
                    operation = "join_room",
                    playerId = participant.playerId,
                    anonymousSession = participant.anonymousSession,
                    activateParticipant = !participant.usesDevelopmentAuthentication,
                )

                result
            },
            onFailure = { error ->
                traceTransportFailure(
                    operation = "join_room",
                    error = error,
                    playerId = request.localPlayerId,
                    durationMillis = elapsedMillisSince(
                        startedAtEpochMillis = startedAtEpochMillis,
                    ),
                )

                rejectedRoomOperation(
                    reason = error.toOnlineFailureReason(
                        fallback = "Falha ao entrar na sala online remota.",
                    ),
                )
            },
        )
    }

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        val client = apiClient
            ?: return rejectedAction(
                action = action,
                reason = getUnavailableBackendReason(),
            ).also {
                trace(
                    level = OnlineTraceLevel.WARN,
                    type = OnlineTraceType.ACTION_REJECTED,
                    action = action,
                    snapshotRevision = it.revision,
                    attributes = action.traceAttributes() + mapOf(
                        "reason" to getUnavailableBackendReason(),
                        "source" to "backend_unavailable",
                    ),
                )
            }

        return refreshMutex.withLock {
            val authenticationFailure =
                configureActiveParticipantAuthentication(
                    client = client,
                    playerId = action.playerId,
                )

            if (authenticationFailure != null) {
                val rejectedResult = rejectedAction(
                    action = action,
                    reason = authenticationFailure,
                )

                trace(
                    level = OnlineTraceLevel.WARN,
                    type = OnlineTraceType.ACTION_REJECTED,
                    action = action,
                    snapshotRevision = rejectedResult.revision,
                    attributes = action.traceAttributes() + mapOf(
                        "reason" to authenticationFailure.take(180),
                        "source" to "authentication_failure",
                    ),
                )

                return@withLock rejectedResult
            }

            trace(
                level = OnlineTraceLevel.INFO,
                type = OnlineTraceType.ACTION_SUBMITTED,
                action = action,
                attributes = action.traceAttributes(),
            )

            val startedAtEpochMillis = nowEpochMillis()

            runCatching {
                client.submitAction(action)
            }.fold(
                onSuccess = { result ->
                    val resolvedResult = result.copy(
                        actionId = result.actionId ?: action.actionId,
                    )

                    if (resolvedResult.accepted) {
                        trace(
                            level = OnlineTraceLevel.INFO,
                            type = OnlineTraceType.ACTION_ACCEPTED,
                            action = action,
                            snapshotRevision = resolvedResult.revision,
                            attributes = action.traceAttributes() + mapOf(
                                "durationMillis" to elapsedMillisSince(
                                    startedAtEpochMillis = startedAtEpochMillis,
                                ).toString(),
                            ),
                        )
                    } else {
                        trace(
                            level = OnlineTraceLevel.WARN,
                            type = OnlineTraceType.ACTION_REJECTED,
                            action = action,
                            snapshotRevision = resolvedResult.revision,
                            attributes = action.traceAttributes() + mapOf(
                                "durationMillis" to elapsedMillisSince(
                                    startedAtEpochMillis = startedAtEpochMillis,
                                ).toString(),
                                "reason" to resolvedResult.reason
                                    .orEmpty()
                                    .take(180),
                            ),
                        )
                    }

                    if (
                        resolvedResult.accepted ||
                        shouldRefreshAfterRejectedAction(
                            action = action,
                            result = resolvedResult,
                        )
                    ) {
                        refreshSnapshotsAfterAction(
                            client = client,
                            action = action,
                        )
                    }

                    resolvedResult
                },
                onFailure = { error ->
                    traceTransportFailure(
                        operation = "submit_action",
                        error = error,
                        action = action,
                        durationMillis = elapsedMillisSince(
                            startedAtEpochMillis = startedAtEpochMillis,
                        ),
                    )

                    val rejectedResult = rejectedAction(
                        action = action,
                        reason = error.toOnlineFailureReason(
                            fallback = "Falha ao enviar ação online remota.",
                        ),
                    )

                    trace(
                        level = OnlineTraceLevel.WARN,
                        type = OnlineTraceType.ACTION_REJECTED,
                        action = action,
                        snapshotRevision = rejectedResult.revision,
                        attributes = action.traceAttributes() + mapOf(
                            "reason" to rejectedResult.reason.orEmpty().take(180),
                            "source" to "transport_failure",
                        ),
                    )

                    rejectedResult
                },
            )
        }
    }

    override suspend fun submitTraceBatch(
        batch: OnlineTraceBatchDto,
    ): OnlineTraceBatchResultDto {
        if (batch.entries.isEmpty()) {
            return OnlineTraceBatchResultDto(
                accepted = true,
            )
        }

        val client = apiClient ?: return OnlineTraceBatchResultDto(
            accepted = false,
            reason = getUnavailableBackendReason(),
        )

        return try {
            client.submitTraceBatch(
                batch = batch,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            /*
             * O canal de diagnóstico não pode realimentar o próprio logger
             * nem interferir no fluxo da partida. O uploader manterá o lote
             * pendente para uma tentativa posterior.
             */
            OnlineTraceBatchResultDto(
                accepted = false,
                reason = error.toOnlineFailureReason(
                    fallback = "Falha ao enviar rastreamento online.",
                ),
            )
        }
    }

    override suspend fun leaveRoom() {
        onlineParticipationBindingRepository?.clear()

        stopPolling(
            reason = "leave_room",
        )

        val client = apiClient
        val currentSnapshot = mutableMatchSnapshot.value
        val currentRoom = mutableRoomSnapshot.value
        val localPlayerId = activePlayerId

        if (
            client != null &&
            currentSnapshot != null &&
            currentRoom != null &&
            localPlayerId != null
        ) {
            val leaveAction = createOnlineLeaveRoomAction(
                roomId = currentRoom.roomId,
                matchId = currentSnapshot.matchId,
                playerId = localPlayerId,
                revision = currentSnapshot.revision,
            )

            val authenticationFailure =
                configureActiveParticipantAuthentication(
                    client = client,
                    playerId = localPlayerId,
                )

            if (authenticationFailure == null) {
                trace(
                    level = OnlineTraceLevel.INFO,
                    type = OnlineTraceType.ACTION_SUBMITTED,
                    action = leaveAction,
                    attributes = leaveAction.traceAttributes() + mapOf(
                        "source" to "leave_room",
                    ),
                )

                val startedAtEpochMillis = nowEpochMillis()

                runCatching {
                    client.submitAction(
                        leaveAction,
                    )
                }.onSuccess { result ->
                    trace(
                        level = if (result.accepted) {
                            OnlineTraceLevel.INFO
                        } else {
                            OnlineTraceLevel.WARN
                        },
                        type = if (result.accepted) {
                            OnlineTraceType.ACTION_ACCEPTED
                        } else {
                            OnlineTraceType.ACTION_REJECTED
                        },
                        action = leaveAction,
                        snapshotRevision = result.revision,
                        attributes = leaveAction.traceAttributes() + mapOf(
                            "source" to "leave_room",
                            "durationMillis" to elapsedMillisSince(
                                startedAtEpochMillis = startedAtEpochMillis,
                            ).toString(),
                            "reason" to result.reason.orEmpty().take(180),
                        ),
                    )
                }.onFailure { error ->
                    traceTransportFailure(
                        operation = "leave_room",
                        error = error,
                        action = leaveAction,
                        durationMillis = elapsedMillisSince(
                            startedAtEpochMillis = startedAtEpochMillis,
                        ),
                    )
                }
            } else {
                trace(
                    level = OnlineTraceLevel.WARN,
                    type = OnlineTraceType.ACTION_REJECTED,
                    action = leaveAction,
                    snapshotRevision = currentSnapshot.revision,
                    attributes = leaveAction.traceAttributes() + mapOf(
                        "source" to "authentication_failure",
                        "reason" to authenticationFailure.take(180),
                    ),
                )
            }
        }

        trace(
            level = OnlineTraceLevel.INFO,
            type = OnlineTraceType.ROOM_LEFT,
            roomId = currentRoom?.roomId,
            matchId = currentSnapshot?.matchId,
            attributes = mapOf(
                "hadRoomSnapshot" to (currentRoom != null).toString(),
                "hadMatchSnapshot" to (currentSnapshot != null).toString(),
            ),
        )

        mutableRoomSnapshot.value = null
        mutableMatchSnapshot.value = null
        activePlayerId = null
        activeAnonymousSession = null
        client?.setBearerAccessToken(
            accessToken = null,
        )
        client?.setDevelopmentPlayerId(
            playerId = null,
        )
    }

    private suspend fun applyRoomOperationResult(
        result: OnlineRoomOperationResultDto,
        client: RemoteOnlineApiClient,
        operation: String,
        playerId: String,
        anonymousSession: OnlineAnonymousSessionDto?,
        activateParticipant: Boolean,
    ) {
        val room = result.roomSnapshot ?: return

        mutableRoomSnapshot.value = room

        trace(
            level = OnlineTraceLevel.INFO,
            type = OnlineTraceType.SNAPSHOT_RECEIVED,
            roomId = room.roomId,
            matchId = room.matchId,
            playerId = playerId,
            localSeatIndex = result.localSeatIndex,
            attributes = room.traceAttributes(
                trigger = operation,
            ),
        )

        persistAcceptedAuthenticatedParticipationBinding(
            result = result,
            room = room,
            anonymousSession = anonymousSession,
        )

        if (result.accepted && activateParticipant) {
            activePlayerId = playerId
            activeAnonymousSession = anonymousSession

            val authenticationFailure =
                configureActiveParticipantAuthentication(
                    client = client,
                    playerId = playerId,
                )

            if (authenticationFailure == null) {
                startPolling(
                    roomId = room.roomId,
                    client = client,
                )
            } else {
                trace(
                    level = OnlineTraceLevel.WARN,
                    type = OnlineTraceType.ACTION_REJECTED,
                    roomId = room.roomId,
                    matchId = room.matchId,
                    playerId = playerId,
                    attributes = mapOf(
                        "operation" to operation,
                        "source" to "authentication_failure",
                        "reason" to authenticationFailure.take(180),
                    ),
                )
            }
        }

        val matchId = room.matchId
        if (result.accepted && matchId != null) {
            fetchAndPublishMatchSnapshot(
                client = client,
                roomId = room.roomId,
                matchId = matchId,
                trigger = operation,
                playerId = activePlayerId ?: playerId,
            )
        }
    }

    private fun persistAcceptedAuthenticatedParticipationBinding(
        result: OnlineRoomOperationResultDto,
        room: OnlineRoomSnapshotDto,
        anonymousSession: OnlineAnonymousSessionDto?,
    ) {
        if (!result.accepted) {
            return
        }

        val bindingRepository =
            onlineParticipationBindingRepository
                ?: return

        val authenticatedPlayerId = anonymousSession
            ?.playerId
            ?.takeIf { playerId ->
                playerId.isNotBlank()
            }
            ?: return

        val localSeatIndex = result.localSeatIndex
            ?.takeIf { seatIndex ->
                seatIndex in 0..3
            }
            ?: return

        if (
            room.roomId.isBlank() ||
            room.matchId?.isBlank() == true
        ) {
            return
        }

        bindingRepository.save(
            binding = OnlineParticipationBinding(
                roomId = room.roomId,
                matchId = room.matchId,
                playerId = authenticatedPlayerId,
                localSeatIndex = localSeatIndex,
            ),
        )
    }

    private suspend fun refreshSnapshotsAfterAction(
        client: RemoteOnlineApiClient,
        action: OnlinePlayerActionDto,
    ) {
        refreshSnapshots(
            client = client,
            roomId = action.roomId,
            fallbackMatchId = action.matchId,
            trigger = "action_refresh",
            playerId = action.playerId,
        )
    }

    private fun startPolling(
        roomId: String,
        client: RemoteOnlineApiClient,
    ) {
        if (!pollingPolicy.enabled) {
            return
        }

        if (
            pollingRoomId == roomId &&
            pollingJob?.isActive == true
        ) {
            return
        }

        stopPolling(
            reason = "replaced",
        )

        pollingRoomId = roomId

        trace(
            level = OnlineTraceLevel.INFO,
            type = OnlineTraceType.POLLING_STARTED,
            roomId = roomId,
            attributes = mapOf(
                "intervalMillis" to pollingPolicy.intervalMillis.toString(),
            ),
        )

        pollingJob = repositoryScope.launch {
            while (isActive) {
                delay(pollingPolicy.intervalMillis)

                refreshMutex.withLock {
                    val playerId = activePlayerId

                    val authenticationFailure =
                        configureActiveParticipantAuthentication(
                            client = client,
                            playerId = playerId,
                        )

                    if (authenticationFailure == null) {
                        refreshSnapshots(
                            client = client,
                            roomId = roomId,
                            fallbackMatchId = mutableMatchSnapshot.value?.matchId,
                            trigger = "polling",
                            playerId = playerId,
                        )
                    } else {
                        trace(
                            level = OnlineTraceLevel.WARN,
                            type = OnlineTraceType.POLLING_FAILED,
                            roomId = roomId,
                            matchId = mutableMatchSnapshot.value?.matchId,
                            playerId = playerId,
                            attributes = mapOf(
                                "reason" to authenticationFailure.take(180),
                                "source" to "authentication_failure",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun stopPolling(
        reason: String,
    ) {
        val previousPollingRoomId = pollingRoomId
        val hadActivePolling = pollingJob != null || previousPollingRoomId != null

        pollingJob?.cancel()
        pollingJob = null
        pollingRoomId = null

        if (hadActivePolling) {
            trace(
                level = OnlineTraceLevel.INFO,
                type = OnlineTraceType.POLLING_STOPPED,
                roomId = previousPollingRoomId,
                attributes = mapOf(
                    "reason" to reason,
                ),
            )
        }
    }

    private suspend fun refreshSnapshots(
        client: RemoteOnlineApiClient,
        roomId: String,
        fallbackMatchId: String?,
        trigger: String,
        playerId: String?,
    ) {
        val resolvedPlayerId = playerId ?: activePlayerId

        var latestMatchId = fallbackMatchId

        val roomRequestStartedAtEpochMillis = nowEpochMillis()

        trace(
            level = OnlineTraceLevel.DEBUG,
            type = OnlineTraceType.SNAPSHOT_REQUESTED,
            roomId = roomId,
            matchId = fallbackMatchId,
            playerId = resolvedPlayerId,
            attributes = mapOf(
                "snapshotKind" to "room",
                "trigger" to trigger,
            ),
        )

        runCatching {
            client.fetchRoomSnapshot(roomId)
        }.onSuccess { room ->
            mutableRoomSnapshot.value = room
            latestMatchId = room.matchId ?: latestMatchId

            trace(
                level = OnlineTraceLevel.DEBUG,
                type = OnlineTraceType.SNAPSHOT_RECEIVED,
                roomId = room.roomId,
                matchId = room.matchId ?: latestMatchId,
                playerId = resolvedPlayerId,
                attributes = room.traceAttributes(
                    trigger = trigger,
                ) + mapOf(
                    "durationMillis" to elapsedMillisSince(
                        startedAtEpochMillis = roomRequestStartedAtEpochMillis,
                    ).toString(),
                ),
            )
        }.onFailure { error ->
            traceTransportFailure(
                operation = "fetch_room_snapshot",
                error = error,
                roomId = roomId,
                matchId = fallbackMatchId,
                playerId = resolvedPlayerId,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis = roomRequestStartedAtEpochMillis,
                ),
                trigger = trigger,
            )
        }

        val matchId = latestMatchId ?: return

        fetchAndPublishMatchSnapshot(
            client = client,
            roomId = roomId,
            matchId = matchId,
            trigger = trigger,
            playerId = resolvedPlayerId,
        )
    }

    private suspend fun fetchAndPublishMatchSnapshot(
        client: RemoteOnlineApiClient,
        roomId: String,
        matchId: String,
        trigger: String,
        playerId: String?,
    ) {
        val currentSnapshot = mutableMatchSnapshot.value

        if (
            currentSnapshot == null ||
            currentSnapshot.matchId != matchId
        ) {
            fetchAndPublishLatestMatchSnapshot(
                client = client,
                roomId = roomId,
                matchId = matchId,
                trigger = trigger,
                playerId = playerId,
            )
            return
        }

        fetchAndPublishMatchSnapshotsAfter(
            client = client,
            roomId = roomId,
            matchId = matchId,
            afterRevision = currentSnapshot.revision,
            trigger = trigger,
            playerId = playerId,
        )
    }

    private suspend fun fetchAndPublishLatestMatchSnapshot(
        client: RemoteOnlineApiClient,
        roomId: String,
        matchId: String,
        trigger: String,
        playerId: String?,
    ) {
        val startedAtEpochMillis = nowEpochMillis()

        trace(
            level = OnlineTraceLevel.DEBUG,
            type = OnlineTraceType.SNAPSHOT_REQUESTED,
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            attributes = mapOf(
                "snapshotKind" to "match",
                "trigger" to trigger,
            ),
        )

        try {
            val match = client.fetchMatchSnapshot(matchId)

            trace(
                level = OnlineTraceLevel.DEBUG,
                type = OnlineTraceType.SNAPSHOT_RECEIVED,
                roomId = roomId,
                matchId = match.matchId,
                playerId = playerId,
                snapshotRevision = match.revision,
                attributes = match.traceAttributes(
                    trigger = trigger,
                ) + mapOf(
                    "durationMillis" to elapsedMillisSince(
                        startedAtEpochMillis = startedAtEpochMillis,
                    ).toString(),
                ),
            )

            publishMatchSnapshotIfNewer(
                snapshot = match,
                trigger = trigger,
                playerId = playerId,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }

            traceTransportFailure(
                operation = "fetch_match_snapshot",
                error = error,
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
                trigger = trigger,
            )
        }
    }

    private suspend fun fetchAndPublishMatchSnapshotsAfter(
        client: RemoteOnlineApiClient,
        roomId: String,
        matchId: String,
        afterRevision: Long,
        trigger: String,
        playerId: String?,
    ) {
        val startedAtEpochMillis = nowEpochMillis()

        trace(
            level = OnlineTraceLevel.DEBUG,
            type = OnlineTraceType.SNAPSHOT_REQUESTED,
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            attributes = mapOf(
                "snapshotKind" to "match_updates",
                "trigger" to trigger,
                "afterRevision" to afterRevision.toString(),
            ),
        )

        try {
            val snapshots = client.fetchMatchSnapshotsAfter(
                matchId = matchId,
                afterRevision = afterRevision,
            ).sortedBy { snapshot ->
                snapshot.revision
            }

            val firstRevision = snapshots.firstOrNull()?.revision
            if (
                firstRevision != null &&
                firstRevision != afterRevision + 1L
            ) {
                trace(
                    level = OnlineTraceLevel.WARN,
                    type = OnlineTraceType.INVARIANT_VIOLATION,
                    roomId = roomId,
                    matchId = matchId,
                    playerId = playerId,
                    snapshotRevision = firstRevision,
                    attributes = mapOf(
                        "reason" to "revision_gap_in_match_updates",
                        "afterRevision" to afterRevision.toString(),
                        "firstReceivedRevision" to firstRevision.toString(),
                    ),
                )
            }

            snapshots.forEach { match ->
                trace(
                    level = OnlineTraceLevel.DEBUG,
                    type = OnlineTraceType.SNAPSHOT_RECEIVED,
                    roomId = roomId,
                    matchId = match.matchId,
                    playerId = playerId,
                    snapshotRevision = match.revision,
                    attributes = match.traceAttributes(
                        trigger = trigger,
                    ) + mapOf(
                        "snapshotKind" to "match_updates",
                        "afterRevision" to afterRevision.toString(),
                        "batchSize" to snapshots.size.toString(),
                        "durationMillis" to elapsedMillisSince(
                            startedAtEpochMillis = startedAtEpochMillis,
                        ).toString(),
                    ),
                )
            }

            /*
             * Um lote contínuo é histórico autoritativo recuperável, não
             * desync. Preserve cada revisão no SharedFlow para que o
             * coordenador decida, a partir da dívida visual real, entre
             * replay FIFO, compactação da cauda ou ressincronização dura.
             *
             * O StateFlow ainda converge para a última revisão porque cada
             * publicação atualiza o estado autoritativo mais recente.
             */
            snapshots.forEach { snapshot ->
                publishMatchSnapshotIfNewer(
                    snapshot = snapshot,
                    trigger = if (snapshots.size > 1) {
                        "$trigger:incremental_history_batch"
                    } else {
                        trigger
                    },
                    playerId = playerId,
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }

            traceTransportFailure(
                operation = "fetch_match_updates",
                error = error,
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
                trigger = trigger,
            )
        }
    }

    private fun shouldRefreshAfterRejectedAction(
        action: OnlinePlayerActionDto,
        result: OnlineActionResultDto,
    ): Boolean {
        val serverRevision = result.revision ?: return false

        return serverRevision > action.revision
    }

    private suspend fun publishMatchSnapshotIfNewer(
        snapshot: OnlineMatchSnapshotDto,
        trigger: String,
        playerId: String?,
    ) {
        val currentSnapshot = mutableMatchSnapshot.value

        if (
            currentSnapshot == null ||
            currentSnapshot.matchId != snapshot.matchId ||
            snapshot.revision >= currentSnapshot.revision
        ) {
            mutableMatchSnapshot.value = snapshot
            mutableMatchSnapshotEvents.emit(snapshot)

            trace(
                level = OnlineTraceLevel.INFO,
                type = OnlineTraceType.SNAPSHOT_PUBLISHED,
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = playerId,
                snapshotRevision = snapshot.revision,
                attributes = mapOf(
                    "trigger" to trigger,
                    "previousRevision" to
                            (currentSnapshot?.revision?.toString() ?: "null"),
                ),
            )

            return
        }

        trace(
            level = OnlineTraceLevel.DEBUG,
            type = OnlineTraceType.SNAPSHOT_IGNORED,
            roomId = snapshot.roomId,
            matchId = snapshot.matchId,
            playerId = playerId,
            snapshotRevision = snapshot.revision,
            attributes = mapOf(
                "reason" to if (
                    currentSnapshot.matchId == snapshot.matchId &&
                    snapshot.revision == currentSnapshot.revision
                ) {
                    "duplicate_match_revision"
                } else {
                    "older_match_revision"
                },
                "trigger" to trigger,
                "currentRevision" to currentSnapshot.revision.toString(),
            ),
        )
    }


    private suspend fun prepareRoomParticipant(
        client: RemoteOnlineApiClient,
        requestedPlayerId: String,
    ): PreparedRoomParticipant {
        if (isDevelopmentOnlyPlayerId(requestedPlayerId)) {
            client.setBearerAccessToken(
                accessToken = null,
            )
            client.setDevelopmentPlayerId(
                playerId = requestedPlayerId,
            )

            return PreparedRoomParticipant(
                playerId = requestedPlayerId,
                usesDevelopmentAuthentication = true,
            )
        }

        val sessionRepository = anonymousSessionRepository

        if (sessionRepository == null) {
            client.setBearerAccessToken(
                accessToken = null,
            )
            client.setDevelopmentPlayerId(
                playerId = requestedPlayerId,
            )

            return PreparedRoomParticipant(
                playerId = requestedPlayerId,
                usesDevelopmentAuthentication = false,
            )
        }

        val session = sessionRepository.getOrCreateValidSession {
            client.createAnonymousSession()
        }

        client.setDevelopmentPlayerId(
            playerId = null,
        )
        client.setBearerAccessToken(
            accessToken = session.accessToken,
        )

        return PreparedRoomParticipant(
            playerId = session.playerId,
            anonymousSession = session,
            usesDevelopmentAuthentication = false,
        )
    }

    private fun restoreActiveParticipantAuthentication(
        client: RemoteOnlineApiClient,
    ) {
        val playerId = activePlayerId

        if (playerId == null) {
            client.setBearerAccessToken(
                accessToken = null,
            )
            client.setDevelopmentPlayerId(
                playerId = null,
            )
            return
        }

        configureActiveParticipantAuthentication(
            client = client,
            playerId = playerId,
        )
    }

    private fun configureActiveParticipantAuthentication(
        client: RemoteOnlineApiClient,
        playerId: String?,
    ): String? {
        val resolvedPlayerId = playerId
            ?.trim()
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return "Identidade do participante online não está disponível."

        val sessionRepository = anonymousSessionRepository

        if (sessionRepository == null) {
            client.setBearerAccessToken(
                accessToken = null,
            )
            client.setDevelopmentPlayerId(
                playerId = resolvedPlayerId,
            )
            return null
        }

        val activeSession = activeAnonymousSession

        if (
            activeSession == null ||
            activeSession.playerId != resolvedPlayerId
        ) {
            clearClientAuthentication(
                client = client,
            )

            return "Sessão anônima ativa não está disponível."
        }

        val storedSession = sessionRepository.getValidSessionOrNull()

        if (
            storedSession == null ||
            storedSession.playerId != activeSession.playerId ||
            storedSession.accessToken != activeSession.accessToken
        ) {
            activeAnonymousSession = null

            clearClientAuthentication(
                client = client,
            )

            return "Sessão anônima ativa expirou ou não é mais válida."
        }

        client.setDevelopmentPlayerId(
            playerId = null,
        )
        client.setBearerAccessToken(
            accessToken = storedSession.accessToken,
        )

        return null
    }

    private fun clearClientAuthentication(
        client: RemoteOnlineApiClient,
    ) {
        client.setBearerAccessToken(
            accessToken = null,
        )
        client.setDevelopmentPlayerId(
            playerId = null,
        )
    }

    private fun isDevelopmentOnlyPlayerId(
        playerId: String,
    ): Boolean {
        return playerId == "fake-host" ||
                playerId.startsWith("fake-player-")
    }

    private fun traceRoomOperationResult(
        operation: String,
        result: OnlineRoomOperationResultDto,
        playerId: String,
    ) {
        val room = result.roomSnapshot

        if (result.accepted) {
            trace(
                level = OnlineTraceLevel.INFO,
                type = when (operation) {
                    "create_room" -> OnlineTraceType.ROOM_CREATED
                    else -> OnlineTraceType.ROOM_JOINED
                },
                roomId = room?.roomId,
                matchId = room?.matchId,
                playerId = playerId,
                localSeatIndex = result.localSeatIndex,
                attributes = mapOf(
                    "operation" to operation,
                    "roomStatus" to (room?.status?.name ?: "null"),
                ),
            )

            return
        }

        trace(
            level = OnlineTraceLevel.WARN,
            type = OnlineTraceType.ACTION_REJECTED,
            roomId = room?.roomId,
            matchId = room?.matchId,
            playerId = playerId,
            localSeatIndex = result.localSeatIndex,
            attributes = mapOf(
                "operation" to operation,
                "reason" to result.reason.orEmpty().take(180),
            ),
        )
    }

    private fun traceTransportFailure(
        operation: String,
        error: Throwable,
        roomId: String? = null,
        matchId: String? = null,
        playerId: String? = null,
        action: OnlinePlayerActionDto? = null,
        durationMillis: Long,
        trigger: String? = null,
    ) {
        if (error is CancellationException) {
            return
        }

        val attributes = mutableMapOf(
            "operation" to operation,
            "errorType" to (error::class.simpleName ?: "UnknownError"),
            "durationMillis" to durationMillis.toString(),
        )

        trigger?.let { value ->
            attributes["trigger"] = value
        }

        error.message
            ?.trim()
            ?.takeIf { message -> message.isNotBlank() }
            ?.take(180)
            ?.let { message ->
                attributes["message"] = message
            }

        trace(
            level = OnlineTraceLevel.ERROR,
            type = OnlineTraceType.TRANSPORT_FAILURE,
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            action = action,
            attributes = attributes,
        )

        if (trigger == "polling") {
            trace(
                level = OnlineTraceLevel.WARN,
                type = OnlineTraceType.POLLING_FAILED,
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                attributes = attributes,
            )
        }
    }

    private fun trace(
        level: OnlineTraceLevel,
        type: OnlineTraceType,
        roomId: String? = null,
        matchId: String? = null,
        playerId: String? = null,
        localSeatIndex: Int? = null,
        action: OnlinePlayerActionDto? = null,
        snapshotRevision: Long? = null,
        attributes: Map<String, String> = emptyMap(),
    ) {
        traceLogger.log(
            level = level,
            source = OnlineTraceSource.CLIENT_REPOSITORY,
            type = type,
            context = OnlineTraceContext(
                roomId = roomId
                    ?: action?.roomId
                    ?: mutableRoomSnapshot.value?.roomId,
                matchId = matchId
                    ?: action?.matchId
                    ?: mutableMatchSnapshot.value?.matchId,
                playerId = playerId ?: action?.playerId,
                localSeatIndex = localSeatIndex,
                actionId = action?.actionId,
                actionRevision = action?.revision,
                snapshotRevision = snapshotRevision,
            ),
            attributes = attributes,
        )
    }

    private fun elapsedMillisSince(
        startedAtEpochMillis: Long,
    ): Long {
        return (nowEpochMillis() - startedAtEpochMillis)
            .coerceAtLeast(0L)
    }

    private fun OnlineRoomSnapshotDto.traceAttributes(
        trigger: String,
    ): Map<String, String> {
        return mapOf(
            "snapshotKind" to "room",
            "trigger" to trigger,
            "roomStatus" to status.name,
            "playerCount" to players.size.toString(),
            "updatedAtEpochMillis" to updatedAtEpochMillis.toString(),
        )
    }

    private fun OnlineMatchSnapshotDto.traceAttributes(
        trigger: String,
    ): Map<String, String> {
        return mapOf(
            "snapshotKind" to "match",
            "trigger" to trigger,
            "serverEpochMillis" to
                    (serverEpochMillis?.toString() ?: "null"),
        )
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

    private fun getUnavailableBackendReason(): String {
        if (config.baseUrl == null) {
            return "Backend online remoto sem endpoint configurado."
        }

        return "Backend online remoto indisponível."
    }

    private fun rejectedRoomOperation(
        reason: String,
    ): OnlineRoomOperationResultDto {
        return OnlineRoomOperationResultDto(
            accepted = false,
            reason = reason,
        )
    }

    private fun rejectedAction(
        action: OnlinePlayerActionDto,
        reason: String,
    ): OnlineActionResultDto {
        return OnlineActionResultDto(
            accepted = false,
            actionId = action.actionId,
            revision = mutableMatchSnapshot.value?.revision,
            reason = reason,
        )
    }
}

private fun createApiClientOrNull(
    config: OnlineBackendConfig,
): RemoteOnlineApiClient? {
    if (config.baseUrl == null) {
        return null
    }

    return KtorRemoteOnlineApiClient(
        config = config,
    )
}

private fun Throwable.toOnlineFailureReason(
    fallback: String,
): String {
    val message = message?.trim()

    if (!message.isNullOrBlank()) {
        return "$fallback $message"
    }

    return fallback
}
