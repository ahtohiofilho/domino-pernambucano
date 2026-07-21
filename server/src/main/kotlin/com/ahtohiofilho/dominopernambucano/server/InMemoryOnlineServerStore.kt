package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchMetricAccumulator
import com.ahtohiofilho.dominopernambucano.competitive.accumulateRankedMatchTransition
import com.ahtohiofilho.dominopernambucano.competitive.didRankedSeatPlayPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
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
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.projectForParticipant
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.applyOnlineRoomPlayerNames
import com.ahtohiofilho.dominopernambucano.online.determineOnlineNextPhase
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.createOnlineTraceStateFingerprint
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceStateSummary
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
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

/*
 * O store de desenvolvimento preserva uma janela de revisÃµes por partida para
 * que o cliente apresente cada transiÃ§Ã£o, em vez de pular ao snapshot atual.
 */
private const val MATCH_REVISION_HISTORY_CAPACITY = 2_048
private const val MAX_SERVER_IDENTIFIER_CHARACTERS = 160
private const val MAX_SERVER_PLAYER_NAME_CHARACTERS = 160
private const val MAX_SERVER_ROOM_CODE_CHARACTERS = 16

class InMemoryOnlineServerStore(
    private val clockPolicy: DominoMatchClockPolicy =
        DominoMatchClockPolicy.OnlinePerPlayerRound,
    private val autoFillDevelopmentBotsAfterTwoHumanPlayers: Boolean = false,
    private val resourcePolicy: OnlineServerStoreResourcePolicy =
        OnlineServerStoreResourcePolicy.Default,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val traceLogger: OnlineTraceLogger = OnlineTraceLogger(
        nowEpochMillis = nowEpochMillis,
    ),
) : OnlineServerStore {
    private data class MatchRecord(
        val roomId: String,
        val matchId: String,
        var snapshot: OnlineMatchSnapshotDto,
        val revisionHistory: ArrayDeque<OnlineMatchSnapshotDto> = ArrayDeque(),
        val automaticSeatIndexes: MutableSet<Int> = mutableSetOf(),
        val automaticRoundSeatIndexes: MutableSet<Int> = mutableSetOf(),
        val developmentBotSeatIndexes: Set<Int> = emptySet(),
        var rankedMetricAccumulator: RankedMatchMetricAccumulator,
    )

    private data class ClockReductionResult(
        val runtimeState: DominoMatchRuntimeState,
        val turnWasResolved: Boolean,
    )

    private data class ActionResultCacheKey(
        val matchId: String,
        val playerId: String,
        val actionId: String,
    )

    private val lock = Any()

    private val roomsById = mutableMapOf<String, OnlineRoomSnapshotDto>()
    private val roomIdsByCode = mutableMapOf<String, String>()
    private val matchesById = mutableMapOf<String, MatchRecord>()
    private val actionResultsByKey =
        mutableMapOf<ActionResultCacheKey, OnlineActionResultDto>()

    private var nextRoomSequence = 1
    private var nextMatchSequence = 1
    private var lastPruneAtEpochMillis: Long? = null

    override fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return synchronized(lock) {
            pruneExpiredRecords(
                nowEpochMillis = nowEpochMillis(),
                force = true,
            )

            if (roomsById.size >= resourcePolicy.maxRoomCount) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Capacidade temporária de salas atingida.",
                )
            }

            if (request.localPlayerId.isBlank()) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Identificador do jogador não informado.",
                )
            }

            if (
                request.localPlayerId.length >
                MAX_SERVER_IDENTIFIER_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Identificador do jogador acima do limite.",
                )
            }

            if (request.playerName.isBlank()) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Nome do jogador não informado.",
                )
            }

            if (
                request.playerName.length >
                MAX_SERVER_PLAYER_NAME_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Nome do jogador acima do limite.",
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
                        participantType =
                            OnlineParticipantTypeDto.HUMAN,
                    ),
                ),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )

            roomsById[roomId] = room
            roomIdsByCode[roomCode] = roomId

            trace(
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.SERVER_STORE,
                type = OnlineTraceType.ROOM_CREATED,
                roomId = roomId,
                playerId = request.localPlayerId,
                localSeatIndex = 0,
                attributes = room.traceAttributes() + mapOf(
                    "operation" to "create_room",
                ),
            )

            OnlineRoomOperationResultDto(
                accepted = true,
                roomSnapshot = room,
                localSeatIndex = 0,
            )
        }
    }

    override fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return synchronized(lock) {
            pruneExpiredRecords(
                nowEpochMillis = nowEpochMillis(),
                force = true,
            )

            val normalizedRoomCode = request.roomCode.trim()

            if (
                normalizedRoomCode.isBlank() ||
                normalizedRoomCode.length >
                MAX_SERVER_ROOM_CODE_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    playerId = request.localPlayerId,
                    reason = "Código de sala inválido.",
                )
            }

            val roomId = roomIdsByCode[normalizedRoomCode]
                ?: return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    playerId = request.localPlayerId,
                    reason = "Código de sala inválido.",
                )

            val currentRoom = roomsById[roomId]
                ?: return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = roomId,
                    playerId = request.localPlayerId,
                    reason = "Sala não encontrada.",
                )

            if (
                currentRoom.status == OnlineRoomStatusDto.CLOSED ||
                currentRoom.status == OnlineRoomStatusDto.FINISHED
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "A sala não está mais disponível.",
                )
            }

            if (request.localPlayerId.isBlank()) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    reason = "Identificador do jogador não informado.",
                )
            }

            if (
                request.localPlayerId.length >
                MAX_SERVER_IDENTIFIER_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "Identificador do jogador acima do limite.",
                )
            }

            if (request.playerName.isBlank()) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "Nome do jogador não informado.",
                )
            }

            if (
                request.playerName.length >
                MAX_SERVER_PLAYER_NAME_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "Nome do jogador acima do limite.",
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

                val controlReclaimed = reclaimHumanSeatControlIfNeeded(
                    room = updatedRoom,
                    player = existingPlayer,
                )

                trace(
                    level = OnlineTraceLevel.INFO,
                    source = OnlineTraceSource.SERVER_STORE,
                    type = OnlineTraceType.ROOM_JOINED,
                    roomId = updatedRoom.roomId,
                    matchId = updatedRoom.matchId,
                    playerId = request.localPlayerId,
                    localSeatIndex = existingPlayer.seatIndex,
                    attributes = updatedRoom.traceAttributes() + mapOf(
                        "operation" to "join_room",
                        "reconnected" to "true",
                        "controlReclaimed" to controlReclaimed.toString(),
                    ),
                )

                return@synchronized OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = updatedRoom,
                    localSeatIndex = existingPlayer.seatIndex,
                )
            }

            if (currentRoom.status == OnlineRoomStatusDto.IN_MATCH) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "A partida já foi iniciada.",
                )
            }

            val occupiedSeats = currentRoom.players
                .mapNotNull { player -> player.seatIndex }
                .toSet()

            val nextSeatIndex = (0..3).firstOrNull { seatIndex ->
                seatIndex !in occupiedSeats
            } ?: return@synchronized rejectedRoomOperationWithTrace(
                operation = "join_room",
                roomId = currentRoom.roomId,
                matchId = currentRoom.matchId,
                playerId = request.localPlayerId,
                reason = "A sala já está cheia.",
            )

            val playersAfterHumanJoin = currentRoom.players + OnlineRoomPlayerDto(
                playerId = request.localPlayerId,
                name = request.playerName,
                seatIndex = nextSeatIndex,
                connected = true,
                participantType =
                    OnlineParticipantTypeDto.HUMAN,
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

            trace(
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.SERVER_STORE,
                type = OnlineTraceType.ROOM_JOINED,
                roomId = updatedRoom.roomId,
                matchId = updatedRoom.matchId,
                playerId = request.localPlayerId,
                localSeatIndex = nextSeatIndex,
                attributes = updatedRoom.traceAttributes() + mapOf(
                    "operation" to "join_room",
                    "reconnected" to "false",
                    "matchStarted" to shouldStartMatch.toString(),
                ),
            )

            OnlineRoomOperationResultDto(
                accepted = true,
                roomSnapshot = updatedRoom,
                localSeatIndex = nextSeatIndex,
            )
        }
    }

    override fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        return synchronized(lock) {
            trace(
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.SERVER_STORE,
                type = OnlineTraceType.ACTION_SUBMITTED,
                action = action,
                attributes = action.traceAttributes(),
            )

            val invalidActionReason = action.validationReasonOrNull()

            if (invalidActionReason != null) {
                val result = rejectedAction(
                    reason = invalidActionReason,
                ).copy(
                    actionId = action.actionId,
                )

                trace(
                    level = OnlineTraceLevel.WARN,
                    source = OnlineTraceSource.SERVER_STORE,
                    type = OnlineTraceType.ACTION_REJECTED,
                    action = action,
                    attributes = action.traceAttributes() + mapOf(
                        "reason" to invalidActionReason,
                    ),
                )

                return@synchronized result
            }

            actionResultsByKey[action.toActionResultCacheKey()]?.let { cachedResult ->
                trace(
                    level = OnlineTraceLevel.INFO,
                    source = OnlineTraceSource.SERVER_STORE,
                    type = OnlineTraceType.ACTION_DEDUPLICATED,
                    action = action,
                    snapshotRevision = cachedResult.revision,
                    attributes = action.traceAttributes() + mapOf(
                        "cachedAccepted" to cachedResult.accepted.toString(),
                    ),
                )

                return@synchronized cachedResult
            }

            val currentRoom = roomsById[action.roomId]
                ?: return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "Sala inválida.",
                )

            val matchRecord = matchesById[action.matchId]
                ?: return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "Partida inválida.",
                )

            if (matchRecord.roomId != currentRoom.roomId) {
                return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "A partida não pertence à sala informada.",
                    revision = matchRecord.snapshot.revision,
                )
            }

            val roomPlayer = currentRoom.players.firstOrNull { player ->
                player.playerId == action.playerId
            } ?: return@synchronized cacheRejectedAction(
                action = action,
                reason = "Jogador não encontrado na sala.",
                revision = matchRecord.snapshot.revision,
            )

            val seatIndex = roomPlayer.seatIndex
                ?: return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "Jogador sem assento definido.",
                    revision = matchRecord.snapshot.revision,
                )

            if (!roomPlayer.connected) {
                return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "Jogador desconectado.",
                    revision = matchRecord.snapshot.revision,
                )
            }

            if (
                action.type == OnlinePlayerActionTypeDto.PLAY_MOVE ||
                action.type == OnlinePlayerActionTypeDto.PASS_TURN
            ) {
                /*
                 * Fecha a pequena janela entre ticks: uma jogada enviada no
                 * instante do timeout ainda é comparada ao relógio do servidor
                 * antes de o redutor validar a ação humana.
                 */
                advanceAuthoritativeMatch(
                    matchRecord = matchRecord,
                    nowEpochMillis = nowEpochMillis(),
                    trigger = "action_pre_validation",
                    traceSource = OnlineTraceSource.SERVER_STORE,
                )
            }

            val result = when (action.type) {
                /*
                 * Mantido temporariamente no contrato para compatibilidade de
                 * versões. Não conduz mais relógio, bot ou troca de turno.
                 */
                OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT -> {
                    OnlineActionResultDto(
                        accepted = true,
                        revision = matchRecord.snapshot.revision,
                    )
                }

                OnlinePlayerActionTypeDto.LEAVE_ROOM -> {
                    markPlayerDisconnected(
                        roomId = currentRoom.roomId,
                        playerId = action.playerId,
                    )

                    trace(
                        level = OnlineTraceLevel.INFO,
                        source = OnlineTraceSource.SERVER_STORE,
                        type = OnlineTraceType.ROOM_LEFT,
                        action = action,
                        snapshotRevision = matchRecord.snapshot.revision,
                        attributes = action.traceAttributes() + mapOf(
                            "operation" to "leave_room",
                        ),
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

            val cachedResult = cacheActionResult(
                action = action,
                result = result,
            )

            traceActionResult(
                action = action,
                result = cachedResult,
                matchRecord = matchRecord,
                localSeatIndex = seatIndex,
            )

            cachedResult
        }
    }

    /**
     * Avança a partida a partir do relógio do próprio servidor.
     *
     * Clientes nunca chamam este método por HTTP. O ticker do processo o invoca
     * em cadência fixa, garantindo que polling e visualização sejam somente
     * observacionais. Cada chamada publica no máximo uma transição por partida,
     * preservando uma cadência consumível pela fila visual dos clientes.
     */
    override fun advanceAuthoritativeTime(): Boolean {
        return synchronized(lock) {
            val now = nowEpochMillis()
            var stateChanged = pruneExpiredRecords(
                nowEpochMillis = now,
            )

            matchesById.values.forEach { matchRecord ->
                val previousRevision = matchRecord.snapshot.revision

                val changed = advanceAuthoritativeMatch(
                    matchRecord = matchRecord,
                    nowEpochMillis = now,
                    trigger = "ticker",
                    traceSource = OnlineTraceSource.SERVER_TICKER,
                )

                if (changed) {
                    stateChanged = true
                    trace(
                        level = OnlineTraceLevel.INFO,
                        source = OnlineTraceSource.SERVER_TICKER,
                        type = OnlineTraceType.AUTHORITATIVE_TICK,
                        roomId = matchRecord.roomId,
                        matchId = matchRecord.matchId,
                        snapshotRevision = matchRecord.snapshot.revision,
                        runtimeState = matchRecord.snapshot.toRuntimeState(
                            localPlayerIndex = 0,
                        ),
                        automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
                        attributes = mapOf(
                            "previousRevision" to previousRevision.toString(),
                            "trigger" to "ticker",
                        ),
                    )
                }
            }

            stateChanged
        }
    }

    override fun getRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto? {
        return synchronized(lock) {
            roomsById[roomId]
        }
    }

    override fun isRoomParticipant(
        roomId: String,
        playerId: String,
    ): Boolean {
        return synchronized(lock) {
            roomsById[roomId]
                ?.players
                ?.any { player ->
                    player.playerId == playerId
                } == true
        }
    }

    override fun isMatchParticipant(
        matchId: String,
        playerId: String,
    ): Boolean {
        return synchronized(lock) {
            val matchRecord = matchesById[matchId] ?: return@synchronized false
            val room = roomsById[matchRecord.roomId] ?: return@synchronized false

            room.players.any { player ->
                player.playerId == playerId
            }
        }
    }

    override fun getMatchSnapshotForParticipant(
        matchId: String,
        playerId: String,
    ): OnlineMatchSnapshotDto? {
        return synchronized(lock) {
            val matchRecord = matchesById[matchId]
                ?: return@synchronized null
            val room = roomsById[matchRecord.roomId]
                ?: return@synchronized null
            val seatIndex = room.players
                .firstOrNull { player ->
                    player.playerId == playerId
                }
                ?.seatIndex
                ?: return@synchronized null

            matchRecord.snapshot.projectForParticipant(
                seatIndex = seatIndex,
            )
        }
    }

    override fun getMatchSnapshotsAfterForParticipant(
        matchId: String,
        playerId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>? {
        return synchronized(lock) {
            val matchRecord = matchesById[matchId]
                ?: return@synchronized null
            val room = roomsById[matchRecord.roomId]
                ?: return@synchronized null
            val seatIndex = room.players
                .firstOrNull { player ->
                    player.playerId == playerId
                }
                ?.seatIndex
                ?: return@synchronized null

            matchRecord.revisionHistory
                .filter { snapshot ->
                    snapshot.revision > afterRevision
                }
                .map { snapshot ->
                    snapshot.projectForParticipant(
                        seatIndex = seatIndex,
                    )
                }
        }
    }

    /**
     * Leitura observacional do estado autoritativo.
     *
     * Buscar um snapshot nunca reduz relógio, move bot, resolve toque ou cria
     * revisão. A mutação é exclusividade de [advanceAuthoritativeTime] e das
     * ações explícitas de jogo aceitas pelo servidor.
     */
    override fun getMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto? {
        return synchronized(lock) {
            matchesById[matchId]?.snapshot
        }
    }

    /**
     * Retorna em ordem todas as revisÃµes posteriores Ã  referÃªncia do cliente.
     * A consulta Ã© observacional e nÃ£o avança relÃ³gios ou turnos.
     */
    override fun getMatchSnapshotsAfter(
        matchId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>? {
        return synchronized(lock) {
            matchesById[matchId]
                ?.revisionHistory
                ?.filter { snapshot ->
                    snapshot.revision > afterRevision
                }
                ?.toList()
        }
    }

    internal fun getRankedMatchMetricAccumulator(
        matchId: String,
    ): RankedMatchMetricAccumulator? {
        return synchronized(lock) {
            matchesById[matchId]?.rankedMetricAccumulator
        }
    }

    internal fun snapshotPersistentState(): OnlineServerStoreState {
        return synchronized(lock) {
            OnlineServerStoreState(
                nextRoomSequence = nextRoomSequence,
                nextMatchSequence = nextMatchSequence,
                rooms = roomsById.values.sortedBy { room ->
                    room.roomId
                },
                matches = matchesById.values
                    .sortedBy { matchRecord ->
                        matchRecord.matchId
                    }
                    .map { matchRecord ->
                        OnlineServerStoredMatch(
                            roomId = matchRecord.roomId,
                            matchId = matchRecord.matchId,
                            snapshot = matchRecord.snapshot,
                            revisionHistory =
                                matchRecord.revisionHistory.toList(),
                            automaticSeatIndexes =
                                matchRecord.automaticSeatIndexes.sorted(),
                            applicationSeatIndexes =
                                matchRecord.developmentBotSeatIndexes.sorted(),
                        )
                    },
                actionResults = actionResultsByKey.entries
                    .map { (key, result) ->
                        OnlineServerStoredActionResult(
                            matchId = key.matchId,
                            playerId = key.playerId,
                            actionId = key.actionId,
                            result = result,
                        )
                    },
            )
        }
    }

    internal fun restorePersistentState(
        state: OnlineServerStoreState,
    ) {
        synchronized(lock) {
            validatePersistentState(state)

            roomsById.clear()
            roomIdsByCode.clear()
            matchesById.clear()
            actionResultsByKey.clear()

            state.rooms.forEach { room ->
                roomsById[room.roomId] = room
                roomIdsByCode[room.roomCode] = room.roomId
            }

            state.matches.forEach { storedMatch ->
                matchesById[storedMatch.matchId] = MatchRecord(
                    roomId = storedMatch.roomId,
                    matchId = storedMatch.matchId,
                    snapshot = storedMatch.snapshot,
                    revisionHistory =
                        ArrayDeque<OnlineMatchSnapshotDto>().apply {
                            addAll(storedMatch.revisionHistory)
                        },
                    automaticSeatIndexes = storedMatch
                        .automaticSeatIndexes
                        .toMutableSet(),
                    developmentBotSeatIndexes = storedMatch
                        .applicationSeatIndexes
                        .toSet(),
                    rankedMetricAccumulator =
                        RankedMatchMetricAccumulator.empty(
                            playerCount = storedMatch
                                .snapshot
                                .gameState
                                .players
                                .size,
                            teamCount = storedMatch
                                .snapshot
                                .gameState
                                .teamScores
                                .size,
                        ),
                )
            }

            state.actionResults.forEach { storedAction ->
                actionResultsByKey[
                    ActionResultCacheKey(
                        matchId = storedAction.matchId,
                        playerId = storedAction.playerId,
                        actionId = storedAction.actionId,
                    )
                ] = storedAction.result
            }

            nextRoomSequence = state.nextRoomSequence
            nextMatchSequence = state.nextMatchSequence
            lastPruneAtEpochMillis = null
        }
    }

    private fun validatePersistentState(
        state: OnlineServerStoreState,
    ) {
        require(
            state.schemaVersion ==
                    ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION
        ) {
            "Versão de estado autoritativo não suportada: " +
                    state.schemaVersion
        }
        require(state.nextRoomSequence > 0)
        require(state.nextMatchSequence > 0)
        require(state.rooms.size <= resourcePolicy.maxRoomCount) {
            "O estado persistido excede a capacidade de salas."
        }
        require(
            state.actionResults.size <=
                    resourcePolicy.maxActionResultCount
        ) {
            "O estado persistido excede a capacidade de idempotência."
        }

        val roomIds = state.rooms.map { room -> room.roomId }
        val roomCodes = state.rooms.map { room -> room.roomCode }
        val matchIds = state.matches.map { match -> match.matchId }

        require(roomIds.none { roomId -> roomId.isBlank() })
        require(roomIds.distinct().size == roomIds.size)
        require(roomCodes.none { roomCode -> roomCode.isBlank() })
        require(roomCodes.distinct().size == roomCodes.size)
        require(matchIds.none { matchId -> matchId.isBlank() })
        require(matchIds.distinct().size == matchIds.size)

        val roomsByPersistedId = state.rooms.associateBy { room ->
            room.roomId
        }
        val matchesByPersistedId = state.matches.associateBy { match ->
            match.matchId
        }

        state.matches.forEach { storedMatch ->
            val room = requireNotNull(
                roomsByPersistedId[storedMatch.roomId],
            ) {
                "Partida persistida referencia sala inexistente."
            }

            require(room.matchId == storedMatch.matchId)
            require(storedMatch.snapshot.roomId == storedMatch.roomId)
            require(storedMatch.snapshot.matchId == storedMatch.matchId)
            require(storedMatch.revisionHistory.isNotEmpty())
            require(
                storedMatch.revisionHistory.size <=
                        MATCH_REVISION_HISTORY_CAPACITY
            )
            require(
                storedMatch.revisionHistory.last() ==
                        storedMatch.snapshot
            )
            require(
                storedMatch.revisionHistory
                    .zipWithNext()
                    .all { (previous, next) ->
                        next.revision == previous.revision + 1L
                    }
            )
            require(
                storedMatch.automaticSeatIndexes.all { index ->
                    index in 0..3
                }
            )
            require(
                storedMatch.applicationSeatIndexes.all { index ->
                    index in 0..3
                }
            )
        }

        state.rooms.forEach { room ->
            room.matchId?.let { matchId ->
                require(matchesByPersistedId[matchId]?.roomId == room.roomId)
            }
        }

        val actionKeys = state.actionResults.map { storedAction ->
            Triple(
                storedAction.matchId,
                storedAction.playerId,
                storedAction.actionId,
            )
        }

        require(actionKeys.distinct().size == actionKeys.size)
        require(
            state.actionResults.all { storedAction ->
                storedAction.matchId.isNotBlank() &&
                        storedAction.playerId.isNotBlank() &&
                        storedAction.actionId.isNotBlank() &&
                        storedAction.result.actionId == storedAction.actionId
            }
        )

        val maximumRoomSequence = roomIds.maxOfOrNull { roomId ->
            roomId.removePrefix("server-room-").toIntOrNull() ?: 0
        } ?: 0
        val maximumMatchSequence = matchIds.maxOfOrNull { matchId ->
            matchId.removePrefix("server-match-").toIntOrNull() ?: 0
        } ?: 0

        require(state.nextRoomSequence > maximumRoomSequence)
        require(state.nextMatchSequence > maximumMatchSequence)
    }

    private fun pruneExpiredRecords(
        nowEpochMillis: Long,
        force: Boolean = false,
    ): Boolean {
        val previousPruneAtEpochMillis = lastPruneAtEpochMillis

        if (
            !force &&
            previousPruneAtEpochMillis != null &&
            nowEpochMillis >= previousPruneAtEpochMillis &&
            nowEpochMillis - previousPruneAtEpochMillis <
            resourcePolicy.pruneIntervalMillis
        ) {
            return false
        }

        lastPruneAtEpochMillis = nowEpochMillis

        val expiredRoomIds = roomsById.values
            .filter { room ->
                val lastUpdatedAtEpochMillis =
                    room.updatedAtEpochMillis
                        ?: room.createdAtEpochMillis
                        ?: return@filter false
                val ageMillis = (
                    nowEpochMillis - lastUpdatedAtEpochMillis
                ).coerceAtLeast(0L)

                when (room.status) {
                    OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
                        ageMillis >=
                                resourcePolicy.waitingRoomRetentionMillis
                    }

                    OnlineRoomStatusDto.FINISHED,
                    OnlineRoomStatusDto.CLOSED -> {
                        ageMillis >=
                                resourcePolicy.finalizedRoomRetentionMillis
                    }

                    OnlineRoomStatusDto.IN_MATCH -> false
                }
            }
            .map { room -> room.roomId }
            .toSet()

        if (expiredRoomIds.isEmpty()) {
            return false
        }

        val expiredMatchIds = matchesById.values
            .filter { matchRecord ->
                matchRecord.roomId in expiredRoomIds
            }
            .map { matchRecord ->
                matchRecord.matchId
            }
            .toSet()

        roomIdsByCode.entries.removeAll { (_, roomId) ->
            roomId in expiredRoomIds
        }
        expiredRoomIds.forEach { roomId ->
            roomsById.remove(roomId)
        }
        expiredMatchIds.forEach { matchId ->
            matchesById.remove(matchId)
        }
        actionResultsByKey.keys.removeAll { key ->
            key.matchId in expiredMatchIds
        }

        return true
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
                        participantType =
                            OnlineParticipantTypeDto.APPLICATION,
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

        val matchRecord = MatchRecord(
            roomId = room.roomId,
            matchId = matchId,
            snapshot = snapshot,
            developmentBotSeatIndexes = room.players
                .filter { player ->
                    player.participantType ==
                        OnlineParticipantTypeDto.APPLICATION
                }
                .mapNotNull { player ->
                    player.seatIndex
                }
                .toSet(),
            rankedMetricAccumulator =
                RankedMatchMetricAccumulator.empty(
                    playerCount = gameState.players.size,
                    teamCount = gameState.teamScores.size,
                ),
        )

        recordSnapshotInHistory(
            matchRecord = matchRecord,
            snapshot = snapshot,
        )

        matchesById[matchId] = matchRecord

        trace(
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.SNAPSHOT_PUBLISHED,
            roomId = room.roomId,
            matchId = matchId,
            snapshotRevision = snapshot.revision,
            runtimeState = runtimeState,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = mapOf(
                "trigger" to "match_created",
                "previousRevision" to "null",
            ),
        )
    }

    /**
     * Processa no máximo uma transição autoritativa da partida.
     *
     * A redução parcial de relógio não gera snapshot novo; o cliente mantém a
     * contagem visual local entre revisões. Uma nova revisão é publicada apenas
     * quando há troca de estado de jogo: jogada automática, toque, timeout ou
     * ação de bot.
     */
    private fun advanceAuthoritativeMatch(
        matchRecord: MatchRecord,
        nowEpochMillis: Long,
        trigger: String,
        traceSource: OnlineTraceSource,
    ): Boolean {
        val currentSnapshot = matchRecord.snapshot
        val runtimeState = currentSnapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        val mandatoryPassRuntimeState = resolveMandatoryPassIfNeeded(
            runtimeState = runtimeState,
        )

        if (mandatoryPassRuntimeState != null) {
            trace(
                level = OnlineTraceLevel.INFO,
                source = traceSource,
                type = OnlineTraceType.AUTOMATIC_TURN_RESOLVED,
                roomId = matchRecord.roomId,
                matchId = matchRecord.matchId,
                snapshotRevision = currentSnapshot.revision,
                runtimeState = mandatoryPassRuntimeState,
                automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
                attributes = mapOf(
                    "playerIndex" to currentPlayerIndex.toString(),
                    "reason" to "mandatory_pass",
                    "trigger" to trigger,
                ),
            )

            publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = mandatoryPassRuntimeState,
                serverEpochMillis = nowEpochMillis,
                trigger = "$trigger:mandatory_pass",
                traceSource = traceSource,
            )

            return true
        }

        val automaticSeatIndexesBefore =
            matchRecord.automaticSeatIndexes.toSet()

        val clockReduction = reduceClockAndRegisterAutomaticPlayer(
            runtimeState = runtimeState,
            automaticSeatIndexes = matchRecord.automaticSeatIndexes,
            automaticRoundSeatIndexes =
                matchRecord.automaticRoundSeatIndexes,
            elapsedMillis = getElapsedMillisSinceSnapshot(
                snapshot = currentSnapshot,
                nowEpochMillis = nowEpochMillis,
            ),
        )

        if (clockReduction.turnWasResolved) {
            val currentPlayerBecameAutomatic =
                currentPlayerIndex !in automaticSeatIndexesBefore &&
                        currentPlayerIndex in matchRecord.automaticSeatIndexes

            if (currentPlayerBecameAutomatic) {
                trace(
                    level = OnlineTraceLevel.WARN,
                    source = traceSource,
                    type = OnlineTraceType.CLOCK_EXPIRED,
                    roomId = matchRecord.roomId,
                    matchId = matchRecord.matchId,
                    snapshotRevision = currentSnapshot.revision,
                    runtimeState = runtimeState,
                    automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
                    attributes = mapOf(
                        "playerIndex" to currentPlayerIndex.toString(),
                        "trigger" to trigger,
                    ),
                )
            }

            trace(
                level = OnlineTraceLevel.INFO,
                source = traceSource,
                type = OnlineTraceType.AUTOMATIC_TURN_RESOLVED,
                roomId = matchRecord.roomId,
                matchId = matchRecord.matchId,
                snapshotRevision = currentSnapshot.revision,
                runtimeState = clockReduction.runtimeState,
                automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
                attributes = mapOf(
                    "playerIndex" to currentPlayerIndex.toString(),
                    "reason" to if (currentPlayerBecameAutomatic) {
                        "clock_expired"
                    } else {
                        "automatic_seat"
                    },
                    "trigger" to trigger,
                ),
            )

            publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = clockReduction.runtimeState,
                serverEpochMillis = nowEpochMillis,
                trigger = "$trigger:automatic_turn",
                traceSource = traceSource,
            )

            return true
        }

        val runtimeStateAfterBotTurn =
            advanceDevelopmentBotTurnIfNeeded(
                matchRecord = matchRecord,
                runtimeState = clockReduction.runtimeState,
            )

        if (runtimeStateAfterBotTurn == clockReduction.runtimeState) {
            return false
        }

        trace(
            level = OnlineTraceLevel.INFO,
            source = traceSource,
            type = OnlineTraceType.AUTOMATIC_TURN_RESOLVED,
            roomId = matchRecord.roomId,
            matchId = matchRecord.matchId,
            snapshotRevision = currentSnapshot.revision,
            runtimeState = runtimeStateAfterBotTurn,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = mapOf(
                "playerIndex" to currentPlayerIndex.toString(),
                "reason" to "development_bot",
                "trigger" to trigger,
            ),
        )

        publishMatchSnapshot(
            matchRecord = matchRecord,
            previousSnapshot = currentSnapshot,
            runtimeState = runtimeStateAfterBotTurn,
            serverEpochMillis = nowEpochMillis,
            trigger = "$trigger:development_bot",
            traceSource = traceSource,
        )

        return true
    }

    private fun resolveMandatoryPassIfNeeded(
        runtimeState: DominoMatchRuntimeState,
    ): DominoMatchRuntimeState? {
        val phase = runtimeState.phase as? DominoMatchPhase.PresentingPass
            ?: return null

        val gameState = runtimeState.gameState

        if (
            isRoundFinished(gameState) ||
            isGameFinished(gameState) ||
            phase.playerIndex != gameState.currentPlayerIndex
        ) {
            return null
        }

        val passedGameState = passTurn(
            state = gameState,
        )

        return runtimeState.copy(
            gameState = passedGameState,
            phase = determineOnlineNextPhase(
                gameState = passedGameState,
            ),
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

        registerAutomaticPiecePlayIfNeeded(
            previousState = gameState,
            updatedState = updatedGameState,
            seatIndex = currentPlayerIndex,
            automaticRoundSeatIndexes =
                matchRecord.automaticRoundSeatIndexes,
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
            automaticRoundSeatIndexes =
                matchRecord.automaticRoundSeatIndexes,
            elapsedMillis = getElapsedMillisSinceSnapshot(
                snapshot = currentSnapshot,
                nowEpochMillis = now,
            ),
        )

        if (clockReduction.turnWasResolved) {
            val automaticResult = publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = clockReduction.runtimeState,
                serverEpochMillis = now,
                trigger = "game_action:automatic_turn",
            )

            return automaticResult.copy(
                accepted = false,
                reason = "Tempo esgotado.",
            )
        }

        val clockedSnapshot = clockReduction.runtimeState.toOnlineSnapshotDto(
            roomId = currentSnapshot.roomId,
            matchId = currentSnapshot.matchId,
            revision = currentSnapshot.revision,
            serverEpochMillis = now,
            automaticPlayerIndexes =
                matchRecord.automaticSeatIndexes.sorted(),
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
                    action = action,
                    trigger = "game_action",
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
                matchRecord.automaticRoundSeatIndexes.clear()

                publishMatchSnapshot(
                    matchRecord = matchRecord,
                    previousSnapshot = matchRecord.snapshot,
                    runtimeState = reduction.runtimeState,
                    action = action,
                    trigger = "start_next_round",
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
                matchRecord.automaticRoundSeatIndexes.clear()
                matchRecord.rankedMetricAccumulator =
                    RankedMatchMetricAccumulator.empty(
                        playerCount = reduction
                            .runtimeState
                            .gameState
                            .players
                            .size,
                        teamCount = reduction
                            .runtimeState
                            .gameState
                            .teamScores
                            .size,
                    )

                publishMatchSnapshot(
                    matchRecord = matchRecord,
                    previousSnapshot = matchRecord.snapshot,
                    runtimeState = reduction.runtimeState,
                    action = action,
                    trigger = "start_new_match",
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
        automaticRoundSeatIndexes: MutableSet<Int>,
        elapsedMillis: Long,
    ): ClockReductionResult {
        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        if (
            shouldForceAutomaticTurnForMarkedCurrentPlayer(
                runtimeState = runtimeState,
                automaticSeatIndexes = automaticSeatIndexes,
            )
        ) {
            val forcedRuntimeState = forceAutomaticTurnForCurrentPlayer(
                runtimeState = runtimeState,
            )

            registerAutomaticPiecePlayIfNeeded(
                previousState = runtimeState.gameState,
                updatedState = forcedRuntimeState.gameState,
                seatIndex = currentPlayerIndex,
                automaticRoundSeatIndexes = automaticRoundSeatIndexes,
            )

            return ClockReductionResult(
                runtimeState = forcedRuntimeState,
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
            registerAutomaticPiecePlayIfNeeded(
                previousState = runtimeState.gameState,
                updatedState = clockedRuntimeState.gameState,
                seatIndex = currentPlayerIndex,
                automaticRoundSeatIndexes = automaticRoundSeatIndexes,
            )

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

        val forcedRuntimeState = forceAutomaticTurnForCurrentPlayer(
            runtimeState = clockedRuntimeState,
        )

        registerAutomaticPiecePlayIfNeeded(
            previousState = clockedRuntimeState.gameState,
            updatedState = forcedRuntimeState.gameState,
            seatIndex = currentPlayerIndex,
            automaticRoundSeatIndexes = automaticRoundSeatIndexes,
        )

        return ClockReductionResult(
            runtimeState = forcedRuntimeState,
            turnWasResolved = true,
        )
    }

    private fun registerAutomaticPiecePlayIfNeeded(
        previousState: DominoGameState,
        updatedState: DominoGameState,
        seatIndex: Int,
        automaticRoundSeatIndexes: MutableSet<Int>,
    ) {
        if (
            didRankedSeatPlayPiece(
                previousState = previousState,
                updatedState = updatedState,
                seatIndex = seatIndex,
            )
        ) {
            automaticRoundSeatIndexes.add(seatIndex)
        }
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
        action: OnlinePlayerActionDto? = null,
        trigger: String,
        traceSource: OnlineTraceSource = OnlineTraceSource.SERVER_STORE,
    ): OnlineActionResultDto {
        matchRecord.rankedMetricAccumulator =
            accumulateRankedMatchTransition(
                accumulator = matchRecord.rankedMetricAccumulator,
                previousState = previousSnapshot.toRuntimeState(
                    localPlayerIndex = 0,
                ).gameState,
                updatedState = runtimeState.gameState,
                automaticSeatIndexes =
                    matchRecord.automaticRoundSeatIndexes,
            )

        val updatedSnapshot = runtimeState.toOnlineSnapshotDto(
            roomId = previousSnapshot.roomId,
            matchId = previousSnapshot.matchId,
            revision = previousSnapshot.revision + 1L,
            serverEpochMillis = serverEpochMillis,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes.sorted(),
        )

        matchRecord.snapshot = updatedSnapshot

        recordSnapshotInHistory(
            matchRecord = matchRecord,
            snapshot = updatedSnapshot,
        )

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

        trace(
            level = OnlineTraceLevel.INFO,
            source = traceSource,
            type = OnlineTraceType.SNAPSHOT_PUBLISHED,
            action = action,
            roomId = updatedSnapshot.roomId,
            matchId = updatedSnapshot.matchId,
            snapshotRevision = updatedSnapshot.revision,
            runtimeState = runtimeState,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = mapOf(
                "trigger" to trigger,
                "previousRevision" to previousSnapshot.revision.toString(),
            ),
        )

        return OnlineActionResultDto(
            accepted = true,
            revision = updatedSnapshot.revision,
        )
    }

    private fun recordSnapshotInHistory(
        matchRecord: MatchRecord,
        snapshot: OnlineMatchSnapshotDto,
    ) {
        matchRecord.revisionHistory.addLast(snapshot)

        while (
            matchRecord.revisionHistory.size >
            MATCH_REVISION_HISTORY_CAPACITY
        ) {
            matchRecord.revisionHistory.removeFirst()
        }
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

    /**
     * Devolve ao dono humano o controle temporariamente assumido pelo
     * servidor. A reconexão já ocorre sob o lock autoritativo, portanto a
     * troca acontece entre ações e nunca no meio de uma redução de jogada.
     *
     * O snapshot preserva o mesmo instante-base do relógio. Assim, publicar a
     * troca de controlador não concede tempo adicional ao jogador da vez.
     */
    private fun reclaimHumanSeatControlIfNeeded(
        room: OnlineRoomSnapshotDto,
        player: OnlineRoomPlayerDto,
    ): Boolean {
        if (
            room.status != OnlineRoomStatusDto.IN_MATCH ||
            player.participantType != OnlineParticipantTypeDto.HUMAN
        ) {
            return false
        }

        val matchId = room.matchId ?: return false
        val seatIndex = player.seatIndex ?: return false
        val matchRecord = matchesById[matchId] ?: return false

        if (!matchRecord.automaticSeatIndexes.remove(seatIndex)) {
            return false
        }

        val previousSnapshot = matchRecord.snapshot
        val updatedSnapshot = previousSnapshot.copy(
            revision = previousSnapshot.revision + 1L,
            automaticPlayerIndexes =
                matchRecord.automaticSeatIndexes.sorted(),
        )

        matchRecord.snapshot = updatedSnapshot
        recordSnapshotInHistory(
            matchRecord = matchRecord,
            snapshot = updatedSnapshot,
        )

        trace(
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.SNAPSHOT_PUBLISHED,
            roomId = room.roomId,
            matchId = matchId,
            playerId = player.playerId,
            localSeatIndex = seatIndex,
            snapshotRevision = updatedSnapshot.revision,
            runtimeState = updatedSnapshot.toRuntimeState(
                localPlayerIndex = seatIndex,
            ),
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = mapOf(
                "trigger" to "human_reconnected:control_reclaimed",
                "previousRevision" to
                    previousSnapshot.revision.toString(),
            ),
        )

        return true
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

    private fun OnlinePlayerActionDto.toActionResultCacheKey(): ActionResultCacheKey {
        return ActionResultCacheKey(
            matchId = matchId,
            playerId = playerId,
            actionId = actionId,
        )
    }

    private fun OnlinePlayerActionDto.validationReasonOrNull(): String? {
        val identifiers = listOf(
            "roomId" to roomId,
            "matchId" to matchId,
            "playerId" to playerId,
            "actionId" to actionId,
        )

        val invalidIdentifier = identifiers.firstOrNull { (_, value) ->
            value.isBlank() ||
                    value.length > MAX_SERVER_IDENTIFIER_CHARACTERS
        }

        if (invalidIdentifier != null) {
            return "${invalidIdentifier.first} ausente ou acima do limite."
        }

        if (revision < 0L) {
            return "Revisão negativa não é permitida."
        }

        return null
    }

    private fun cacheActionResult(
        action: OnlinePlayerActionDto,
        result: OnlineActionResultDto,
    ): OnlineActionResultDto {
        val cachedResult = result.copy(
            actionId = action.actionId,
        )

        actionResultsByKey[action.toActionResultCacheKey()] = cachedResult

        while (
            actionResultsByKey.size >
            resourcePolicy.maxActionResultCount
        ) {
            val iterator = actionResultsByKey.entries.iterator()

            check(iterator.hasNext()) {
                "O cache de idempotência perdeu seu estado."
            }

            iterator.next()
            iterator.remove()
        }

        return cachedResult
    }

    private fun cacheRejectedAction(
        action: OnlinePlayerActionDto,
        reason: String,
        revision: Long? = null,
    ): OnlineActionResultDto {
        val result = cacheActionResult(
            action = action,
            result = rejectedAction(
                reason = reason,
                revision = revision,
            ),
        )

        trace(
            level = OnlineTraceLevel.WARN,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.ACTION_REJECTED,
            action = action,
            snapshotRevision = result.revision,
            attributes = action.traceAttributes() + mapOf(
                "reason" to reason.take(180),
            ),
        )

        return result
    }

    private fun traceActionResult(
        action: OnlinePlayerActionDto,
        result: OnlineActionResultDto,
        matchRecord: MatchRecord,
        localSeatIndex: Int,
    ) {
        val runtimeState = matchRecord.snapshot.toRuntimeState(
            localPlayerIndex = localSeatIndex,
        )

        trace(
            level = if (result.accepted) {
                OnlineTraceLevel.INFO
            } else {
                OnlineTraceLevel.WARN
            },
            source = OnlineTraceSource.SERVER_STORE,
            type = if (result.accepted) {
                OnlineTraceType.ACTION_ACCEPTED
            } else {
                OnlineTraceType.ACTION_REJECTED
            },
            action = action,
            snapshotRevision = result.revision,
            runtimeState = runtimeState,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = action.traceAttributes() + mapOf(
                "reason" to result.reason.orEmpty().take(180),
            ),
        )
    }

    private fun rejectedRoomOperationWithTrace(
        operation: String,
        reason: String,
        roomId: String? = null,
        matchId: String? = null,
        playerId: String? = null,
    ): OnlineRoomOperationResultDto {
        trace(
            level = OnlineTraceLevel.WARN,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.ACTION_REJECTED,
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            attributes = mapOf(
                "operation" to operation,
                "reason" to reason.take(180),
            ),
        )

        return rejectedRoomOperation(
            reason = reason,
        )
    }

    private fun trace(
        level: OnlineTraceLevel,
        source: OnlineTraceSource,
        type: OnlineTraceType,
        action: OnlinePlayerActionDto? = null,
        roomId: String? = null,
        matchId: String? = null,
        playerId: String? = null,
        localSeatIndex: Int? = null,
        snapshotRevision: Long? = null,
        runtimeState: DominoMatchRuntimeState? = null,
        automaticPlayerIndexes: Set<Int> = emptySet(),
        attributes: Map<String, String> = emptyMap(),
    ) {
        traceLogger.log(
            level = level,
            source = source,
            type = type,
            context = OnlineTraceContext(
                roomId = roomId ?: action?.roomId,
                matchId = matchId ?: action?.matchId,
                playerId = playerId ?: action?.playerId,
                localSeatIndex = localSeatIndex,
                actionId = action?.actionId,
                actionRevision = action?.revision,
                snapshotRevision = snapshotRevision,
            ),
            state = runtimeState?.toTraceStateSummary(
                automaticPlayerIndexes = automaticPlayerIndexes,
            ),
            attributes = attributes,
        )
    }

    private fun DominoMatchRuntimeState.toTraceStateSummary(
        automaticPlayerIndexes: Set<Int>,
    ): OnlineTraceStateSummary {
        return OnlineTraceStateSummary(
            roundNumber = roundNumber,
            phase = phase.traceName(),
            currentPlayerIndex = gameState.currentPlayerIndex,
            boardPieceCount = gameState.board.size,
            teamScores = gameState.teamScores,
            playerClockMillis = playerClockMillis,
            automaticPlayerIndexes = automaticPlayerIndexes.sorted(),
            stateFingerprint = createOnlineTraceStateFingerprint(
                runtimeState = this,
                automaticPlayerIndexes = automaticPlayerIndexes,
            ),
        )
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

    private fun OnlineRoomSnapshotDto.traceAttributes(): Map<String, String> {
        return mapOf(
            "roomStatus" to status.name,
            "playerCount" to players.size.toString(),
            "roomCode" to roomCode,
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


}
