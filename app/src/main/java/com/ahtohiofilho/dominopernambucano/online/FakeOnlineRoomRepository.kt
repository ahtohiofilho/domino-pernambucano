package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.createInitialPlayerClockMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeOnlineRoomRepository(
    private val clockPolicy: DominoMatchClockPolicy =
        DominoMatchClockPolicy.OnlinePerPlayerRound,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) : OnlineRoomRepository {
    private val mutableRoomSnapshot =
        MutableStateFlow<OnlineRoomSnapshotDto?>(null)

    private val mutableMatchSnapshot =
        MutableStateFlow<OnlineMatchSnapshotDto?>(null)

    private var nextRoomSequence = 1
    private var nextMatchSequence = 1
    private var revision = 0L

    private val actionResultsById = mutableMapOf<String, OnlineActionResultDto>()

    override val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?> =
        mutableRoomSnapshot.asStateFlow()

    override val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?> =
        mutableMatchSnapshot.asStateFlow()

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        val roomSequence = nextRoomSequence++
        val roomId = "fake-room-$roomSequence"
        val roomCode = roomSequence.toString().padStart(
            length = 4,
            padChar = '0',
        )

        revision = 0L
        mutableMatchSnapshot.value = null
        actionResultsById.clear()

        val now = nowEpochMillis()

        val snapshot = OnlineRoomSnapshotDto(
            roomId = roomId,
            roomCode = roomCode,
            hostPlayerId = request.localPlayerId,
            status = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = request.localPlayerId,
                    name = request.playerName,
                    seatIndex = 0,
                    connected = true,
                )
            ),
            matchId = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

        mutableRoomSnapshot.value = snapshot

        return OnlineRoomOperationResultDto(
            accepted = true,
            roomSnapshot = snapshot,
            localSeatIndex = 0,
        )
    }

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        val currentRoom = mutableRoomSnapshot.value
            ?: return rejectedRoomOperation(
                reason = "Nenhuma sala fake foi criada."
            )

        if (currentRoom.roomCode != request.roomCode) {
            return rejectedRoomOperation(
                reason = "Código de sala inválido."
            )
        }

        if (
            currentRoom.status == OnlineRoomStatusDto.CLOSED ||
            currentRoom.status == OnlineRoomStatusDto.FINISHED
        ) {
            return rejectedRoomOperation(
                reason = "A sala não está mais disponível."
            )
        }

        val existingPlayer = currentRoom.players.firstOrNull { player ->
            player.playerId == request.localPlayerId
        }

        if (existingPlayer != null) {
            val updatedPlayers = currentRoom.players.map { player ->
                if (player.playerId == request.localPlayerId) {
                    player.copy(
                        name = request.playerName,
                        connected = true,
                    )
                } else {
                    player
                }
            }

            val updatedRoom = currentRoom.copy(
                players = updatedPlayers,
                updatedAtEpochMillis = nowEpochMillis(),
            )

            mutableRoomSnapshot.value = updatedRoom

            return OnlineRoomOperationResultDto(
                accepted = true,
                roomSnapshot = updatedRoom,
                localSeatIndex = existingPlayer.seatIndex,
            )
        }

        if (currentRoom.status == OnlineRoomStatusDto.IN_MATCH) {
            return rejectedRoomOperation(
                reason = "A partida já foi iniciada."
            )
        }

        val occupiedSeats = currentRoom.players
            .mapNotNull { player -> player.seatIndex }
            .toSet()

        val nextSeatIndex = (0..3).firstOrNull { seatIndex ->
            seatIndex !in occupiedSeats
        } ?: return rejectedRoomOperation(
            reason = "A sala já está cheia."
        )

        val updatedPlayers = currentRoom.players + OnlineRoomPlayerDto(
            playerId = request.localPlayerId,
            name = request.playerName,
            seatIndex = nextSeatIndex,
            connected = true,
        )

        val shouldStartMatch = updatedPlayers.size >= 4
        val nextMatchId = if (shouldStartMatch) {
            "fake-match-${nextMatchSequence++}"
        } else {
            null
        }

        val updatedRoom = currentRoom.copy(
            status = if (shouldStartMatch) {
                OnlineRoomStatusDto.IN_MATCH
            } else {
                OnlineRoomStatusDto.WAITING_FOR_PLAYERS
            },
            players = updatedPlayers,
            matchId = nextMatchId,
            updatedAtEpochMillis = nowEpochMillis(),
        )

        mutableRoomSnapshot.value = updatedRoom

        if (shouldStartMatch && nextMatchId != null) {
            startMatch(
                room = updatedRoom,
                matchId = nextMatchId,
            )
        }

        return OnlineRoomOperationResultDto(
            accepted = true,
            roomSnapshot = updatedRoom,
            localSeatIndex = nextSeatIndex,
        )
    }

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        actionResultsById[action.actionId]?.let { previousResult ->
            return previousResult
        }

        val currentRoom = mutableRoomSnapshot.value
            ?: return cacheActionResult(
                action = action,
                result = rejectedAction(
                    reason = "Nenhuma sala ativa.",
                ),
            )

        val currentSnapshot = mutableMatchSnapshot.value
            ?: return cacheActionResult(
                action = action,
                result = rejectedAction(
                    reason = "A partida ainda não foi iniciada.",
                ),
            )

        if (action.roomId != currentRoom.roomId) {
            return cacheActionResult(
                action = action,
                result = rejectedAction(
                    reason = "Sala inválida.",
                    revision = currentSnapshot.revision,
                ),
            )
        }

        if (action.matchId != currentSnapshot.matchId) {
            return cacheActionResult(
                action = action,
                result = rejectedAction(
                    reason = "Partida inválida.",
                    revision = currentSnapshot.revision,
                ),
            )
        }

        val roomPlayer = currentRoom.players.firstOrNull { player ->
            player.playerId == action.playerId
        } ?: return cacheActionResult(
            action = action,
            result = rejectedAction(
                reason = "Jogador não encontrado na sala.",
                revision = currentSnapshot.revision,
            ),
        )

        val seatIndex = roomPlayer.seatIndex
            ?: return cacheActionResult(
                action = action,
                result = rejectedAction(
                    reason = "Jogador sem assento definido.",
                    revision = currentSnapshot.revision,
                ),
            )

        if (!roomPlayer.connected) {
            return cacheActionResult(
                action = action,
                result = rejectedAction(
                    reason = "Jogador desconectado.",
                    revision = currentSnapshot.revision,
                ),
            )
        }

        val result = when (action.type) {
            OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT -> {
                submitSnapshotRequest(
                    currentRoom = currentRoom,
                    currentSnapshot = currentSnapshot,
                )
            }

            OnlinePlayerActionTypeDto.LEAVE_ROOM -> {
                markPlayerDisconnected(
                    playerId = action.playerId,
                )

                OnlineActionResultDto(
                    accepted = true,
                    revision = currentSnapshot.revision,
                )
            }

            OnlinePlayerActionTypeDto.START_NEXT_ROUND -> {
                submitStartNextRound(
                    action = action,
                    currentRoom = currentRoom,
                    currentSnapshot = currentSnapshot,
                )
            }

            OnlinePlayerActionTypeDto.START_NEW_MATCH -> {
                submitStartNewMatch(
                    action = action,
                    currentRoom = currentRoom,
                    currentSnapshot = currentSnapshot,
                )
            }

            OnlinePlayerActionTypeDto.PLAY_MOVE,
            OnlinePlayerActionTypeDto.PASS_TURN -> {
                submitGameAction(
                    action = action,
                    currentSnapshot = currentSnapshot,
                    seatIndex = seatIndex,
                )
            }
        }

        return cacheActionResult(
            action = action,
            result = result,
        )
    }

    override suspend fun leaveRoom() {
        val currentRoom = mutableRoomSnapshot.value ?: return

        mutableRoomSnapshot.value = currentRoom.copy(
            status = OnlineRoomStatusDto.CLOSED,
            players = currentRoom.players.map { player ->
                player.copy(
                    connected = false,
                )
            },
            updatedAtEpochMillis = nowEpochMillis(),
        )

        mutableMatchSnapshot.value = null
    }

    private fun startMatch(
        room: OnlineRoomSnapshotDto,
        matchId: String,
    ) {
        val gameState = applyOnlineRoomPlayerNames(
            gameState = createInitialDominoGameState(),
            room = room,
        )

        val initialRuntimeState = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = determineOnlineNextPhase(
                gameState = gameState,
            ),
            clockPolicy = clockPolicy,
            playerClockMillis = createInitialPlayerClockMillis(
                playerCount = gameState.players.size,
                clockPolicy = clockPolicy,
            ),
        )

        revision = 1L

        mutableMatchSnapshot.value = initialRuntimeState.toOnlineSnapshotDto(
            roomId = room.roomId,
            matchId = matchId,
            revision = revision,
            serverEpochMillis = nowEpochMillis(),
        )
    }

    private fun submitSnapshotRequest(
        currentRoom: OnlineRoomSnapshotDto,
        currentSnapshot: OnlineMatchSnapshotDto,
    ): OnlineActionResultDto {
        val now = nowEpochMillis()
        val runtimeState = currentSnapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        if (
            shouldAdvanceFakePlayerForSnapshotRequest(
                room = currentRoom,
                gameState = runtimeState.gameState,
            )
        ) {
            val updatedGameState = advanceSingleFakeTurn(
                gameState = runtimeState.gameState,
            )

            val updatedRuntimeState = runtimeState.copy(
                gameState = updatedGameState,
                phase = determineOnlineNextPhase(
                    gameState = updatedGameState,
                ),
            )

            return publishMatchSnapshot(
                previousSnapshot = currentSnapshot,
                runtimeState = updatedRuntimeState,
                serverEpochMillis = now,
            )
        }

        val clockedRuntimeState = reduceOnlineAuthoritativeClock(
            runtimeState = runtimeState,
            elapsedMillis = getElapsedMillisSinceSnapshot(
                snapshot = currentSnapshot,
                nowEpochMillis = now,
            ),
        )

        if (clockedRuntimeState != runtimeState) {
            return publishMatchSnapshot(
                previousSnapshot = currentSnapshot,
                runtimeState = clockedRuntimeState,
                serverEpochMillis = now,
            )
        }

        return OnlineActionResultDto(
            accepted = true,
            revision = currentSnapshot.revision,
        )
    }

    private fun submitGameAction(
        action: OnlinePlayerActionDto,
        currentSnapshot: OnlineMatchSnapshotDto,
        seatIndex: Int,
    ): OnlineActionResultDto {
        val now = nowEpochMillis()
        val runtimeState = currentSnapshot.toRuntimeState(
            localPlayerIndex = seatIndex,
        )

        val clockedRuntimeState = reduceOnlineAuthoritativeClock(
            runtimeState = runtimeState,
            elapsedMillis = getElapsedMillisSinceSnapshot(
                snapshot = currentSnapshot,
                nowEpochMillis = now,
            ),
        )

        if (clockedRuntimeState.gameState != runtimeState.gameState) {
            val timeoutResult = publishMatchSnapshot(
                previousSnapshot = currentSnapshot,
                runtimeState = clockedRuntimeState,
                serverEpochMillis = now,
            )

            return timeoutResult.copy(
                accepted = false,
                reason = "Tempo esgotado.",
            )
        }

        val clockedSnapshot = clockedRuntimeState.toOnlineSnapshotDto(
            roomId = currentSnapshot.roomId,
            matchId = currentSnapshot.matchId,
            revision = currentSnapshot.revision,
            serverEpochMillis = now,
        )

        return publishMatchReduction(
            previousSnapshot = clockedSnapshot,
            reduction = reduceOnlineGameAction(
                action = action,
                currentSnapshot = clockedSnapshot,
                seatIndex = seatIndex,
            ),
            serverEpochMillis = now,
        )
    }

    private fun submitStartNextRound(
        action: OnlinePlayerActionDto,
        currentRoom: OnlineRoomSnapshotDto,
        currentSnapshot: OnlineMatchSnapshotDto,
    ): OnlineActionResultDto {
        return publishMatchReduction(
            previousSnapshot = currentSnapshot,
            reduction = reduceOnlineStartNextRoundAction(
                action = action,
                currentRoom = currentRoom,
                currentSnapshot = currentSnapshot,
            ),
        )
    }

    private fun submitStartNewMatch(
        action: OnlinePlayerActionDto,
        currentRoom: OnlineRoomSnapshotDto,
        currentSnapshot: OnlineMatchSnapshotDto,
    ): OnlineActionResultDto {
        return publishMatchReduction(
            previousSnapshot = currentSnapshot,
            reduction = reduceOnlineStartNewMatchAction(
                action = action,
                currentRoom = currentRoom,
                currentSnapshot = currentSnapshot,
            ),
        )
    }

    private fun publishMatchReduction(
        previousSnapshot: OnlineMatchSnapshotDto,
        reduction: OnlineMatchActionReduction,
        serverEpochMillis: Long = nowEpochMillis(),
    ): OnlineActionResultDto {
        return when (reduction) {
            is OnlineMatchActionReduction.Accepted -> {
                publishMatchSnapshot(
                    previousSnapshot = previousSnapshot,
                    runtimeState = reduction.runtimeState,
                    serverEpochMillis = serverEpochMillis,
                )
            }

            is OnlineMatchActionReduction.Rejected -> {
                rejectedAction(
                    reason = reduction.reason,
                    revision = reduction.revision,
                )
            }
        }
    }

    private fun publishMatchSnapshot(
        previousSnapshot: OnlineMatchSnapshotDto,
        runtimeState: DominoMatchRuntimeState,
        serverEpochMillis: Long = nowEpochMillis(),
    ): OnlineActionResultDto {
        revision = previousSnapshot.revision + 1L

        val updatedSnapshot = runtimeState.toOnlineSnapshotDto(
            roomId = previousSnapshot.roomId,
            matchId = previousSnapshot.matchId,
            revision = revision,
            serverEpochMillis = serverEpochMillis,
        )

        mutableMatchSnapshot.value = updatedSnapshot

        mutableRoomSnapshot.value = mutableRoomSnapshot.value?.copy(
            status = if (runtimeState.gameState.gameWinnerTeamIndex != null) {
                OnlineRoomStatusDto.FINISHED
            } else {
                OnlineRoomStatusDto.IN_MATCH
            },
            updatedAtEpochMillis = serverEpochMillis,
        )

        return OnlineActionResultDto(
            accepted = true,
            revision = revision,
        )
    }

    private fun getElapsedMillisSinceSnapshot(
        snapshot: OnlineMatchSnapshotDto,
        nowEpochMillis: Long,
    ): Long {
        val previousEpochMillis = snapshot.serverEpochMillis
            ?: return 0L

        return (nowEpochMillis - previousEpochMillis).coerceAtLeast(0L)
    }

    private fun markPlayerDisconnected(
        playerId: String,
    ) {
        val currentRoom = mutableRoomSnapshot.value ?: return

        mutableRoomSnapshot.value = currentRoom.copy(
            players = currentRoom.players.map { player ->
                if (player.playerId == playerId) {
                    player.copy(
                        connected = false,
                    )
                } else {
                    player
                }
            },
            updatedAtEpochMillis = nowEpochMillis(),
        )
    }

    private fun cacheActionResult(
        action: OnlinePlayerActionDto,
        result: OnlineActionResultDto,
    ): OnlineActionResultDto {
        val resultWithActionId = result.copy(
            actionId = action.actionId,
        )

        actionResultsById[action.actionId] = resultWithActionId

        return resultWithActionId
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
        reason: String,
        revision: Long? = mutableMatchSnapshot.value?.revision,
    ): OnlineActionResultDto {
        return OnlineActionResultDto(
            accepted = false,
            revision = revision,
            reason = reason,
        )
    }
}