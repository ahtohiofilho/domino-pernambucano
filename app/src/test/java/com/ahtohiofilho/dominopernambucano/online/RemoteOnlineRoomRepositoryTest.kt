package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.online.observability.InMemoryOnlineTraceBuffer
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteOnlineRoomRepositoryTest {
    @Test
    fun create_room_updates_room_snapshot_without_match_snapshot_when_room_has_no_match() =
        runBlocking {
            val room = createWaitingRoomSnapshot()

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
            )

            val request = CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "Jogador 1",
            )

            val result = repository.createRoom(request)

            assertTrue(result.accepted)
            assertEquals(0, result.localSeatIndex)
            assertEquals(listOf(request), apiClient.createRoomRequests)
            assertEquals(room, repository.roomSnapshot.value)
            assertNull(repository.matchSnapshot.value)
            assertEquals(emptyList<String>(), apiClient.fetchMatchSnapshotRequests)
        }

    @Test
    fun create_room_fetches_match_snapshot_when_accepted_room_contains_match_id() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
            )

            val result = repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                )
            )

            assertTrue(result.accepted)
            assertEquals(room, repository.roomSnapshot.value)
            assertEquals(match, repository.matchSnapshot.value)
            assertEquals(listOf(match.matchId), apiClient.fetchMatchSnapshotRequests)
        }

    @Test
    fun join_room_updates_room_snapshot_and_fetches_match_snapshot_when_room_is_in_match() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 3L,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                joinRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 1,
                ),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
            )

            val request = JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "player-2",
                playerName = "Jogador 2",
            )

            val result = repository.joinRoom(request)

            assertTrue(result.accepted)
            assertEquals(1, result.localSeatIndex)
            assertEquals(listOf(request), apiClient.joinRoomRequests)
            assertEquals(room, repository.roomSnapshot.value)
            assertEquals(match, repository.matchSnapshot.value)
            assertEquals(listOf(match.matchId), apiClient.fetchMatchSnapshotRequests)
        }

    @Test
    fun submit_action_refreshes_room_and_match_snapshots_after_accepted_action() =
        runBlocking {
            val refreshedRoom = createInMatchRoomSnapshot(
                updatedAtEpochMillis = 2_000L,
            )

            val refreshedMatch = createMatchSnapshot(
                revision = 2L,
                serverEpochMillis = 2_000L,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                submitActionResult = OnlineActionResultDto(
                    accepted = true,
                    revision = refreshedMatch.revision,
                    actionId = "action-1",
                ),
                roomSnapshotsById = mutableMapOf(
                    refreshedRoom.roomId to refreshedRoom,
                ),
                matchSnapshotsById = mutableMapOf(
                    refreshedMatch.matchId to refreshedMatch,
                ),
            )

            val traceBuffer = InMemoryOnlineTraceBuffer()

            val repository = createRepository(
                apiClient = apiClient,
                traceLogger = createTraceLogger(
                    traceBuffer = traceBuffer,
                ),
            )

            val action = createOnlinePassTurnAction(
                roomId = refreshedRoom.roomId,
                matchId = refreshedMatch.matchId,
                playerId = "player-1",
                revision = 1L,
                actionId = "action-1",
            )

            val result = repository.submitAction(action)

            assertTrue(result.accepted)
            assertEquals(refreshedMatch.revision, result.revision)
            assertEquals(listOf(action), apiClient.submitActionRequests)
            assertEquals(listOf(refreshedRoom.roomId), apiClient.fetchRoomSnapshotRequests)
            assertEquals(listOf(refreshedMatch.matchId), apiClient.fetchMatchSnapshotRequests)
            assertEquals(refreshedRoom, repository.roomSnapshot.value)
            assertEquals(refreshedMatch, repository.matchSnapshot.value)

            assertEquals(
                listOf(
                    OnlineTraceType.ACTION_SUBMITTED,
                    OnlineTraceType.ACTION_ACCEPTED,
                    OnlineTraceType.SNAPSHOT_REQUESTED,
                    OnlineTraceType.SNAPSHOT_RECEIVED,
                    OnlineTraceType.SNAPSHOT_REQUESTED,
                    OnlineTraceType.SNAPSHOT_RECEIVED,
                    OnlineTraceType.SNAPSHOT_PUBLISHED,
                ),
                traceBuffer.snapshot().map { entry ->
                    entry.event.type
                },
            )
        }

    @Test
    fun stale_action_rejection_refreshes_authoritative_snapshots() =
        runBlocking {
            val refreshedRoom = createInMatchRoomSnapshot(
                updatedAtEpochMillis = 2_000L,
            )

            val refreshedMatch = createMatchSnapshot(
                revision = 2L,
                serverEpochMillis = 2_000L,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                submitActionResult = OnlineActionResultDto(
                    accepted = false,
                    revision = refreshedMatch.revision,
                    actionId = "action-1",
                    reason = "Snapshot desatualizado.",
                ),
                roomSnapshotsById = mutableMapOf(
                    refreshedRoom.roomId to refreshedRoom,
                ),
                matchSnapshotsById = mutableMapOf(
                    refreshedMatch.matchId to refreshedMatch,
                ),
            )

            val traceBuffer = InMemoryOnlineTraceBuffer()

            val repository = createRepository(
                apiClient = apiClient,
                traceLogger = createTraceLogger(
                    traceBuffer = traceBuffer,
                ),
            )

            val action = createOnlinePassTurnAction(
                roomId = refreshedRoom.roomId,
                matchId = refreshedMatch.matchId,
                playerId = "player-1",
                revision = 1L,
                actionId = "action-1",
            )

            val result = repository.submitAction(action)

            assertEquals(false, result.accepted)
            assertEquals("Snapshot desatualizado.", result.reason)
            assertEquals(listOf(action), apiClient.submitActionRequests)
            assertEquals(listOf(refreshedRoom.roomId), apiClient.fetchRoomSnapshotRequests)
            assertEquals(listOf(refreshedMatch.matchId), apiClient.fetchMatchSnapshotRequests)
            assertEquals(refreshedRoom, repository.roomSnapshot.value)
            assertEquals(refreshedMatch, repository.matchSnapshot.value)

            assertEquals(
                listOf(
                    OnlineTraceType.ACTION_SUBMITTED,
                    OnlineTraceType.ACTION_REJECTED,
                    OnlineTraceType.SNAPSHOT_REQUESTED,
                    OnlineTraceType.SNAPSHOT_RECEIVED,
                    OnlineTraceType.SNAPSHOT_REQUESTED,
                    OnlineTraceType.SNAPSHOT_RECEIVED,
                    OnlineTraceType.SNAPSHOT_PUBLISHED,
                ),
                traceBuffer.snapshot().map { entry ->
                    entry.event.type
                },
            )
        }

    @Test
    fun incremental_catch_up_batch_publishes_only_latest_snapshot_for_fast_forward() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val initialMatch = createMatchSnapshot(
                revision = 1L,
            )
            val catchUpSnapshots = (2L..6L).map { revision ->
                createMatchSnapshot(
                    revision = revision,
                    serverEpochMillis = revision * 1_000L,
                )
            }

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                submitActionResult = OnlineActionResultDto(
                    accepted = true,
                    revision = catchUpSnapshots.last().revision,
                    actionId = "action-1",
                ),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    initialMatch.matchId to initialMatch,
                ),
                matchSnapshotsAfterById = mutableMapOf(
                    initialMatch.matchId to catchUpSnapshots,
                ),
            )

            val traceBuffer = InMemoryOnlineTraceBuffer()

            val repository = createRepository(
                apiClient = apiClient,
                traceLogger = createTraceLogger(
                    traceBuffer = traceBuffer,
                ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            )

            traceBuffer.clear()

            val action = createOnlinePassTurnAction(
                roomId = room.roomId,
                matchId = initialMatch.matchId,
                playerId = "player-1",
                revision = initialMatch.revision,
                actionId = "action-1",
            )

            val result = repository.submitAction(
                action,
            )

            assertTrue(result.accepted)
            assertEquals(
                catchUpSnapshots.last(),
                repository.matchSnapshot.value,
            )
            assertEquals(
                listOf(
                    initialMatch.matchId to initialMatch.revision,
                ),
                apiClient.fetchMatchSnapshotsAfterRequests,
            )

            assertEquals(
                listOf(2L, 3L, 4L, 5L, 6L),
                traceBuffer.snapshot()
                    .filter { entry ->
                        entry.event.type == OnlineTraceType.SNAPSHOT_RECEIVED &&
                                entry.event.context.snapshotRevision != null
                    }
                    .map { entry ->
                        entry.event.context.snapshotRevision
                    },
            )

            val publishedEntries = traceBuffer.snapshot().filter { entry ->
                entry.event.type == OnlineTraceType.SNAPSHOT_PUBLISHED
            }

            assertEquals(
                1,
                publishedEntries.size,
            )
            assertEquals(
                6L,
                publishedEntries.single().event.context.snapshotRevision,
            )
            assertEquals(
                "action_refresh:fast_forward_batch",
                publishedEntries.single().event.attributes["trigger"],
            )
        }

    @Test
    fun refresh_keeps_current_match_when_incremental_feed_has_no_newer_revision() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val currentMatch = createMatchSnapshot(
                revision = 2L,
                serverEpochMillis = 2_000L,
            )
            val staleMatch = createMatchSnapshot(
                revision = 1L,
                serverEpochMillis = 1_000L,
            )

            val matchSnapshotsById = mutableMapOf(
                currentMatch.matchId to currentMatch,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                submitActionResult = OnlineActionResultDto(
                    accepted = true,
                    revision = 3L,
                    actionId = "action-1",
                ),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = matchSnapshotsById,
            )

            val traceBuffer = InMemoryOnlineTraceBuffer()

            val repository = createRepository(
                apiClient = apiClient,
                traceLogger = createTraceLogger(
                    traceBuffer = traceBuffer,
                ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                )
            )

            assertEquals(currentMatch, repository.matchSnapshot.value)

            traceBuffer.clear()
            matchSnapshotsById[currentMatch.matchId] = staleMatch

            val action = createOnlinePassTurnAction(
                roomId = room.roomId,
                matchId = currentMatch.matchId,
                playerId = "player-1",
                revision = currentMatch.revision,
                actionId = "action-1",
            )

            repository.submitAction(action)

            assertEquals(currentMatch, repository.matchSnapshot.value)

            assertEquals(
                listOf(
                    OnlineTraceType.ACTION_SUBMITTED,
                    OnlineTraceType.ACTION_ACCEPTED,
                    OnlineTraceType.SNAPSHOT_REQUESTED,
                    OnlineTraceType.SNAPSHOT_RECEIVED,
                    OnlineTraceType.SNAPSHOT_REQUESTED,
                ),
                traceBuffer.snapshot().map { entry ->
                    entry.event.type
                },
            )
        }

    @Test
    fun create_room_with_remote_backend_without_base_url_returns_rejected_result() =
        runBlocking {
            val repository = RemoteOnlineRoomRepository(
                config = OnlineBackendConfig.remote(
                    baseUrl = "",
                ),
            )

            val result = repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                )
            )

            assertEquals(false, result.accepted)
            assertEquals(
                "Backend online remoto sem endpoint configurado.",
                result.reason,
            )
            assertNull(repository.roomSnapshot.value)
            assertNull(repository.matchSnapshot.value)
        }

    @Test
    fun join_room_returns_rejected_result_when_client_throws() =
        runBlocking {
            val apiClient = FakeRemoteOnlineApiClient(
                joinRoomFailure = IllegalStateException("servidor fora"),
            )

            val repository = createRepository(
                apiClient = apiClient,
            )

            val request = JoinOnlineRoomRequestDto(
                roomCode = "123456",
                localPlayerId = "player-2",
                playerName = "Jogador 2",
            )

            val result = repository.joinRoom(request)

            assertEquals(false, result.accepted)
            assertEquals(
                "Falha ao entrar na sala online remota. servidor fora",
                result.reason,
            )
            assertEquals(listOf(request), apiClient.joinRoomRequests)
            assertNull(repository.roomSnapshot.value)
            assertNull(repository.matchSnapshot.value)
        }

    @Test
    fun submit_action_returns_rejected_result_when_client_throws() =
        runBlocking {
            val apiClient = FakeRemoteOnlineApiClient(
                submitActionFailure = IllegalStateException("timeout"),
            )

            val traceBuffer = InMemoryOnlineTraceBuffer()

            val repository = createRepository(
                apiClient = apiClient,
                traceLogger = createTraceLogger(
                    traceBuffer = traceBuffer,
                ),
            )

            val action = createOnlinePassTurnAction(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "player-1",
                revision = 1L,
                actionId = "action-1",
            )

            val result = repository.submitAction(action)

            assertEquals(false, result.accepted)
            assertEquals("action-1", result.actionId)
            assertEquals(
                "Falha ao enviar ação online remota. timeout",
                result.reason,
            )
            assertEquals(listOf(action), apiClient.submitActionRequests)
            assertEquals(emptyList<String>(), apiClient.fetchRoomSnapshotRequests)
            assertEquals(emptyList<String>(), apiClient.fetchMatchSnapshotRequests)
            assertNull(repository.roomSnapshot.value)
            assertNull(repository.matchSnapshot.value)

            assertEquals(
                listOf(
                    OnlineTraceType.ACTION_SUBMITTED,
                    OnlineTraceType.TRANSPORT_FAILURE,
                    OnlineTraceType.ACTION_REJECTED,
                ),
                traceBuffer.snapshot().map { entry ->
                    entry.event.type
                },
            )
        }

    @Test
    fun create_room_keeps_room_snapshot_when_fetch_match_snapshot_fails() =
        runBlocking {
            val room = createInMatchRoomSnapshot()

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                fetchMatchSnapshotFailure = IllegalStateException(
                    "partida indisponível",
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
            )

            val result = repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                )
            )

            assertTrue(result.accepted)
            assertEquals(room, repository.roomSnapshot.value)
            assertNull(repository.matchSnapshot.value)
            assertEquals(listOf("match-1"), apiClient.fetchMatchSnapshotRequests)
        }

    @Test
    fun submit_action_keeps_previous_room_snapshot_when_room_refresh_fails_but_match_refresh_succeeds() =
        runBlocking {
            val initialRoom = createInMatchRoomSnapshot(
                updatedAtEpochMillis = 1_000L,
            )

            val initialMatch = createMatchSnapshot(
                revision = 1L,
                serverEpochMillis = 1_000L,
            )

            val refreshedMatch = createMatchSnapshot(
                revision = 2L,
                serverEpochMillis = 2_000L,
            )

            val matchSnapshotsById = mutableMapOf(
                initialMatch.matchId to initialMatch,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = initialRoom,
                    localSeatIndex = 0,
                ),
                submitActionResult = OnlineActionResultDto(
                    accepted = true,
                    revision = refreshedMatch.revision,
                    actionId = "action-1",
                ),
                fetchRoomSnapshotFailure = IllegalStateException(
                    "sala indisponível",
                ),
                matchSnapshotsById = matchSnapshotsById,
            )

            val repository = createRepository(
                apiClient = apiClient,
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                )
            )

            matchSnapshotsById[refreshedMatch.matchId] = refreshedMatch

            val action = createOnlinePassTurnAction(
                roomId = initialRoom.roomId,
                matchId = initialMatch.matchId,
                playerId = "player-1",
                revision = initialMatch.revision,
                actionId = "action-1",
            )

            val result = repository.submitAction(action)

            assertTrue(result.accepted)
            assertEquals(refreshedMatch.revision, result.revision)
            assertEquals(initialRoom, repository.roomSnapshot.value)
            assertEquals(refreshedMatch, repository.matchSnapshot.value)
            assertEquals(listOf(initialRoom.roomId), apiClient.fetchRoomSnapshotRequests)
            assertEquals(
                listOf(initialMatch.matchId, refreshedMatch.matchId),
                apiClient.fetchMatchSnapshotRequests,
            )
        }

    @Test
    fun leave_room_clears_snapshots_even_when_leave_action_fails() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                submitActionFailure = IllegalStateException("falha ao sair"),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                )
            )

            repository.leaveRoom()

            assertEquals(1, apiClient.submitActionRequests.size)
            assertEquals(
                OnlinePlayerActionTypeDto.LEAVE_ROOM,
                apiClient.submitActionRequests.first().type,
            )
            assertNull(repository.roomSnapshot.value)
            assertNull(repository.matchSnapshot.value)
        }

    private fun createRepository(
        apiClient: RemoteOnlineApiClient,
        traceLogger: OnlineTraceLogger = OnlineTraceLogger(),
    ): RemoteOnlineRoomRepository {
        return RemoteOnlineRoomRepository(
            config = OnlineBackendConfig.remote(
                baseUrl = "http://localhost:8080",
            ),
            apiClient = apiClient,
            traceLogger = traceLogger,
            nowEpochMillis = { 1_000L },
        )
    }

    private fun createTraceLogger(
        traceBuffer: InMemoryOnlineTraceBuffer,
    ): OnlineTraceLogger {
        return OnlineTraceLogger(
            sink = traceBuffer,
            nowEpochMillis = { 1_000L },
        )
    }

    private fun createWaitingRoomSnapshot(): OnlineRoomSnapshotDto {
        return OnlineRoomSnapshotDto(
            roomId = "room-1",
            roomCode = "123456",
            hostPlayerId = "player-1",
            status = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = "player-1",
                    name = "Jogador 1",
                    seatIndex = 0,
                    connected = true,
                ),
            ),
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
    }

    private fun createInMatchRoomSnapshot(
        updatedAtEpochMillis: Long = 1_000L,
    ): OnlineRoomSnapshotDto {
        return OnlineRoomSnapshotDto(
            roomId = "room-1",
            roomCode = "123456",
            hostPlayerId = "player-1",
            status = OnlineRoomStatusDto.IN_MATCH,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = "player-1",
                    name = "Jogador 1",
                    seatIndex = 0,
                    connected = true,
                ),
                OnlineRoomPlayerDto(
                    playerId = "player-2",
                    name = "Jogador 2",
                    seatIndex = 1,
                    connected = true,
                ),
                OnlineRoomPlayerDto(
                    playerId = "player-3",
                    name = "Jogador 3",
                    seatIndex = 2,
                    connected = true,
                ),
                OnlineRoomPlayerDto(
                    playerId = "player-4",
                    name = "Jogador 4",
                    seatIndex = 3,
                    connected = true,
                ),
            ),
            matchId = "match-1",
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    private fun createMatchSnapshot(
        revision: Long,
        serverEpochMillis: Long = 1_000L,
    ): OnlineMatchSnapshotDto {
        val gameState = createInitialDominoGameState()

        val runtimeState = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.RoundIntro,
        )

        return runtimeState.toOnlineSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = revision,
            serverEpochMillis = serverEpochMillis,
        )
    }

    private class FakeRemoteOnlineApiClient(
        private val createRoomResult: OnlineRoomOperationResultDto =
            OnlineRoomOperationResultDto(
                accepted = false,
                reason = "createRoom não configurado no teste.",
            ),
        private val joinRoomResult: OnlineRoomOperationResultDto =
            OnlineRoomOperationResultDto(
                accepted = false,
                reason = "joinRoom não configurado no teste.",
            ),
        private val submitActionResult: OnlineActionResultDto =
            OnlineActionResultDto(
                accepted = false,
                reason = "submitAction não configurado no teste.",
            ),
        private val createRoomFailure: Throwable? = null,
        private val joinRoomFailure: Throwable? = null,
        private val submitActionFailure: Throwable? = null,
        private val fetchRoomSnapshotFailure: Throwable? = null,
        private val fetchMatchSnapshotFailure: Throwable? = null,
        private val roomSnapshotsById: MutableMap<String, OnlineRoomSnapshotDto> =
            mutableMapOf(),
        private val matchSnapshotsById: MutableMap<String, OnlineMatchSnapshotDto> =
            mutableMapOf(),
        private val matchSnapshotsAfterById: MutableMap<String, List<OnlineMatchSnapshotDto>> =
            mutableMapOf(),
    ) : RemoteOnlineApiClient {
        val createRoomRequests = mutableListOf<CreateOnlineRoomRequestDto>()
        val joinRoomRequests = mutableListOf<JoinOnlineRoomRequestDto>()
        val submitActionRequests = mutableListOf<OnlinePlayerActionDto>()
        val fetchRoomSnapshotRequests = mutableListOf<String>()
        val fetchMatchSnapshotRequests = mutableListOf<String>()
        val fetchMatchSnapshotsAfterRequests = mutableListOf<Pair<String, Long>>()

        override suspend fun createRoom(
            request: CreateOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto {
            createRoomRequests += request

            createRoomFailure?.let { error ->
                throw error
            }

            return createRoomResult
        }

        override suspend fun joinRoom(
            request: JoinOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto {
            joinRoomRequests += request

            joinRoomFailure?.let { error ->
                throw error
            }

            return joinRoomResult
        }

        override suspend fun submitAction(
            action: OnlinePlayerActionDto,
        ): OnlineActionResultDto {
            submitActionRequests += action

            submitActionFailure?.let { error ->
                throw error
            }

            return submitActionResult
        }

        override suspend fun fetchRoomSnapshot(
            roomId: String,
        ): OnlineRoomSnapshotDto {
            fetchRoomSnapshotRequests += roomId

            fetchRoomSnapshotFailure?.let { error ->
                throw error
            }

            return requireNotNull(roomSnapshotsById[roomId]) {
                "Snapshot de sala não configurado para $roomId."
            }
        }

        override suspend fun fetchMatchSnapshot(
            matchId: String,
        ): OnlineMatchSnapshotDto {
            fetchMatchSnapshotRequests += matchId

            fetchMatchSnapshotFailure?.let { error ->
                throw error
            }

            return requireNotNull(matchSnapshotsById[matchId]) {
                "Snapshot de partida não configurado para $matchId."
            }
        }

        override suspend fun fetchMatchSnapshotsAfter(
            matchId: String,
            afterRevision: Long,
        ): List<OnlineMatchSnapshotDto> {
            fetchMatchSnapshotsAfterRequests += matchId to afterRevision

            val configuredSnapshots = matchSnapshotsAfterById[matchId]

            if (configuredSnapshots != null) {
                return configuredSnapshots.filter { snapshot ->
                    snapshot.revision > afterRevision
                }
            }

            return listOf(
                fetchMatchSnapshot(matchId),
            ).filter { snapshot ->
                snapshot.revision > afterRevision
            }
        }
    }
}