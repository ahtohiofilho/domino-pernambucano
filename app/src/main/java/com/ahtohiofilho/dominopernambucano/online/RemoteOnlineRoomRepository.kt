package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
    private val coroutineDispatcher: CoroutineDispatcher =
        Dispatchers.Main.immediate,
    private val traceLogger: OnlineTraceLogger =
        OnlineTraceLogger(),
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val anonymousSessionRepository:
        OnlineAnonymousSessionRepository? = null,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository? = null,
    private val onlineParticipationBindingRepository:
        OnlineParticipationBindingRepository? = null,
) : OnlineRoomRepository {
    private val repositoryScope = CoroutineScope(
        SupervisorJob() + coroutineDispatcher,
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

    private val mutableActiveMatchSessionInvalidationEvents =
        MutableSharedFlow<OnlineActiveMatchSessionInvalidation>(
            extraBufferCapacity = 8,
        )

    private val mutableActiveMatchResourceLossEvents =
        MutableSharedFlow<OnlineActiveMatchResourceLoss>(
            extraBufferCapacity = 8,
        )

    private val mutableActiveMatchParticipationAuthorizationLossEvents =
        MutableSharedFlow<OnlineActiveMatchParticipationAuthorizationLoss>(
            extraBufferCapacity = 8,
        )

    private var pollingJob: Job? = null
    private var pollingRoomId: String? = null
    private var activePlayerId: String? = null
    private var activeSessionCredential: OnlineSessionCredential? = null

private var consecutiveReadRateLimits: Int = 0
private var readRateLimitBackoffMillis: Long? = null
private var consecutiveReadTransportFailures: Int = 0
private var readTransportBackoffMillis: Long? = null

private enum class ActiveReadRefreshOutcome {
    SUCCESS,
    RATE_LIMITED,
    OTHER_FAILURE,
    TERMINAL,
}

    private data class PreparedRoomParticipant(
        val playerId: String,
        val sessionCredential: OnlineSessionCredential? = null,
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

    override val activeMatchSessionInvalidationEvents:
        Flow<OnlineActiveMatchSessionInvalidation> =
        mutableActiveMatchSessionInvalidationEvents.asSharedFlow()

    override val activeMatchResourceLossEvents:
        Flow<OnlineActiveMatchResourceLoss> =
        mutableActiveMatchResourceLossEvents.asSharedFlow()

    override val activeMatchParticipationAuthorizationLossEvents:
        Flow<OnlineActiveMatchParticipationAuthorizationLoss> =
        mutableActiveMatchParticipationAuthorizationLossEvents.asSharedFlow()

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
                    sessionCredential = participant.sessionCredential,
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
                    sessionCredential = participant.sessionCredential,
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

                if (
                    publishActiveMatchSessionInvalidation(
                        roomId = action.roomId,
                        matchId = action.matchId,
                        playerId = action.playerId,
                    )
                ) {
                    stopPolling(
                        reason = "active_session_invalidated",
                    )
                }

                return@withLock rejectedResult
            }

            trace(
                level = OnlineTraceLevel.INFO,
                type = OnlineTraceType.ACTION_SUBMITTED,
                action = action,
                attributes = action.traceAttributes() + mapOf(
                    "submitAttempt" to "1",
                ),
            )

            val startedAtEpochMillis = nowEpochMillis()
            val firstAttempt = submitActionAttempt(
                client = client,
                action = action,
            )
            val firstFailure = firstAttempt.exceptionOrNull()
            val replayAttempted =
                firstFailure != null &&
                    isAmbiguousSubmitActionTransportFailure(
                        error = firstFailure,
                    )

            val finalAttempt = if (replayAttempted) {
                traceTransportFailure(
                    operation = "submit_action",
                    error = requireNotNull(firstFailure),
                    action = action,
                    durationMillis = elapsedMillisSince(
                        startedAtEpochMillis = startedAtEpochMillis,
                    ),
                )

                trace(
                    level = OnlineTraceLevel.INFO,
                    type = OnlineTraceType.ACTION_SUBMITTED,
                    action = action,
                    attributes = action.traceAttributes() + mapOf(
                        "submitAttempt" to "2",
                        "replay" to "true",
                        "replayReason" to
                            "ambiguous_transport_outcome",
                    ),
                )

                submitActionAttempt(
                    client = client,
                    action = action,
                )
            } else {
                firstAttempt
            }

            finalAttempt.fold(
                onSuccess = { result ->
                    handleSubmittedActionResult(
                        client = client,
                        action = action,
                        result = result,
                        startedAtEpochMillis =
                            startedAtEpochMillis,
                        replayAttempted = replayAttempted,
                    )
                },
                onFailure = { error ->
                    handleSubmitActionFailure(
                        client = client,
                        action = action,
                        error = error,
                        startedAtEpochMillis =
                            startedAtEpochMillis,
                        replayAttempted = replayAttempted,
                    )
                },
            )
        }
    }

    private suspend fun submitActionAttempt(
        client: RemoteOnlineApiClient,
        action: OnlinePlayerActionDto,
    ): Result<OnlineActionResultDto> {
        return try {
            Result.success(
                client.submitAction(action),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun isAmbiguousSubmitActionTransportFailure(
        error: Throwable,
    ): Boolean {
        if (error is CancellationException) {
            return false
        }

        if (error is io.ktor.client.plugins.ResponseException) {
            return false
        }

        return error is java.io.IOException ||
                error is io.ktor.client.plugins.HttpRequestTimeoutException
    }

    private suspend fun handleSubmittedActionResult(
        client: RemoteOnlineApiClient,
        action: OnlinePlayerActionDto,
        result: OnlineActionResultDto,
        startedAtEpochMillis: Long,
        replayAttempted: Boolean,
    ): OnlineActionResultDto {
        val resolvedResult = result.copy(
            actionId = result.actionId ?: action.actionId,
        )
        val submitAttempt = if (replayAttempted) {
            "2"
        } else {
            "1"
        }

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
                    "submitAttempt" to submitAttempt,
                    "recoveredAfterAmbiguousTransport" to
                        replayAttempted.toString(),
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
                    "submitAttempt" to submitAttempt,
                    "recoveredAfterAmbiguousTransport" to
                        replayAttempted.toString(),
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

        return resolvedResult
    }

    private suspend fun handleSubmitActionFailure(
        client: RemoteOnlineApiClient,
        action: OnlinePlayerActionDto,
        error: Throwable,
        startedAtEpochMillis: Long,
        replayAttempted: Boolean,
    ): OnlineActionResultDto {
        if (error is CancellationException) {
            throw error
        }

        val remoteSessionRejected =
            isRemoteSessionRejected(error)
        val remoteParticipationForbidden =
            isRemoteParticipationForbidden(error)
        val remoteRateLimited =
            isRemoteRateLimited(error)

        when {
            remoteSessionRejected -> {
                invalidateActiveMatchAfterRemoteSessionRejected(
                    roomId = action.roomId,
                    matchId = action.matchId,
                    playerId = action.playerId,
                    operation = "submit_action",
                    trigger = "action",
                )
            }

            remoteParticipationForbidden -> {
                invalidateActiveMatchAfterRemoteParticipationAuthorizationLoss(
                    roomId = action.roomId,
                    matchId = action.matchId,
                    playerId = action.playerId,
                    reason =
                        OnlineActiveMatchParticipationAuthorizationLossReason
                            .ACTION_IDENTITY_FORBIDDEN,
                    operation = "submit_action",
                    trigger = "action",
                )
            }

            remoteRateLimited -> Unit

            else -> {
                traceTransportFailure(
                    operation = "submit_action",
                    error = error,
                    action = action,
                    durationMillis = elapsedMillisSince(
                        startedAtEpochMillis = startedAtEpochMillis,
                    ),
                )
            }
        }

        val shouldReconcileAmbiguousOutcome =
            replayAttempted &&
                !remoteSessionRejected &&
                !remoteParticipationForbidden

        if (shouldReconcileAmbiguousOutcome) {
            refreshSnapshotsAfterAction(
                client = client,
                action = action,
            )
        }

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
                "source" to when {
                    remoteSessionRejected ->
                        "remote_session_rejected"

                    remoteParticipationForbidden ->
                        "remote_participation_forbidden"

                    remoteRateLimited ->
                        "remote_rate_limited"

                    else ->
                        "transport_failure"
                },
                "submitAttempt" to if (replayAttempted) {
                    "2"
                } else {
                    "1"
                },
                "ambiguousReplayAttempted" to
                    replayAttempted.toString(),
                "authoritativeReadReconciliation" to
                    shouldReconcileAmbiguousOutcome.toString(),
            ),
        )

        return rejectedResult
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
            retryable = true,
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
                retryable = true,
            )
        }
    }

    override suspend fun inspectPendingParticipation(
        binding: OnlineParticipationBinding,
    ): OnlinePendingParticipationRemoteInspection {
        val client = apiClient
            ?: return OnlinePendingParticipationRemoteInspection
                .TemporarilyUnavailable(
                    reason = getUnavailableBackendReason(),
                )

        return refreshMutex.withLock {
            val session = getValidSessionCredentialOrNull()
                ?: return@withLock OnlinePendingParticipationRemoteInspection
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .MISSING_VALID_ANONYMOUS_SESSION,
                    )

            if (session.playerId != binding.playerId) {
                return@withLock OnlinePendingParticipationRemoteInspection
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .ANONYMOUS_SESSION_IDENTITY_MISMATCH,
                    )
            }

            client.setDevelopmentPlayerId(
                playerId = null,
            )
            client.setBearerAccessToken(
                accessToken = session.accessToken,
            )

            try {
                val room = client.fetchRoomSnapshot(
                    roomId = binding.roomId,
                )

                inspectPendingParticipationRoom(
                    binding = binding,
                    room = room,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (
                    error is io.ktor.client.plugins.ClientRequestException &&
                            error.response.status == io.ktor.http.HttpStatusCode.Unauthorized
                ) {
                    return@withLock OnlinePendingParticipationRemoteInspection
                        .RemoteSessionRejected
                }

                if (
                    error is io.ktor.client.plugins.ClientRequestException &&
                            error.response.status == io.ktor.http.HttpStatusCode.NotFound
                ) {
                    return@withLock OnlinePendingParticipationRemoteInspection
                        .NoLongerRecoverable(
                            reason = OnlinePendingParticipationRemoteInvalidReason
                                .ROOM_NOT_FOUND,
                        )
                }

                OnlinePendingParticipationRemoteInspection
                    .TemporarilyUnavailable(
                        reason = error.toOnlineFailureReason(
                            fallback =
                                "Falha ao consultar participação pendente online.",
                        ),
                    )
            } finally {
                restoreActiveParticipantAuthentication(
                    client = client,
                )
            }
        }
    }

    override suspend fun preparePendingParticipationMatchResume(
        binding: OnlineParticipationBinding,
    ): OnlinePendingParticipationMatchResumePreparation {
        val client = apiClient
            ?: return OnlinePendingParticipationMatchResumePreparation
                .TemporarilyUnavailable(
                    reason = getUnavailableBackendReason(),
                )

        return refreshMutex.withLock {
            val session = getValidSessionCredentialOrNull()
                ?: return@withLock OnlinePendingParticipationMatchResumePreparation
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .MISSING_VALID_ANONYMOUS_SESSION,
                    )

            if (session.playerId != binding.playerId) {
                return@withLock OnlinePendingParticipationMatchResumePreparation
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .ANONYMOUS_SESSION_IDENTITY_MISMATCH,
                    )
            }

            client.setDevelopmentPlayerId(
                playerId = null,
            )
            client.setBearerAccessToken(
                accessToken = session.accessToken,
            )

            var remoteNotFoundReason =
                OnlinePendingParticipationMatchResumeInvalidReason
                    .ROOM_NOT_FOUND

            try {
                val room = client.fetchRoomSnapshot(
                    roomId = binding.roomId,
                )

                val inspection = inspectPendingParticipationRoom(
                    binding = binding,
                    room = room,
                )

                if (
                    inspection is OnlinePendingParticipationRemoteInspection
                        .NoLongerRecoverable
                ) {
                    return@withLock OnlinePendingParticipationMatchResumePreparation
                        .NoLongerRecoverable(
                            reason = inspection.reason.toMatchResumeInvalidReason(),
                        )
                }

                val recoverableRoom = (
                    inspection as OnlinePendingParticipationRemoteInspection
                        .Recoverable
                ).roomSnapshot

                if (recoverableRoom.status != OnlineRoomStatusDto.IN_MATCH) {
                    return@withLock OnlinePendingParticipationMatchResumePreparation
                        .WaitingForPlayers(
                            binding = binding,
                            roomSnapshot = recoverableRoom,
                        )
                }

                val matchId = recoverableRoom.matchId
                    ?.takeIf { value ->
                        value.isNotBlank()
                    }
                    ?: return@withLock OnlinePendingParticipationMatchResumePreparation
                        .NoLongerRecoverable(
                            reason =
                                OnlinePendingParticipationMatchResumeInvalidReason
                                    .MISSING_MATCH_ID,
                        )

                remoteNotFoundReason =
                    OnlinePendingParticipationMatchResumeInvalidReason
                        .MATCH_NOT_FOUND

                val match = client.fetchMatchSnapshot(
                    matchId = matchId,
                )

                if (match.roomId != binding.roomId) {
                    return@withLock OnlinePendingParticipationMatchResumePreparation
                        .NoLongerRecoverable(
                            reason =
                                OnlinePendingParticipationMatchResumeInvalidReason
                                    .MATCH_SNAPSHOT_ROOM_ID_MISMATCH,
                        )
                }

                if (match.matchId != matchId) {
                    return@withLock OnlinePendingParticipationMatchResumePreparation
                        .NoLongerRecoverable(
                            reason =
                                OnlinePendingParticipationMatchResumeInvalidReason
                                    .MATCH_SNAPSHOT_MATCH_ID_MISMATCH,
                        )
                }

                OnlinePendingParticipationMatchResumePreparation.Ready(
                    binding = binding,
                    roomSnapshot = recoverableRoom,
                    matchSnapshot = match,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (
                    error is io.ktor.client.plugins.ClientRequestException &&
                            error.response.status == io.ktor.http.HttpStatusCode.Unauthorized
                ) {
                    return@withLock OnlinePendingParticipationMatchResumePreparation
                        .RemoteSessionRejected
                }

                if (
                    error is io.ktor.client.plugins.ClientRequestException &&
                            error.response.status == io.ktor.http.HttpStatusCode.NotFound
                ) {
                    return@withLock OnlinePendingParticipationMatchResumePreparation
                        .NoLongerRecoverable(
                            reason = remoteNotFoundReason,
                        )
                }

                OnlinePendingParticipationMatchResumePreparation
                    .TemporarilyUnavailable(
                        reason = error.toOnlineFailureReason(
                            fallback =
                                "Falha ao preparar retomada de participação pendente online.",
                        ),
                    )
            } finally {
                restoreActiveParticipantAuthentication(
                    client = client,
                )
            }
        }
    }

    override suspend fun activatePendingParticipationRoomResume(
        preparation:
            OnlinePendingParticipationMatchResumePreparation
                .WaitingForPlayers,
    ): OnlinePendingParticipationMatchResumeActivation {
        return activatePendingParticipationResume(
            binding = preparation.binding,
            roomSnapshot = preparation.roomSnapshot,
            matchSnapshot = null,
            expectedRoomStatus = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            traceTrigger =
                "pending_participation_room_resume_activation",
        )
    }

    override suspend fun activatePendingParticipationMatchResume(
        preparation: OnlinePendingParticipationMatchResumePreparation.Ready,
    ): OnlinePendingParticipationMatchResumeActivation {
        return activatePendingParticipationResume(
            binding = preparation.binding,
            roomSnapshot = preparation.roomSnapshot,
            matchSnapshot = preparation.matchSnapshot,
            expectedRoomStatus = OnlineRoomStatusDto.IN_MATCH,
            traceTrigger = "pending_participation_resume_activation",
        )
    }

    private suspend fun activatePendingParticipationResume(
        binding: OnlineParticipationBinding,
        roomSnapshot: OnlineRoomSnapshotDto,
        matchSnapshot: OnlineMatchSnapshotDto?,
        expectedRoomStatus: OnlineRoomStatusDto,
        traceTrigger: String,
    ): OnlinePendingParticipationMatchResumeActivation {
        val client = apiClient
            ?: return OnlinePendingParticipationMatchResumeActivation
                .TemporarilyUnavailable(
                    reason = getUnavailableBackendReason(),
                )

        return refreshMutex.withLock {
            val session = getValidSessionCredentialOrNull()
                ?: return@withLock OnlinePendingParticipationMatchResumeActivation
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .MISSING_VALID_ANONYMOUS_SESSION,
                    )

            if (session.playerId != binding.playerId) {
                return@withLock OnlinePendingParticipationMatchResumeActivation
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .ANONYMOUS_SESSION_IDENTITY_MISMATCH,
                    )
            }

            val preparedPlayer = roomSnapshot.players.firstOrNull { player ->
                player.playerId == binding.playerId
            }

            val preparationMatchesBinding =
                roomSnapshot.roomId == binding.roomId &&
                        roomSnapshot.status == expectedRoomStatus &&
                        preparedPlayer?.seatIndex == binding.localSeatIndex &&
                        (
                                matchSnapshot == null ||
                                        (
                                                matchSnapshot.roomId == binding.roomId &&
                                                        roomSnapshot.matchId ==
                                                        matchSnapshot.matchId
                                                )
                                )

            if (!preparationMatchesBinding) {
                return@withLock OnlinePendingParticipationMatchResumeActivation
                    .TemporarilyUnavailable(
                        reason =
                            "A preparação da retomada não corresponde à participação salva.",
                    )
            }

            val previousActivePlayerId = activePlayerId
            val previousActiveSessionCredential = activeSessionCredential

            activePlayerId = session.playerId
            activeSessionCredential = session

            val authenticationFailure =
                configureActiveParticipantAuthentication(
                    client = client,
                    playerId = session.playerId,
                )

            if (authenticationFailure != null) {
                activePlayerId = previousActivePlayerId
                activeSessionCredential = previousActiveSessionCredential

                restoreActiveParticipantAuthentication(
                    client = client,
                )

                return@withLock OnlinePendingParticipationMatchResumeActivation
                    .TemporarilyUnavailable(
                        reason = authenticationFailure,
                    )
            }

            try {
                mutableRoomSnapshot.value = roomSnapshot

                if (matchSnapshot == null) {
                    mutableMatchSnapshot.value = null

                    trace(
                        level = OnlineTraceLevel.INFO,
                        type = OnlineTraceType.SNAPSHOT_RECEIVED,
                        roomId = roomSnapshot.roomId,
                        matchId = roomSnapshot.matchId,
                        playerId = session.playerId,
                        localSeatIndex = binding.localSeatIndex,
                        attributes = roomSnapshot.traceAttributes(
                            trigger = traceTrigger,
                        ),
                    )
                } else {
                    publishMatchSnapshotIfNewer(
                        snapshot = matchSnapshot,
                        trigger = traceTrigger,
                        playerId = session.playerId,
                    )
                }
            } catch (error: CancellationException) {
                activePlayerId = previousActivePlayerId
                activeSessionCredential = previousActiveSessionCredential

                restoreActiveParticipantAuthentication(
                    client = client,
                )

                throw error
            } catch (error: Throwable) {
                activePlayerId = previousActivePlayerId
                activeSessionCredential = previousActiveSessionCredential

                restoreActiveParticipantAuthentication(
                    client = client,
                )

                return@withLock OnlinePendingParticipationMatchResumeActivation
                    .TemporarilyUnavailable(
                        reason = error.toOnlineFailureReason(
                            fallback =
                                "Falha ao ativar retomada de participação pendente online.",
                        ),
                    )
            }

            startPolling(
                roomId = roomSnapshot.roomId,
                client = client,
            )

            OnlinePendingParticipationMatchResumeActivation.Activated
        }
    }

    override suspend fun activatePublicRankedMatch(
        matchId: String,
        localSeatIndex: Int,
    ): OnlinePublicRankedMatchActivation {
        val normalizedMatchId = matchId.trim()

        if (
            normalizedMatchId.isBlank() ||
            localSeatIndex !in 0..3
        ) {
            return OnlinePublicRankedMatchActivation.Failure(
                kind =
                    OnlinePublicRankedMatchActivationFailureKind
                        .INVALID_MATCH,
            )
        }

        val client = apiClient
            ?: return OnlinePublicRankedMatchActivation.Failure(
                kind =
                    OnlinePublicRankedMatchActivationFailureKind
                        .UNAVAILABLE,
            )

        return refreshMutex.withLock {
            val session = getValidSessionCredentialOrNull()
                ?: return@withLock OnlinePublicRankedMatchActivation.Failure(
                        kind =
                            OnlinePublicRankedMatchActivationFailureKind
                                .AUTHENTICATION_REQUIRED,
                    )

            if (
                session.sessionKind != OnlineSessionKind.ACCOUNT ||
                session.accountId.isNullOrBlank()
            ) {
                return@withLock OnlinePublicRankedMatchActivation.Failure(
                        kind =
                            OnlinePublicRankedMatchActivationFailureKind
                                .ACCOUNT_REQUIRED,
                    )
            }

            client.setDevelopmentPlayerId(
                playerId = null,
            )
            client.setBearerAccessToken(
                accessToken = session.accessToken,
            )

            try {
                val matchSnapshot = client.fetchMatchSnapshot(
                    matchId = normalizedMatchId,
                )

                if (
                    matchSnapshot.matchId != normalizedMatchId ||
                    matchSnapshot.roomId.isBlank() ||
                    matchSnapshot.gameState.players.size != 4 ||
                    localSeatIndex !in
                        matchSnapshot.gameState.players.indices
                ) {
                    return@withLock OnlinePublicRankedMatchActivation.Failure(
                            kind =
                                OnlinePublicRankedMatchActivationFailureKind
                                    .INVALID_MATCH,
                        )
                }

                val roomSnapshot = client.fetchRoomSnapshot(
                    roomId = matchSnapshot.roomId,
                )

                val localParticipant =
                    roomSnapshot.players.singleOrNull { player ->
                        player.seatIndex == localSeatIndex
                    }

                if (
                    roomSnapshot.roomId != matchSnapshot.roomId ||
                    roomSnapshot.matchId != normalizedMatchId ||
                    roomSnapshot.status != OnlineRoomStatusDto.IN_MATCH ||
                    localParticipant?.playerId != session.playerId
                ) {
                    return@withLock OnlinePublicRankedMatchActivation.Failure(
                            kind =
                                OnlinePublicRankedMatchActivationFailureKind
                                    .INVALID_MATCH,
                        )
                }

                onlineParticipationBindingRepository?.save(
                    binding = OnlineParticipationBinding(
                        roomId = roomSnapshot.roomId,
                        matchId = normalizedMatchId,
                        playerId = session.playerId,
                        localSeatIndex = localSeatIndex,
                    ),
                )

                activePlayerId = session.playerId
                activeSessionCredential = session
                mutableRoomSnapshot.value = roomSnapshot

                publishMatchSnapshotIfNewer(
                    snapshot = matchSnapshot,
                    trigger = "public_ranked_match_activation",
                    playerId = session.playerId,
                )

                startPolling(
                    roomId = roomSnapshot.roomId,
                    client = client,
                )

                OnlinePublicRankedMatchActivation.Ready(
                    playerId = session.playerId,
                    roomId = roomSnapshot.roomId,
                    matchId = normalizedMatchId,
                    localSeatIndex = localSeatIndex,
                    initialSnapshot = matchSnapshot,
                )
            } catch (error: CancellationException) {
                restoreActiveParticipantAuthentication(
                    client = client,
                )
                throw error
            } catch (error: Throwable) {
                restoreActiveParticipantAuthentication(
                    client = client,
                )

                val failureKind = if (
                    error is io.ktor.client.plugins.ClientRequestException &&
                    error.response.status ==
                        io.ktor.http.HttpStatusCode.Unauthorized
                ) {
                    OnlinePublicRankedMatchActivationFailureKind
                        .SESSION_REJECTED
                } else {
                    OnlinePublicRankedMatchActivationFailureKind
                        .UNAVAILABLE
                }

                OnlinePublicRankedMatchActivation.Failure(
                    kind = failureKind,
                )
            }
        }
    }

    override fun releaseCompletedMatchLocally() {
        val client = apiClient
        val currentSnapshot = mutableMatchSnapshot.value
        val currentRoom = mutableRoomSnapshot.value

        onlineParticipationBindingRepository?.clear()

        stopPolling(
            reason = "completed_match_local_release",
        )

        trace(
            level = OnlineTraceLevel.INFO,
            type = OnlineTraceType.ROOM_LEFT,
            roomId = currentRoom?.roomId,
            matchId = currentSnapshot?.matchId,
            attributes = mapOf(
                "source" to "completed_match_local_release",
                "remoteActionSubmitted" to "false",
                "hadRoomSnapshot" to (currentRoom != null).toString(),
                "hadMatchSnapshot" to (currentSnapshot != null).toString(),
            ),
        )

        mutableRoomSnapshot.value = null
        mutableMatchSnapshot.value = null
        activePlayerId = null
        activeSessionCredential = null
        client?.setBearerAccessToken(
            accessToken = null,
        )
        client?.setDevelopmentPlayerId(
            playerId = null,
        )
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
        activeSessionCredential = null
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
        sessionCredential: OnlineSessionCredential?,
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
            sessionCredential = sessionCredential,
        )

        if (result.accepted && activateParticipant) {
            activePlayerId = playerId
            activeSessionCredential = sessionCredential

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
        sessionCredential: OnlineSessionCredential?,
    ) {
        if (!result.accepted) {
            return
        }

        val bindingRepository =
            onlineParticipationBindingRepository
                ?: return

        val authenticatedPlayerId = sessionCredential
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

    private fun inspectPendingParticipationRoom(
        binding: OnlineParticipationBinding,
        room: OnlineRoomSnapshotDto,
    ): OnlinePendingParticipationRemoteInspection {
        if (room.roomId != binding.roomId) {
            return OnlinePendingParticipationRemoteInspection
                .NoLongerRecoverable(
                    reason = OnlinePendingParticipationRemoteInvalidReason
                        .ROOM_ID_MISMATCH,
                )
        }

        when (room.status) {
            OnlineRoomStatusDto.CLOSED -> {
                return OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .ROOM_CLOSED,
                    )
            }

            OnlineRoomStatusDto.FINISHED -> {
                return OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .ROOM_FINISHED,
                    )
            }

            OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            OnlineRoomStatusDto.IN_MATCH -> Unit
        }

        val localPlayer = room.players.firstOrNull { player ->
            player.playerId == binding.playerId
        } ?: return OnlinePendingParticipationRemoteInspection
            .NoLongerRecoverable(
                reason = OnlinePendingParticipationRemoteInvalidReason
                    .PLAYER_NOT_FOUND,
            )

        if (localPlayer.seatIndex != binding.localSeatIndex) {
            return OnlinePendingParticipationRemoteInspection
                .NoLongerRecoverable(
                    reason = OnlinePendingParticipationRemoteInvalidReason
                        .LOCAL_SEAT_MISMATCH,
                )
        }

        if (
            binding.matchId != null &&
            room.matchId != binding.matchId
        ) {
            return OnlinePendingParticipationRemoteInspection
                .NoLongerRecoverable(
                    reason = OnlinePendingParticipationRemoteInvalidReason
                        .MATCH_ID_MISMATCH,
                )
        }

        return OnlinePendingParticipationRemoteInspection
            .Recoverable(
                roomSnapshot = room,
            )
    }


    private fun OnlinePendingParticipationRemoteInvalidReason
        .toMatchResumeInvalidReason():
        OnlinePendingParticipationMatchResumeInvalidReason {
        return when (this) {
            OnlinePendingParticipationRemoteInvalidReason.ROOM_NOT_FOUND -> {
                OnlinePendingParticipationMatchResumeInvalidReason
                    .ROOM_NOT_FOUND
            }

            OnlinePendingParticipationRemoteInvalidReason.ROOM_ID_MISMATCH -> {
                OnlinePendingParticipationMatchResumeInvalidReason
                    .ROOM_ID_MISMATCH
            }

            OnlinePendingParticipationRemoteInvalidReason.ROOM_CLOSED -> {
                OnlinePendingParticipationMatchResumeInvalidReason
                    .ROOM_CLOSED
            }

            OnlinePendingParticipationRemoteInvalidReason.ROOM_FINISHED -> {
                OnlinePendingParticipationMatchResumeInvalidReason
                    .ROOM_FINISHED
            }

            OnlinePendingParticipationRemoteInvalidReason.PLAYER_NOT_FOUND -> {
                OnlinePendingParticipationMatchResumeInvalidReason
                    .PLAYER_NOT_FOUND
            }

            OnlinePendingParticipationRemoteInvalidReason.LOCAL_SEAT_MISMATCH -> {
                OnlinePendingParticipationMatchResumeInvalidReason
                    .LOCAL_SEAT_MISMATCH
            }

            OnlinePendingParticipationRemoteInvalidReason.MATCH_ID_MISMATCH -> {
                OnlinePendingParticipationMatchResumeInvalidReason
                    .MATCH_ID_MISMATCH
            }
        }
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

        resetReadRateLimitBackoff()
        resetReadTransportBackoff()
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
                delayBeforePollingRefresh()

                refreshMutex.withLock {
                    val playerId = activePlayerId

                    val authenticationFailure =
                        configureActiveParticipantAuthentication(
                            client = client,
                            playerId = playerId,
                        )

                    if (authenticationFailure == null) {
                        val refreshedRoomStatus = refreshSnapshots(
                            client = client,
                            roomId = roomId,
                            fallbackMatchId = mutableMatchSnapshot.value?.matchId,
                            trigger = "polling",
                            playerId = playerId,
                        )

                        if (
                            refreshedRoomStatus ==
                            OnlineRoomStatusDto.FINISHED
                        ) {
                            stopPolling(
                                reason = "room_finished",
                            )
                            return@launch
                        }
                    } else {
                        val activeMatchId =
                            mutableMatchSnapshot.value?.matchId
                                ?: mutableRoomSnapshot.value?.matchId

                        trace(
                            level = OnlineTraceLevel.WARN,
                            type = OnlineTraceType.POLLING_FAILED,
                            roomId = roomId,
                            matchId = activeMatchId,
                            playerId = playerId,
                            attributes = mapOf(
                                "reason" to authenticationFailure.take(180),
                                "source" to "authentication_failure",
                            ),
                        )

                        if (
                            publishActiveMatchSessionInvalidation(
                                roomId = roomId,
                                matchId = activeMatchId,
                                playerId = playerId,
                            )
                        ) {
                            stopPolling(
                                reason = "active_session_invalidated",
                            )
                            return@launch
                        }
                    }
                }
            }
        }
    }

