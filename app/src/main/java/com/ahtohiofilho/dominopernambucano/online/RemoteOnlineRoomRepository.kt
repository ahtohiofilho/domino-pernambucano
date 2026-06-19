package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteOnlineRoomRepository(
    private val config: OnlineBackendConfig,
    private val apiClient: RemoteOnlineApiClient? = createApiClientOrNull(config),
) : OnlineRoomRepository {
    private val mutableRoomSnapshot =
        MutableStateFlow<OnlineRoomSnapshotDto?>(null)

    private val mutableMatchSnapshot =
        MutableStateFlow<OnlineMatchSnapshotDto?>(null)

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

        return runCatching {
            client.submitAction(action)
        }.fold(
            onSuccess = { result ->
                if (result.accepted) {
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

    override suspend fun leaveRoom() {
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

        val matchId = room.matchId
        if (result.accepted && matchId != null) {
            runCatching {
                client.fetchMatchSnapshot(matchId)
            }.onSuccess { match ->
                mutableMatchSnapshot.value = match
            }
        }
    }

    private suspend fun refreshSnapshotsAfterAction(
        client: RemoteOnlineApiClient,
        action: OnlinePlayerActionDto,
    ) {
        runCatching {
            client.fetchRoomSnapshot(action.roomId)
        }.onSuccess { room ->
            mutableRoomSnapshot.value = room
        }

        runCatching {
            client.fetchMatchSnapshot(action.matchId)
        }.onSuccess { match ->
            mutableMatchSnapshot.value = match
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