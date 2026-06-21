package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import com.ahtohiofilho.dominopernambucano.domain.passTurn
import com.ahtohiofilho.dominopernambucano.domain.playMoveForCurrentPlayer
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.createInitialPlayerClockMillis
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.match.findRandomPlayableMove
import com.ahtohiofilho.dominopernambucano.match.isPlayerClockExpired
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchActionReduction
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.applyOnlineRoomPlayerNames
import com.ahtohiofilho.dominopernambucano.online.determineOnlineNextPhase
import com.ahtohiofilho.dominopernambucano.online.reduceOnlineAuthoritativeClock
import com.ahtohiofilho.dominopernambucano.online.reduceOnlineGameAction
import com.ahtohiofilho.dominopernambucano.online.reduceOnlineStartNewMatchAction
import com.ahtohiofilho.dominopernambucano.online.reduceOnlineStartNextRoundAction
import com.ahtohiofilho.dominopernambucano.online.toOnlineSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState

private const val DEVELOPMENT_BOT_PLAYER_ID_PREFIX =
    "development-bot-seat-"

private const val FIRST_DEVELOPMENT_BOT_SEAT_INDEX = 2
private const val LAST_DEVELOPMENT_BOT_SEAT_INDEX = 3

class InMemoryOnlineServerStore(
    private val clockPolicy: DominoMatchClockPolicy =
        DominoMatchClockPolicy.OnlinePerPlayerRound,
    private val autoFillDevelopmentBotsAfterTwoHumanPlayers: Boolean = false,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {
    private data class MatchRecord(
        val roomId: String,
        val matchId: String,
        var snapshot: OnlineMatchSnapshotDto,
        val automaticSeatIndexes: MutableSet<Int> = mutableSetOf(),
        val developmentBotSeatIndexes: Set<Int> = emptySet(),
    )

    private data class ClockReductionResult(
        val runtimeState: DominoMatchRuntimeState,
        val turnWasResolved: Boolean,
    )

    private val lock = Any()

    private val roomsById = mutableMapOf<String, OnlineRoomSnapshotDto>()
    private val roomIdsByCode = mutableMapOf<String, String>()
    private val matchesById = mutableMapOf<String, MatchRecord>()
    private val actionResultsById = mutableMapOf<String, OnlineActionResultDto>()

    private var nextRoomSequence = 1
    private var nextMatchSequence = 1

    fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return synchronized(lock) {
            if (request.localPlayerId.isBlank()) {
                return@synchronized rejectedRoomOperation(
                    reason = "Identificador do jogador não informado.",
                )
            }

            if (request.playerName.isBlank()) {
                return@synchronized rejectedRoomOperation(
                    reason = "Nome do jogador não informado.",
                )
            }

            val roomSequence = nextRoomSequence++
            val roomId = "server-room-$roomSequence"
            val roomCode = roomSequence.toString().padStart(
                length = 4,
                padChar = '0',
            )

            val now = nowEpochMillis()

            val room = OnlineRoomSnapshotDto(
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
                    ),
                ),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )

            roomsById[roomId] = room
            roomIdsByCode[roomCode] = roomId

            OnlineRoomOperationResultDto(
                accepted = true,
                roomSnapshot = room,
                localSeatIndex = 0,
            )
        }
    }

    fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return synchronized(lock) {
            val normalizedRoomCode = request.roomCode.trim()

            val roomId = roomIdsByCode[normalizedRoomCode]
                ?: return@synchronized rejectedRoomOperation(
                    reason = "Código de sala inválido.",
                )

            val currentRoom = roomsById[roomId]
                ?: return@synchronized rejectedRoomOperation(
                    reason = "Sala não encontrada.",
                )

            if (
                currentRoom.status == OnlineRoomStatusDto.CLOSED ||
                currentRoom.status == OnlineRoomStatusDto.FINISHED
            ) {
                return@synchronized rejectedRoomOperation(
                    reason = "A sala não está mais disponível.",
                )
            }

            if (request.localPlayerId.isBlank()) {
                return@synchronized rejectedRoomOperation(
                    reason = "Identificador do jogador não informado.",
                )
            }

            if (request.playerName.isBlank()) {
                return@synchronized rejectedRoomOperation(
                    reason = "Nome do jogador não informado.",
                )
            }

            val existingPlayer = currentRoom.players.firstOrNull { player ->
                player.playerId == request.localPlayerId
            }

            if (existingPlayer != null) {
                val updatedRoom = currentRoom.copy(
                    players = currentRoom.players.map { player ->
                        if (player.playerId == request.localPlayerId) {
                            player.copy(
                                name = request.playerName,
                                connected = true,
                            )
                        } else {
                            player
                        }
                    },
                    updatedAtEpochMillis = nowEpochMillis(),
                )

                roomsById[updatedRoom.roomId] = updatedRoom

                return@synchronized OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = updatedRoom,
                    localSeatIndex = existingPlayer.seatIndex,
                )
            }

            if (currentRoom.status == OnlineRoomStatusDto.IN_MATCH) {
                return@synchronized rejectedRoomOperation(
                    reason = "A partida já foi iniciada.",
                )
            }

            val occupiedSeats = currentRoom.players
                .mapNotNull { player -> player.seatIndex }
                .toSet()

            val nextSeatIndex = (0..3).firstOrNull { seatIndex ->
                seatIndex !in occupiedSeats
            } ?: return@synchronized rejectedRoomOperation(
                reason = "A sala já está cheia.",
            )

            val playersAfterHumanJoin = currentRoom.players + OnlineRoomPlayerDto(
                playerId = request.localPlayerId,
                name = request.playerName,
                seatIndex = nextSeatIndex,
                connected = true,
            )

            val updatedPlayers = addDevelopmentBotsIfNeeded(
                players = playersAfterHumanJoin,
            )

            val shouldStartMatch = updatedPlayers.size == 4
            val nextMatchId = if (shouldStartMatch) {
                "server-match-${nextMatchSequence++}"
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

            roomsById[updatedRoom.roomId] = updatedRoom

            if (nextMatchId != null) {
                createMatch(
                    room = updatedRoom,
                    matchId = nextMatchId,
                )
            }

            OnlineRoomOperationResultDto(
                accepted = true,
                roomSnapshot = updatedRoom,
                localSeatIndex = nextSeatIndex,
            )
        }
    }

    fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        return synchronized(lock) {
            actionResultsById[action.actionId]?.let { cachedResult ->
                return@synchronized cachedResult
            }

            val currentRoom = roomsById[action.roomId]
                ?: return@synchronized cacheActionResult(
                    action = action,
                    result = rejectedAction(
                        reason = "Sala inválida.",
                    ),
                )

            val matchRecord = matchesById[action.matchId]
                ?: return@synchronized cacheActionResult(
                    action = action,
                    result = rejectedAction(
                        reason = "Partida inválida.",
                    ),
                )

            if (matchRecord.roomId != currentRoom.roomId) {
                return@synchronized cacheActionResult(
                    action = action,
                    result = rejectedAction(
                        reason = "A partida não pertence à sala informada.",
                        revision = matchRecord.snapshot.revision,
                    ),
                )
            }

            val roomPlayer = currentRoom.players.firstOrNull { player ->
                player.playerId == action.playerId
            } ?: return@synchronized cacheActionResult(
                action = action,
                result = rejectedAction(
                    reason = "Jogador não encontrado na sala.",
                    revision = matchRecord.snapshot.revision,
                ),
            )

            val seatIndex = roomPlayer.seatIndex
                ?: return@synchronized cacheActionResult(
                    action = action,
                    result = rejectedAction(
                        reason = "Jogador sem assento definido.",
                        revision = matchRecord.snapshot.revision,
                    ),
                )

            if (!roomPlayer.connected) {
                return@synchronized cacheActionResult(
                    action = action,
                    result = rejectedAction(
                        reason = "Jogador desconectado.",
                        revision = matchRecord.snapshot.revision,
                    ),
                )
            }

            val result = when (action.type) {
                OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT -> {
                    submitSnapshotRequest(
                        matchRecord = matchRecord,
                    )
                }

                OnlinePlayerActionTypeDto.LEAVE_ROOM -> {
                    markPlayerDisconnected(
                        roomId = currentRoom.roomId,
                        playerId = action.playerId,
                    )

                    OnlineActionResultDto(
                        accepted = true,
                        revision = matchRecord.snapshot.revision,
                    )
                }

                OnlinePlayerActionTypeDto.START_NEXT_ROUND -> {
                    submitStartNextRound(
                        action = action,
                        currentRoom = currentRoom,
                        matchRecord = matchRecord,
                    )
                }

                OnlinePlayerActionTypeDto.START_NEW_MATCH -> {
                    submitStartNewMatch(
                        action = action,
                        currentRoom = currentRoom,
                        matchRecord = matchRecord,
                    )
                }

                OnlinePlayerActionTypeDto.PLAY_MOVE,
                OnlinePlayerActionTypeDto.PASS_TURN -> {
                    submitGameAction(
                        action = action,
                        seatIndex = seatIndex,
                        matchRecord = matchRecord,
                    )
                }
            }

            cacheActionResult(
                action = action,
                result = result,
            )
        }
    }

    fun getRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto? {
        return synchronized(lock) {
            roomsById[roomId]
        }
    }

    fun getMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto? {
        return synchronized(lock) {
            val matchRecord = matchesById[matchId]
                ?: return@synchronized null

            submitSnapshotRequest(
                matchRecord = matchRecord,
            )

            matchRecord.snapshot
        }
    }

    private fun addDevelopmentBotsIfNeeded(
        players: List<OnlineRoomPlayerDto>,
    ): List<OnlineRoomPlayerDto> {
        if (!autoFillDevelopmentBotsAfterTwoHumanPlayers) {
            return players
        }

        if (players.size != 2) {
            return players
        }

        val occupiedSeatIndexes = players
            .mapNotNull { player ->
                player.seatIndex
            }
            .toSet()

        val developmentBots =
            (FIRST_DEVELOPMENT_BOT_SEAT_INDEX..LAST_DEVELOPMENT_BOT_SEAT_INDEX)
                .filter { seatIndex ->
                    seatIndex !in occupiedSeatIndexes
                }
                .map { seatIndex ->
                    OnlineRoomPlayerDto(
                        playerId = "$DEVELOPMENT_BOT_PLAYER_ID_PREFIX$seatIndex",
                        name = "Bot ${seatIndex + 1}",
                        seatIndex = seatIndex,
                        connected = true,
                    )
                }

        return players + developmentBots
    }

    private fun createMatch(
        room: OnlineRoomSnapshotDto,
        matchId: String,
    ) {
        val gameState = applyOnlineRoomPlayerNames(
            gameState = createInitialDominoGameState(),
            room = room,
        )

        val runtimeState = DominoMatchRuntimeState(
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

        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = room.roomId,
            matchId = matchId,
            revision = 1L,
            serverEpochMillis = nowEpochMillis(),
        )

        matchesById[matchId] = MatchRecord(
            roomId = room.roomId,
            matchId = matchId,
            snapshot = snapshot,
            developmentBotSeatIndexes = room.players
                .filter { player ->
                    isDevelopmentBotPlayerId(
                        playerId = player.playerId,
                    )
                }
                .mapNotNull { player ->
                    player.seatIndex
                }
                .toSet(),
        )
    }

    private fun submitSnapshotRequest(
        matchRecord: MatchRecord,
    ): OnlineActionResultDto {
        val currentSnapshot = matchRecord.snapshot
        val runtimeState = currentSnapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        val clockReduction = reduceClockAndRegisterAutomaticPlayer(
            runtimeState = runtimeState,
            automaticSeatIndexes = matchRecord.automaticSeatIndexes,
            elapsedMillis = getElapsedMillisSinceSnapshot(
                snapshot = currentSnapshot,
                nowEpochMillis = nowEpochMillis(),
            ),
        )

        /*
         * Mantém uma única transição autoritativa por polling.
         *
         * Se o relógio resolveu a vez atual, este snapshot é publicado
         * primeiro. O próximo polling poderá avançar um bot, preservando
         * cadência para as animações dos clientes.
         */
        if (clockReduction.turnWasResolved) {
            return publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = clockReduction.runtimeState,
            )
        }

        val runtimeStateAfterClock = clockReduction.runtimeState

        val runtimeStateAfterBotTurn =
            advanceDevelopmentBotTurnIfNeeded(
                matchRecord = matchRecord,
                runtimeState = runtimeStateAfterClock,
            )

        if (runtimeStateAfterBotTurn != runtimeStateAfterClock) {
            return publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = runtimeStateAfterBotTurn,
            )
        }

        if (runtimeStateAfterClock != runtimeState) {
            return publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = runtimeStateAfterClock,
            )
        }

        return OnlineActionResultDto(
            accepted = true,
            revision = currentSnapshot.revision,
        )
    }

    private fun advanceDevelopmentBotTurnIfNeeded(
        matchRecord: MatchRecord,
        runtimeState: DominoMatchRuntimeState,
    ): DominoMatchRuntimeState {
        if (matchRecord.developmentBotSeatIndexes.isEmpty()) {
            return runtimeState
        }

        val gameState = runtimeState.gameState

        if (isRoundFinished(gameState) || isGameFinished(gameState)) {
            return runtimeState
        }

        val currentPlayerIndex = gameState.currentPlayerIndex

        if (currentPlayerIndex !in matchRecord.developmentBotSeatIndexes) {
            return runtimeState
        }

        val updatedGameState = findBasicBotMove(
            state = gameState,
        )?.let { move ->
            playMoveForCurrentPlayer(
                state = gameState,
                playableMove = move,
            )
        } ?: passTurn(
            state = gameState,
        )

        return runtimeState.copy(
            gameState = updatedGameState,
            phase = determineOnlineNextPhase(
                gameState = updatedGameState,
            ),
        )
    }

    private fun submitGameAction(
        action: OnlinePlayerActionDto,
        seatIndex: Int,
        matchRecord: MatchRecord,
    ): OnlineActionResultDto {
        val currentSnapshot = matchRecord.snapshot
        val now = nowEpochMillis()

        val runtimeState = currentSnapshot.toRuntimeState(
            localPlayerIndex = seatIndex,
        )

        val clockReduction = reduceClockAndRegisterAutomaticPlayer(
            runtimeState = runtimeState,
            automaticSeatIndexes = matchRecord.automaticSeatIndexes,
            elapsedMillis = getElapsedMillisSinceSnapshot(
                snapshot = currentSnapshot,
                nowEpochMillis = now,
            ),
        )

        if (clockReduction.turnWasResolved) {
            val publishedResult = publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = clockReduction.runtimeState,
                serverEpochMillis = now,
            )

            val reason = if (
                seatIndex in matchRecord.automaticSeatIndexes
            ) {
                "Jogador em modo automático nesta rodada."
            } else {
                "Tempo esgotado."
            }

            return publishedResult.copy(
                accepted = false,
                reason = reason,
            )
        }

        val clockedSnapshot = clockReduction.runtimeState.toOnlineSnapshotDto(
            roomId = currentSnapshot.roomId,
            matchId = currentSnapshot.matchId,
            revision = currentSnapshot.revision,
            serverEpochMillis = now,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes.sorted(),
        )

        return when (
            val reduction = reduceOnlineGameAction(
                action = action,
                currentSnapshot = clockedSnapshot,
                seatIndex = seatIndex,
            )
        ) {
            is OnlineMatchActionReduction.Accepted -> {
                publishMatchSnapshot(
                    matchRecord = matchRecord,
                    previousSnapshot = clockedSnapshot,
                    runtimeState = reduction.runtimeState,
                    serverEpochMillis = now,
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

    private fun submitStartNextRound(
        action: OnlinePlayerActionDto,
        currentRoom: OnlineRoomSnapshotDto,
        matchRecord: MatchRecord,
    ): OnlineActionResultDto {
        return when (
            val reduction = reduceOnlineStartNextRoundAction(
                action = action,
                currentRoom = currentRoom,
                currentSnapshot = matchRecord.snapshot,
            )
        ) {
            is OnlineMatchActionReduction.Accepted -> {
                /*
                 * O modo automático é deliberadamente limitado à rodada.
                 * Ao começar a próxima rodada, todos recuperam o controle.
                 */
                matchRecord.automaticSeatIndexes.clear()

                publishMatchSnapshot(
                    matchRecord = matchRecord,
                    previousSnapshot = matchRecord.snapshot,
                    runtimeState = reduction.runtimeState,
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

    private fun submitStartNewMatch(
        action: OnlinePlayerActionDto,
        currentRoom: OnlineRoomSnapshotDto,
        matchRecord: MatchRecord,
    ): OnlineActionResultDto {
        return when (
            val reduction = reduceOnlineStartNewMatchAction(
                action = action,
                currentRoom = currentRoom,
                currentSnapshot = matchRecord.snapshot,
            )
        ) {
            is OnlineMatchActionReduction.Accepted -> {
                matchRecord.automaticSeatIndexes.clear()

                publishMatchSnapshot(
                    matchRecord = matchRecord,
                    previousSnapshot = matchRecord.snapshot,
                    runtimeState = reduction.runtimeState,
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

    private fun reduceClockAndRegisterAutomaticPlayer(
        runtimeState: DominoMatchRuntimeState,
        automaticSeatIndexes: MutableSet<Int>,
        elapsedMillis: Long,
    ): ClockReductionResult {
        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        if (
            shouldForceAutomaticTurnForMarkedCurrentPlayer(
                runtimeState = runtimeState,
                automaticSeatIndexes = automaticSeatIndexes,
            )
        ) {
            return ClockReductionResult(
                runtimeState = forceAutomaticTurnForCurrentPlayer(
                    runtimeState = runtimeState,
                ),
                turnWasResolved = true,
            )
        }

        val currentPlayerWasAlreadyExpired =
            shouldForceAutomaticTurnForExpiredCurrentPlayer(
                runtimeState = runtimeState,
            )

        val clockedRuntimeState = reduceOnlineAuthoritativeClock(
            runtimeState = runtimeState,
            elapsedMillis = elapsedMillis,
        )

        val turnWasResolved = wasTurnResolvedByClockOrTimeout(
            previousRuntimeState = runtimeState,
            updatedRuntimeState = clockedRuntimeState,
        )

        if (currentPlayerWasAlreadyExpired || turnWasResolved) {
            automaticSeatIndexes.add(currentPlayerIndex)
        }

        if (turnWasResolved) {
            return ClockReductionResult(
                runtimeState = clockedRuntimeState,
                turnWasResolved = true,
            )
        }

        val currentPlayerIsNowExpired =
            shouldForceAutomaticTurnForExpiredCurrentPlayer(
                runtimeState = clockedRuntimeState,
            )

        if (!currentPlayerIsNowExpired) {
            return ClockReductionResult(
                runtimeState = clockedRuntimeState,
                turnWasResolved = false,
            )
        }

        automaticSeatIndexes.add(currentPlayerIndex)

        return ClockReductionResult(
            runtimeState = forceAutomaticTurnForCurrentPlayer(
                runtimeState = clockedRuntimeState,
            ),
            turnWasResolved = true,
        )
    }

    private fun shouldForceAutomaticTurnForMarkedCurrentPlayer(
        runtimeState: DominoMatchRuntimeState,
        automaticSeatIndexes: Set<Int>,
    ): Boolean {
        val gameState = runtimeState.gameState

        if (isRoundFinished(gameState) || isGameFinished(gameState)) {
            return false
        }

        if (gameState.currentPlayerIndex !in automaticSeatIndexes) {
            return false
        }

        return when (val phase = runtimeState.phase) {
            DominoMatchPhase.WaitingForLocalMove -> true

            is DominoMatchPhase.PresentingPass -> {
                phase.playerIndex == gameState.currentPlayerIndex
            }

            else -> false
        }
    }

    private fun shouldForceAutomaticTurnForExpiredCurrentPlayer(
        runtimeState: DominoMatchRuntimeState,
    ): Boolean {
        if (!runtimeState.clockPolicy.enabled) {
            return false
        }

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return false
        }

        val gameState = runtimeState.gameState

        if (isRoundFinished(gameState) || isGameFinished(gameState)) {
            return false
        }

        return isPlayerClockExpired(
            clocks = runtimeState.playerClockMillis,
            playerIndex = gameState.currentPlayerIndex,
        )
    }

    private fun forceAutomaticTurnForCurrentPlayer(
        runtimeState: DominoMatchRuntimeState,
    ): DominoMatchRuntimeState {
        val updatedGameState = when (val phase = runtimeState.phase) {
            DominoMatchPhase.WaitingForLocalMove -> {
                val move = findRandomPlayableMove(
                    state = runtimeState.gameState,
                )

                if (move != null) {
                    playMoveForCurrentPlayer(
                        state = runtimeState.gameState,
                        playableMove = move,
                    )
                } else {
                    passTurn(
                        state = runtimeState.gameState,
                    )
                }
            }

            is DominoMatchPhase.PresentingPass -> {
                if (phase.playerIndex == runtimeState.gameState.currentPlayerIndex) {
                    passTurn(
                        state = runtimeState.gameState,
                    )
                } else {
                    runtimeState.gameState
                }
            }

            else -> runtimeState.gameState
        }

        return runtimeState.copy(
            gameState = updatedGameState,
            phase = determineOnlineNextPhase(
                gameState = updatedGameState,
            ),
        )
    }

    private fun wasTurnResolvedByClockOrTimeout(
        previousRuntimeState: DominoMatchRuntimeState,
        updatedRuntimeState: DominoMatchRuntimeState,
    ): Boolean {
        return updatedRuntimeState.gameState != previousRuntimeState.gameState ||
                updatedRuntimeState.phase != previousRuntimeState.phase
    }

    private fun publishMatchSnapshot(
        matchRecord: MatchRecord,
        previousSnapshot: OnlineMatchSnapshotDto,
        runtimeState: DominoMatchRuntimeState,
        serverEpochMillis: Long = nowEpochMillis(),
    ): OnlineActionResultDto {
        val updatedSnapshot = runtimeState.toOnlineSnapshotDto(
            roomId = previousSnapshot.roomId,
            matchId = previousSnapshot.matchId,
            revision = previousSnapshot.revision + 1L,
            serverEpochMillis = serverEpochMillis,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes.sorted(),
        )

        matchRecord.snapshot = updatedSnapshot

        val currentRoom = roomsById[matchRecord.roomId]

        if (currentRoom != null) {
            roomsById[currentRoom.roomId] = currentRoom.copy(
                status = if (
                    runtimeState.gameState.gameWinnerTeamIndex != null
                ) {
                    OnlineRoomStatusDto.FINISHED
                } else {
                    OnlineRoomStatusDto.IN_MATCH
                },
                updatedAtEpochMillis = serverEpochMillis,
            )
        }

        return OnlineActionResultDto(
            accepted = true,
            revision = updatedSnapshot.revision,
        )
    }

    private fun markPlayerDisconnected(
        roomId: String,
        playerId: String,
    ) {
        val currentRoom = roomsById[roomId] ?: return

        roomsById[roomId] = currentRoom.copy(
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

    private fun getElapsedMillisSinceSnapshot(
        snapshot: OnlineMatchSnapshotDto,
        nowEpochMillis: Long,
    ): Long {
        val previousEpochMillis = snapshot.serverEpochMillis
            ?: return 0L

        return (nowEpochMillis - previousEpochMillis)
            .coerceAtLeast(0L)
    }

    private fun cacheActionResult(
        action: OnlinePlayerActionDto,
        result: OnlineActionResultDto,
    ): OnlineActionResultDto {
        val cachedResult = result.copy(
            actionId = action.actionId,
        )

        actionResultsById[action.actionId] = cachedResult

        return cachedResult
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
        revision: Long? = null,
    ): OnlineActionResultDto {
        return OnlineActionResultDto(
            accepted = false,
            revision = revision,
            reason = reason,
        )
    }

    private fun isDevelopmentBotPlayerId(
        playerId: String,
    ): Boolean {
        return playerId.startsWith(
            DEVELOPMENT_BOT_PLAYER_ID_PREFIX,
        )
    }
}