private suspend fun delayBeforePollingRefresh() {
    val delayMillis =
        pollingPolicy.calculateEffectivePollingDelayMillis(
            rateLimitBackoffMillis = readRateLimitBackoffMillis,
            transportBackoffMillis = readTransportBackoffMillis,
        )

    delay(delayMillis)
}

private fun isRemoteRateLimited(
    error: Throwable,
): Boolean {
    return error is io.ktor.client.plugins.ClientRequestException &&
            error.response.status ==
            io.ktor.http.HttpStatusCode.TooManyRequests
}

private fun handleRemoteReadRateLimit(
    error: Throwable,
    roomId: String?,
    matchId: String?,
    playerId: String?,
    operation: String,
    trigger: String,
    durationMillis: Long,
): Boolean {
    if (!isRemoteRateLimited(error)) {
        return false
    }

    consecutiveReadRateLimits =
        (consecutiveReadRateLimits + 1)
            .coerceAtMost(30)

    val retryAfterMillis =
        retryAfterMillisOrNull(
            error = error,
        )

    val backoffMillis =
        pollingPolicy.calculateRateLimitBackoffMillis(
            consecutiveRateLimits = consecutiveReadRateLimits,
            retryAfterMillis = retryAfterMillis,
        )

    readRateLimitBackoffMillis = backoffMillis

    val attributes = mapOf(
        "operation" to operation,
        "trigger" to trigger,
        "source" to "remote_rate_limited",
        "reason" to "remote_http_429",
        "consecutiveRateLimits" to
                consecutiveReadRateLimits.toString(),
        "retryAfterMillis" to
                (retryAfterMillis?.toString() ?: "null"),
        "backoffMillis" to backoffMillis.toString(),
        "durationMillis" to durationMillis.toString(),
    )

    trace(
        level = OnlineTraceLevel.WARN,
        type = OnlineTraceType.TRANSPORT_FAILURE,
        roomId = roomId,
        matchId = matchId,
        playerId = playerId,
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

    return true
}

private fun isGenericNoResponseReadTransportFailure(
    error: Throwable,
): Boolean {
    if (error is CancellationException) {
        return false
    }

    if (error is io.ktor.client.plugins.ResponseException) {
        return false
    }

    return error is java.io.IOException ||
            error is io.ktor.client.plugins.HttpRequestTimeoutException
}

private fun handleRemoteReadTransportBackoff(
    error: Throwable,
    roomId: String?,
    matchId: String?,
    playerId: String?,
    operation: String,
    trigger: String,
    durationMillis: Long,
): Boolean {
    if (!isGenericNoResponseReadTransportFailure(error)) {
        return false
    }

    consecutiveReadTransportFailures =
        (consecutiveReadTransportFailures + 1)
            .coerceAtMost(30)

    val backoffMillis =
        pollingPolicy.calculateTransportBackoffMillis(
            consecutiveTransportFailures =
                consecutiveReadTransportFailures,
        )

    readTransportBackoffMillis = backoffMillis

    val attributes = mapOf(
        "operation" to operation,
        "trigger" to trigger,
        "source" to "remote_transport_backoff",
        "reason" to "remote_no_response_transport_failure",
        "consecutiveTransportFailures" to
                consecutiveReadTransportFailures.toString(),
        "backoffMillis" to backoffMillis.toString(),
        "durationMillis" to durationMillis.toString(),
        "failureType" to
                (error::class.simpleName ?: "Throwable").take(120),
    )

    trace(
        level = OnlineTraceLevel.WARN,
        type = OnlineTraceType.TRANSPORT_FAILURE,
        roomId = roomId,
        matchId = matchId,
        playerId = playerId,
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

    return true
}

private fun retryAfterMillisOrNull(
    error: Throwable,
): Long? {
    val response = (
        error as? io.ktor.client.plugins.ClientRequestException
    )?.response ?: return null

    val rawValue = response.headers[
        io.ktor.http.HttpHeaders.RetryAfter
    ]
        ?.trim()
        ?.takeIf { value ->
            value.isNotBlank()
        }
        ?: return null

    val seconds = rawValue
        .toLongOrNull()
        ?.takeIf { value ->
            value >= 0L
        }
        ?: return null

    return seconds
        .coerceAtMost(Long.MAX_VALUE / 1_000L) *
            1_000L
}

private fun resetReadRateLimitBackoff() {
    consecutiveReadRateLimits = 0
    readRateLimitBackoffMillis = null
}

private fun resetReadTransportBackoff() {
    consecutiveReadTransportFailures = 0
    readTransportBackoffMillis = null
}

    private fun isRemoteSessionRejected(
        error: Throwable,
    ): Boolean {
        return error is io.ktor.client.plugins.ClientRequestException &&
                error.response.status ==
                io.ktor.http.HttpStatusCode.Unauthorized
    }

    private fun isRemoteParticipationForbidden(
        error: Throwable,
    ): Boolean {
        return error is io.ktor.client.plugins.ClientRequestException &&
                error.response.status ==
                io.ktor.http.HttpStatusCode.Forbidden
    }

    private fun isRemoteResourceNotFound(
        error: Throwable,
    ): Boolean {
        return error is io.ktor.client.plugins.ClientRequestException &&
                error.response.status ==
                io.ktor.http.HttpStatusCode.NotFound
    }

    private fun invalidateActiveMatchAfterRemoteSessionRejected(
        roomId: String?,
        matchId: String?,
        playerId: String?,
        operation: String,
        trigger: String,
    ): Boolean {
        trace(
            level = OnlineTraceLevel.WARN,
            type = OnlineTraceType.POLLING_FAILED,
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            attributes = mapOf(
                "operation" to operation,
                "trigger" to trigger,
                "source" to "remote_session_rejected",
                "reason" to "remote_http_401",
            ),
        )

        val published = publishActiveMatchSessionInvalidation(
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
        )

        if (published) {
            stopPolling(
                reason = "active_session_invalidated",
            )
        }

        return published
    }

    private fun publishActiveMatchSessionInvalidation(
        roomId: String?,
        matchId: String?,
        playerId: String?,
    ): Boolean {
        val resolvedRoomId = roomId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        val resolvedMatchId = matchId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        val resolvedPlayerId = playerId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        mutableActiveMatchSessionInvalidationEvents.tryEmit(
            OnlineActiveMatchSessionInvalidation(
                roomId = resolvedRoomId,
                matchId = resolvedMatchId,
                playerId = resolvedPlayerId,
            ),
        )

        return true
    }

    private fun invalidateActiveMatchAfterRemoteParticipationAuthorizationLoss(
        roomId: String?,
        matchId: String?,
        playerId: String?,
        reason: OnlineActiveMatchParticipationAuthorizationLossReason,
        operation: String,
        trigger: String,
    ): Boolean {
        trace(
            level = OnlineTraceLevel.WARN,
            type = OnlineTraceType.POLLING_FAILED,
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            attributes = mapOf(
                "operation" to operation,
                "trigger" to trigger,
                "source" to "remote_participation_forbidden",
                "reason" to "remote_http_403",
                "authorizationLossReason" to reason.name,
            ),
        )

        val published = publishActiveMatchParticipationAuthorizationLoss(
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            reason = reason,
        )

        if (published) {
            stopPolling(
                reason = "active_participation_forbidden",
            )
        }

        return published
    }

    private fun publishActiveMatchParticipationAuthorizationLoss(
        roomId: String?,
        matchId: String?,
        playerId: String?,
        reason: OnlineActiveMatchParticipationAuthorizationLossReason,
    ): Boolean {
        val resolvedRoomId = roomId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        val resolvedMatchId = matchId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        val resolvedPlayerId = playerId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        mutableActiveMatchParticipationAuthorizationLossEvents.tryEmit(
            OnlineActiveMatchParticipationAuthorizationLoss(
                roomId = resolvedRoomId,
                matchId = resolvedMatchId,
                playerId = resolvedPlayerId,
                reason = reason,
            ),
        )

        return true
    }

    private fun invalidateActiveMatchAfterRemoteResourceLoss(
        roomId: String?,
        matchId: String?,
        playerId: String?,
        reason: OnlineActiveMatchResourceLossReason,
        operation: String,
        trigger: String,
    ): Boolean {
        trace(
            level = OnlineTraceLevel.WARN,
            type = OnlineTraceType.POLLING_FAILED,
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            attributes = mapOf(
                "operation" to operation,
                "trigger" to trigger,
                "source" to "remote_resource_not_found",
                "reason" to "remote_http_404",
                "resourceLossReason" to reason.name,
            ),
        )

        val published = publishActiveMatchResourceLoss(
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            reason = reason,
        )

        if (published) {
            stopPolling(
                reason = "active_resource_lost",
            )
        }

        return published
    }

    private fun publishActiveMatchResourceLoss(
        roomId: String?,
        matchId: String?,
        playerId: String?,
        reason: OnlineActiveMatchResourceLossReason,
    ): Boolean {
        val resolvedRoomId = roomId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        val resolvedMatchId = matchId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        val resolvedPlayerId = playerId
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return false

        mutableActiveMatchResourceLossEvents.tryEmit(
            OnlineActiveMatchResourceLoss(
                roomId = resolvedRoomId,
                matchId = resolvedMatchId,
                playerId = resolvedPlayerId,
                reason = reason,
            ),
        )

        return true
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
): OnlineRoomStatusDto? {
    val resolvedPlayerId = playerId ?: activePlayerId

    var latestMatchId =
        fallbackMatchId
            ?: mutableMatchSnapshot.value?.matchId
            ?: mutableRoomSnapshot.value?.matchId
    var latestRoomStatus: OnlineRoomStatusDto? = null
    var roomReadSucceeded = false

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

    try {
        val room = client.fetchRoomSnapshot(roomId)

        mutableRoomSnapshot.value = room
        latestRoomStatus = room.status
        latestMatchId = room.matchId ?: latestMatchId
        roomReadSucceeded = true

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
                    startedAtEpochMillis =
                        roomRequestStartedAtEpochMillis,
                ).toString(),
            ),
        )
    } catch (error: Throwable) {
        if (error is CancellationException) {
            throw error
        }

        if (isRemoteSessionRejected(error)) {
            invalidateActiveMatchAfterRemoteSessionRejected(
                roomId = roomId,
                matchId = latestMatchId,
                playerId = resolvedPlayerId,
                operation = "fetch_room_snapshot",
                trigger = trigger,
            )
            return null
        }

        if (isRemoteParticipationForbidden(error)) {
            invalidateActiveMatchAfterRemoteParticipationAuthorizationLoss(
                roomId = roomId,
                matchId = latestMatchId,
                playerId = resolvedPlayerId,
                reason =
                    OnlineActiveMatchParticipationAuthorizationLossReason
                        .ROOM_PARTICIPATION_FORBIDDEN,
                operation = "fetch_room_snapshot",
                trigger = trigger,
            )
            return null
        }

        if (isRemoteResourceNotFound(error)) {
            invalidateActiveMatchAfterRemoteResourceLoss(
                roomId = roomId,
                matchId = latestMatchId,
                playerId = resolvedPlayerId,
                reason =
                    OnlineActiveMatchResourceLossReason.ROOM_NOT_FOUND,
                operation = "fetch_room_snapshot",
                trigger = trigger,
            )
            return null
        }

        if (
            handleRemoteReadRateLimit(
                error = error,
                roomId = roomId,
                matchId = latestMatchId,
                playerId = resolvedPlayerId,
                operation = "fetch_room_snapshot",
                trigger = trigger,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis =
                        roomRequestStartedAtEpochMillis,
                ),
            )
        ) {
            return null
        }

        if (
            handleRemoteReadTransportBackoff(
                error = error,
                roomId = roomId,
                matchId = latestMatchId,
                playerId = resolvedPlayerId,
                operation = "fetch_room_snapshot",
                trigger = trigger,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis =
                        roomRequestStartedAtEpochMillis,
                ),
            )
        ) {
            return null
        }

        traceTransportFailure(
            operation = "fetch_room_snapshot",
            error = error,
            roomId = roomId,
            matchId = latestMatchId,
            playerId = resolvedPlayerId,
            durationMillis = elapsedMillisSince(
                startedAtEpochMillis =
                    roomRequestStartedAtEpochMillis,
            ),
            trigger = trigger,
        )
    }

    val matchId = latestMatchId
    val matchReadOutcome = if (matchId != null) {
        fetchAndPublishMatchSnapshot(
            client = client,
            roomId = roomId,
            matchId = matchId,
            trigger = trigger,
            playerId = resolvedPlayerId,
        )
    } else {
        ActiveReadRefreshOutcome.SUCCESS
    }

    if (
        roomReadSucceeded &&
        matchReadOutcome == ActiveReadRefreshOutcome.SUCCESS
    ) {
        resetReadRateLimitBackoff()
        resetReadTransportBackoff()
    }

    return latestRoomStatus
}

