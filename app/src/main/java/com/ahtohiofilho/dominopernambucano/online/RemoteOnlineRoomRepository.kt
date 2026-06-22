package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private var pollingJob: Job? = null
    private var pollingRoomId: String? = null

    override val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?> =
        mutableRoomSnapshot.asStateFlow()

    override val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?> =
        mutableMatchSnapshot.asStateFlow()

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        val client = apiClient
            ?: return rejectedRoomOperation(
                reason = getUnavailableBackendReason(),
            )

        return runCatching {
            client.createRoom(request)
        }.fold(
            onSuccess = { result ->
                applyRoomOperationResult(
                    result = result,
                    client = client,
                )

                result
            },
            onFailure = { error ->
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
            )

        return runCatching {
            client.joinRoom(request)
        }.fold(
            onSuccess = { result ->
                applyRoomOperationResult(
                    result = result,
                    client = client,
                )

                result
            },
            onFailure = { error ->
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
            )

        return refreshMutex.withLock {
            runCatching {
                client.submitAction(action)
            }.fold(
                onSuccess = { result ->
                    if (
                        result.accepted ||
                        shouldRefreshAfterRejectedAction(
                            action = action,
                            result = result,
                        )
                    ) {
                        refreshSnapshotsAfterAction(
                            client = client,
                            action = action,
                        )
                    }

                    result.copy(
                        actionId = result.actionId ?: action.actionId,
                    )
                },
                onFailure = { error ->
                    rejectedAction(
                        action = action,
                        reason = error.toOnlineFailureReason(
                            fallback = "Falha ao enviar ação online remota.",
                        ),
                    )
                },
            )
        }
    }

    override suspend fun leaveRoom() {
        stopPolling()

        val client = apiClient
        val currentSnapshot = mutableMatchSnapshot.value
        val currentRoom = mutableRoomSnapshot.value

        if (
            client != null &&
            currentSnapshot != null &&
            currentRoom != null
        ) {
            runCatching {
                client.submitAction(
                    createOnlineLeaveRoomAction(
                        roomId = currentRoom.roomId,
                        matchId = currentSnapshot.matchId,
                        playerId = currentRoom.players
                            .firstOrNull { player -> player.connected }
                            ?.playerId
                            ?: currentRoom.hostPlayerId,
                        revision = currentSnapshot.revision,
                    )
                )
            }
        }

        mutableRoomSnapshot.value = null
        mutableMatchSnapshot.value = null
    }

    private suspend fun applyRoomOperationResult(
        result: OnlineRoomOperationResultDto,
        client: RemoteOnlineApiClient,
    ) {
        val room = result.roomSnapshot ?: return

        mutableRoomSnapshot.value = room

        if (result.accepted) {
            startPolling(
                roomId = room.roomId,
                client = client,
            )
        }

        val matchId = room.matchId
        if (result.accepted && matchId != null) {
            runCatching {
                client.fetchMatchSnapshot(matchId)
            }.onSuccess { match ->
                publishMatchSnapshotIfNewer(
                    snapshot = match,
                )
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

        stopPolling()

        pollingRoomId = roomId

        pollingJob = repositoryScope.launch {
            while (isActive) {
                delay(pollingPolicy.intervalMillis)

                refreshMutex.withLock {
                    refreshSnapshots(
                        client = client,
                        roomId = roomId,
                        fallbackMatchId = mutableMatchSnapshot.value?.matchId,
                    )
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        pollingRoomId = null
    }

    private suspend fun refreshSnapshots(
        client: RemoteOnlineApiClient,
        roomId: String,
        fallbackMatchId: String?,
    ) {
        var latestMatchId = fallbackMatchId

        runCatching {
            client.fetchRoomSnapshot(roomId)
        }.onSuccess { room ->
            mutableRoomSnapshot.value = room
            latestMatchId = room.matchId ?: latestMatchId
        }

        val matchId = latestMatchId ?: return

        runCatching {
            client.fetchMatchSnapshot(matchId)
        }.onSuccess { match ->
            publishMatchSnapshotIfNewer(
                snapshot = match,
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

    private fun publishMatchSnapshotIfNewer(
        snapshot: OnlineMatchSnapshotDto,
    ) {
        val currentSnapshot = mutableMatchSnapshot.value

        if (
            currentSnapshot == null ||
            currentSnapshot.matchId != snapshot.matchId ||
            snapshot.revision >= currentSnapshot.revision
        ) {
            mutableMatchSnapshot.value = snapshot
        }
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