private suspend fun fetchAndPublishMatchSnapshot(
    client: RemoteOnlineApiClient,
    roomId: String,
    matchId: String,
    trigger: String,
    playerId: String?,
): ActiveReadRefreshOutcome {
    val currentSnapshot = mutableMatchSnapshot.value

    if (
        currentSnapshot == null ||
        currentSnapshot.matchId != matchId
    ) {
        return fetchAndPublishLatestMatchSnapshot(
            client = client,
            roomId = roomId,
            matchId = matchId,
            trigger = trigger,
            playerId = playerId,
        )
    }

    return fetchAndPublishMatchSnapshotsAfter(
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
): ActiveReadRefreshOutcome {
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

        return ActiveReadRefreshOutcome.SUCCESS
    } catch (error: Throwable) {
        if (error is CancellationException) {
            throw error
        }

        if (isRemoteSessionRejected(error)) {
            invalidateActiveMatchAfterRemoteSessionRejected(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                operation = "fetch_match_snapshot",
                trigger = trigger,
            )
            return ActiveReadRefreshOutcome.TERMINAL
        }

        if (isRemoteParticipationForbidden(error)) {
            invalidateActiveMatchAfterRemoteParticipationAuthorizationLoss(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                reason =
                    OnlineActiveMatchParticipationAuthorizationLossReason
                        .MATCH_PARTICIPATION_FORBIDDEN,
                operation = "fetch_match_snapshot",
                trigger = trigger,
            )
            return ActiveReadRefreshOutcome.TERMINAL
        }

        if (isRemoteResourceNotFound(error)) {
            invalidateActiveMatchAfterRemoteResourceLoss(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                reason =
                    OnlineActiveMatchResourceLossReason.MATCH_NOT_FOUND,
                operation = "fetch_match_snapshot",
                trigger = trigger,
            )
            return ActiveReadRefreshOutcome.TERMINAL
        }

        if (
            handleRemoteReadRateLimit(
                error = error,
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                operation = "fetch_match_snapshot",
                trigger = trigger,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
            )
        ) {
            return ActiveReadRefreshOutcome.RATE_LIMITED
        }

        if (
            handleRemoteReadTransportBackoff(
                error = error,
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                operation = "fetch_match_snapshot",
                trigger = trigger,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
            )
        ) {
            return ActiveReadRefreshOutcome.OTHER_FAILURE
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

        return ActiveReadRefreshOutcome.OTHER_FAILURE
    }
}

private suspend fun fetchAndPublishMatchSnapshotsAfter(
    client: RemoteOnlineApiClient,
    roomId: String,
    matchId: String,
    afterRevision: Long,
    trigger: String,
    playerId: String?,
): ActiveReadRefreshOutcome {
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
                    "firstReceivedRevision" to
                            firstRevision.toString(),
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

        return ActiveReadRefreshOutcome.SUCCESS
    } catch (error: Throwable) {
        if (error is CancellationException) {
            throw error
        }

        if (isRemoteSessionRejected(error)) {
            invalidateActiveMatchAfterRemoteSessionRejected(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                operation = "fetch_match_updates",
                trigger = trigger,
            )
            return ActiveReadRefreshOutcome.TERMINAL
        }

        if (isRemoteParticipationForbidden(error)) {
            invalidateActiveMatchAfterRemoteParticipationAuthorizationLoss(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                reason =
                    OnlineActiveMatchParticipationAuthorizationLossReason
                        .MATCH_PARTICIPATION_FORBIDDEN,
                operation = "fetch_match_updates",
                trigger = trigger,
            )
            return ActiveReadRefreshOutcome.TERMINAL
        }

        if (isRemoteResourceNotFound(error)) {
            invalidateActiveMatchAfterRemoteResourceLoss(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                reason =
                    OnlineActiveMatchResourceLossReason.MATCH_NOT_FOUND,
                operation = "fetch_match_updates",
                trigger = trigger,
            )
            return ActiveReadRefreshOutcome.TERMINAL
        }

        if (
            handleRemoteReadRateLimit(
                error = error,
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                operation = "fetch_match_updates",
                trigger = trigger,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
            )
        ) {
            return ActiveReadRefreshOutcome.RATE_LIMITED
        }

        if (
            handleRemoteReadTransportBackoff(
                error = error,
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                operation = "fetch_match_updates",
                trigger = trigger,
                durationMillis = elapsedMillisSince(
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
            )
        ) {
            return ActiveReadRefreshOutcome.OTHER_FAILURE
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

        return ActiveReadRefreshOutcome.OTHER_FAILURE
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
            snapshot.revision > currentSnapshot.revision
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

        val credentialRepository = sessionCredentialRepository
        val legacySessionRepository = anonymousSessionRepository

        if (
            credentialRepository == null &&
            legacySessionRepository == null
        ) {
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

        val session = if (credentialRepository != null) {
            credentialRepository.getOrCreateUsableCredential(
                createAnonymousSession = {
                    client.createAnonymousSession()
                },
                refreshAccountSession = { accessToken ->
                    client.setDevelopmentPlayerId(
                        playerId = null,
                    )
                    client.setBearerAccessToken(
                        accessToken = accessToken,
                    )
                    client.promoteAccount()
                },
            )
        } else {
            requireNotNull(legacySessionRepository)
                .getOrCreateValidSession {
                    client.createAnonymousSession()
                }
                .toOnlineSessionCredential()
        }

        client.setDevelopmentPlayerId(
            playerId = null,
        )
        client.setBearerAccessToken(
            accessToken = session.accessToken,
        )

        return PreparedRoomParticipant(
            playerId = session.playerId,
            sessionCredential = session,
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

        val hasPersistentSessionRepository =
            sessionCredentialRepository != null ||
                    anonymousSessionRepository != null

        if (!hasPersistentSessionRepository) {
            client.setBearerAccessToken(
                accessToken = null,
            )
            client.setDevelopmentPlayerId(
                playerId = resolvedPlayerId,
            )
            return null
        }

        val activeSession = activeSessionCredential

        if (
            activeSession == null ||
            activeSession.playerId != resolvedPlayerId
        ) {
            clearClientAuthentication(
                client = client,
            )

            return "Credencial online ativa não está disponível."
        }

        val storedSession = getValidSessionCredentialOrNull()

        if (
            storedSession == null ||
            storedSession.playerId != activeSession.playerId ||
            storedSession.accessToken != activeSession.accessToken ||
            storedSession.sessionKind != activeSession.sessionKind ||
            storedSession.accountId != activeSession.accountId
        ) {
            activeSessionCredential = null

            clearClientAuthentication(
                client = client,
            )

            return "Credencial online ativa expirou ou não é mais válida."
        }

        client.setDevelopmentPlayerId(
            playerId = null,
        )
        client.setBearerAccessToken(
            accessToken = storedSession.accessToken,
        )

        return null
    }

    private fun getValidSessionCredentialOrNull():
        OnlineSessionCredential? {
        val credentialRepository = sessionCredentialRepository

        if (credentialRepository != null) {
            return credentialRepository.getValidCredentialOrNull()
        }

        return anonymousSessionRepository
            ?.getValidSessionOrNull()
            ?.toOnlineSessionCredential()
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
