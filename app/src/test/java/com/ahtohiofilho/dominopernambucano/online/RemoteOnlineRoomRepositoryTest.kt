package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.online.observability.InMemoryOnlineTraceBuffer
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEntry
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    fun incremental_catch_up_batch_publishes_all_snapshots_in_revision_order() =
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
                listOf(2L, 3L, 4L, 5L, 6L),
                publishedEntries.map { entry ->
                    entry.event.context.snapshotRevision
                },
            )
            assertTrue(
                publishedEntries.all { entry ->
                    entry.event.attributes["trigger"] ==
                            "action_refresh:incremental_history_batch"
                },
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
    fun trace_submission_preserves_structured_terminal_rejection() =
        runBlocking {
            val rejection = OnlineTraceBatchResultDto(
                accepted = false,
                reason = "Evento inválido.",
            )
            val apiClient = FakeRemoteOnlineApiClient(
                submitTraceBatchResult = rejection,
            )
            val repository = createRepository(
                apiClient = apiClient,
            )
            val batch = createTraceBatch()

            val result = repository.submitTraceBatch(
                batch = batch,
            )

            assertEquals(rejection, result)
            assertEquals(false, result.retryable)
            assertEquals(
                listOf(batch),
                apiClient.submitTraceBatchRequests,
            )
        }

    @Test
    fun trace_submission_marks_client_failure_as_retryable() =
        runBlocking {
            val apiClient = FakeRemoteOnlineApiClient(
                submitTraceBatchFailure =
                    IllegalStateException("servidor fora"),
            )
            val repository = createRepository(
                apiClient = apiClient,
            )
            val batch = createTraceBatch()

            val result = repository.submitTraceBatch(
                batch = batch,
            )

            assertEquals(false, result.accepted)
            assertEquals(true, result.retryable)
            assertTrue(
                result.reason.orEmpty().contains(
                    "servidor fora",
                ),
            )
            assertEquals(
                listOf(batch),
                apiClient.submitTraceBatchRequests,
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


    @Test
    fun create_room_uses_anonymous_session_identity_and_bearer() =
        runBlocking {
            val session = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "session-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val room = createWaitingRoomSnapshot().copy(
                hostPlayerId = session.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = session.playerId,
                        name = "Jogador 1",
                        seatIndex = 0,
                        connected = true,
                    ),
                ),
            )

            val apiClient = FakeRemoteOnlineApiClient(
                anonymousSession = session,
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    OnlineAnonymousSessionRepository(
                        store = InMemoryOnlineAnonymousSessionStore(),
                        nowEpochMillis = { 1_000L },
                    ),
            )

            val result = repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-local",
                    playerName = "Jogador 1",
                ),
            )

            assertTrue(result.accepted)
            assertEquals(1, apiClient.createAnonymousSessionCallCount)
            assertEquals(
                listOf(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = session.playerId,
                        playerName = "Jogador 1",
                    ),
                ),
                apiClient.createRoomRequests,
            )
            assertEquals(
                session.accessToken,
                apiClient.bearerAccessTokenUpdates.last(),
            )
            assertEquals(
                null,
                apiClient.developmentPlayerIdUpdates.last(),
            )
        }

    @Test
    fun fake_join_restores_active_participant_bearer() =
        runBlocking {
            val session = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "session-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val authenticatedRoom = createWaitingRoomSnapshot().copy(
                hostPlayerId = session.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = session.playerId,
                        name = "Jogador 1",
                        seatIndex = 0,
                        connected = true,
                    ),
                ),
            )

            val roomAfterFakeJoin = authenticatedRoom.copy(
                players = authenticatedRoom.players + OnlineRoomPlayerDto(
                    playerId = "fake-player-2",
                    name = "Jogador 2",
                    seatIndex = 1,
                    connected = true,
                ),
            )

            val apiClient = FakeRemoteOnlineApiClient(
                anonymousSession = session,
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = authenticatedRoom,
                    localSeatIndex = 0,
                ),
                joinRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = roomAfterFakeJoin,
                    localSeatIndex = 1,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    OnlineAnonymousSessionRepository(
                        store = InMemoryOnlineAnonymousSessionStore(),
                        nowEpochMillis = { 1_000L },
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-local",
                    playerName = "Jogador 1",
                ),
            )

            apiClient.bearerAccessTokenUpdates.clear()
            apiClient.developmentPlayerIdUpdates.clear()

            val fakeJoinRequest = JoinOnlineRoomRequestDto(
                roomCode = authenticatedRoom.roomCode,
                localPlayerId = "fake-player-2",
                playerName = "Jogador 2",
            )

            val result = repository.joinRoom(
                fakeJoinRequest,
            )

            assertTrue(result.accepted)
            assertEquals(
                listOf(fakeJoinRequest),
                apiClient.joinRoomRequests,
            )
            assertEquals(
                listOf(
                    null,
                    session.accessToken,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
            assertEquals(
                listOf(
                    "fake-player-2",
                    null,
                ),
                apiClient.developmentPlayerIdUpdates,
            )
        }

    @Test
    fun create_room_persists_binding_for_accepted_authenticated_participant() =
        runBlocking {
            val session = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "session-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val room = createWaitingRoomSnapshot().copy(
                hostPlayerId = session.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = session.playerId,
                        name = "Jogador 1",
                        seatIndex = 0,
                        connected = true,
                    ),
                ),
            )

            val bindingRepository =
                OnlineParticipationBindingRepository(
                    store = InMemoryOnlineParticipationBindingStore(),
                )

            val apiClient = FakeRemoteOnlineApiClient(
                anonymousSession = session,
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    OnlineAnonymousSessionRepository(
                        store = InMemoryOnlineAnonymousSessionStore(),
                        nowEpochMillis = { 1_000L },
                    ),
                onlineParticipationBindingRepository =
                    bindingRepository,
            )

            val result = repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-local",
                    playerName = "Jogador 1",
                ),
            )

            assertTrue(result.accepted)
            assertEquals(
                OnlineParticipationBinding(
                    roomId = room.roomId,
                    matchId = null,
                    playerId = session.playerId,
                    localSeatIndex = 0,
                ),
                bindingRepository.getValidBindingOrNull(),
            )
        }

    @Test
    fun join_room_persists_binding_for_accepted_authenticated_participant() =
        runBlocking {
            val session = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-2",
                accessToken = "session-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val room = createWaitingRoomSnapshot().copy(
                hostPlayerId = "host-player",
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = "host-player",
                        name = "Host",
                        seatIndex = 0,
                        connected = true,
                    ),
                    OnlineRoomPlayerDto(
                        playerId = session.playerId,
                        name = "Jogador 2",
                        seatIndex = 1,
                        connected = true,
                    ),
                ),
            )

            val bindingRepository =
                OnlineParticipationBindingRepository(
                    store = InMemoryOnlineParticipationBindingStore(),
                )

            val apiClient = FakeRemoteOnlineApiClient(
                anonymousSession = session,
                joinRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 1,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    OnlineAnonymousSessionRepository(
                        store = InMemoryOnlineAnonymousSessionStore(),
                        nowEpochMillis = { 1_000L },
                    ),
                onlineParticipationBindingRepository =
                    bindingRepository,
            )

            val result = repository.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "player-local",
                    playerName = "Jogador 2",
                ),
            )

            assertTrue(result.accepted)
            assertEquals(
                session.playerId,
                apiClient.joinRoomRequests.single().localPlayerId,
            )
            assertEquals(
                OnlineParticipationBinding(
                    roomId = room.roomId,
                    matchId = null,
                    playerId = session.playerId,
                    localSeatIndex = 1,
                ),
                bindingRepository.getValidBindingOrNull(),
            )
        }

    @Test
    fun accepted_room_operation_without_confirmed_local_seat_does_not_persist_binding() =
        runBlocking {
            val session = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "session-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val room = createWaitingRoomSnapshot().copy(
                hostPlayerId = session.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = session.playerId,
                        name = "Jogador 1",
                        seatIndex = 0,
                        connected = true,
                    ),
                ),
            )

            val bindingRepository =
                OnlineParticipationBindingRepository(
                    store = InMemoryOnlineParticipationBindingStore(),
                )

            val apiClient = FakeRemoteOnlineApiClient(
                anonymousSession = session,
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = null,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    OnlineAnonymousSessionRepository(
                        store = InMemoryOnlineAnonymousSessionStore(),
                        nowEpochMillis = { 1_000L },
                    ),
                onlineParticipationBindingRepository =
                    bindingRepository,
            )

            val result = repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-local",
                    playerName = "Jogador 1",
                ),
            )

            assertTrue(result.accepted)
            assertNull(
                bindingRepository.getValidBindingOrNull(),
            )
        }

    @Test
    fun fake_join_does_not_overwrite_authenticated_participant_binding() =
        runBlocking {
            val session = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "session-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val authenticatedRoom = createWaitingRoomSnapshot().copy(
                hostPlayerId = session.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = session.playerId,
                        name = "Jogador 1",
                        seatIndex = 0,
                        connected = true,
                    ),
                ),
            )

            val roomAfterFakeJoin = authenticatedRoom.copy(
                players = authenticatedRoom.players + OnlineRoomPlayerDto(
                    playerId = "fake-player-2",
                    name = "Jogador 2",
                    seatIndex = 1,
                    connected = true,
                ),
            )

            val bindingRepository =
                OnlineParticipationBindingRepository(
                    store = InMemoryOnlineParticipationBindingStore(),
                )

            val apiClient = FakeRemoteOnlineApiClient(
                anonymousSession = session,
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = authenticatedRoom,
                    localSeatIndex = 0,
                ),
                joinRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = roomAfterFakeJoin,
                    localSeatIndex = 1,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    OnlineAnonymousSessionRepository(
                        store = InMemoryOnlineAnonymousSessionStore(),
                        nowEpochMillis = { 1_000L },
                    ),
                onlineParticipationBindingRepository =
                    bindingRepository,
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-local",
                    playerName = "Jogador 1",
                ),
            )

            val expectedBinding = OnlineParticipationBinding(
                roomId = authenticatedRoom.roomId,
                matchId = null,
                playerId = session.playerId,
                localSeatIndex = 0,
            )

            assertEquals(
                expectedBinding,
                bindingRepository.getValidBindingOrNull(),
            )

            val result = repository.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = authenticatedRoom.roomCode,
                    localPlayerId = "fake-player-2",
                    playerName = "Jogador 2",
                ),
            )

            assertTrue(result.accepted)
            assertEquals(
                expectedBinding,
                bindingRepository.getValidBindingOrNull(),
            )
        }

    @Test
    fun leave_room_clears_authenticated_participant_binding_when_remote_action_fails() =
        runBlocking {
            val session = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "session-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val initialRoom = createInMatchRoomSnapshot()
            val room = initialRoom.copy(
                hostPlayerId = session.playerId,
                players = initialRoom.players.map { player ->
                    if (player.seatIndex == 0) {
                        player.copy(
                            playerId = session.playerId,
                            name = "Jogador 1",
                        )
                    } else {
                        player
                    }
                },
            )
            val match = createMatchSnapshot(
                revision = 1L,
            )

            val bindingRepository =
                OnlineParticipationBindingRepository(
                    store = InMemoryOnlineParticipationBindingStore(),
                )

            val apiClient = FakeRemoteOnlineApiClient(
                anonymousSession = session,
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
                anonymousSessionRepository =
                    OnlineAnonymousSessionRepository(
                        store = InMemoryOnlineAnonymousSessionStore(),
                        nowEpochMillis = { 1_000L },
                    ),
                onlineParticipationBindingRepository =
                    bindingRepository,
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-local",
                    playerName = "Jogador 1",
                ),
            )

            assertEquals(
                OnlineParticipationBinding(
                    roomId = room.roomId,
                    matchId = room.matchId,
                    playerId = session.playerId,
                    localSeatIndex = 0,
                ),
                bindingRepository.getValidBindingOrNull(),
            )

            repository.leaveRoom()

            assertEquals(1, apiClient.submitActionRequests.size)
            assertEquals(
                OnlinePlayerActionTypeDto.LEAVE_ROOM,
                apiClient.submitActionRequests.first().type,
            )
            assertNull(
                bindingRepository.getValidBindingOrNull(),
            )
        }


    @Test
    fun prepare_pending_participation_match_resume_returns_ready_without_publishing_snapshots() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
                localSeatIndex = 2,
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val match = createMatchSnapshot(
                revision = 5L,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation.Ready(
                    binding = binding,
                    roomSnapshot = room,
                    matchSnapshot = match,
                ),
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                listOf(match.matchId),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                emptyList<CreateOnlineRoomRequestDto>(),
                apiClient.createRoomRequests,
            )
            assertEquals(
                emptyList<JoinOnlineRoomRequestDto>(),
                apiClient.joinRoomRequests,
            )
            assertEquals(
                emptyList<OnlinePlayerActionDto>(),
                apiClient.submitActionRequests,
            )
            assertEquals(
                0,
                apiClient.createAnonymousSessionCallCount,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
            assertEquals(
                listOf(
                    null,
                    null,
                ),
                apiClient.developmentPlayerIdUpdates,
            )
        }

    @Test
    fun activate_pending_participation_match_resume_publishes_prepared_snapshots_without_fetching_again() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
                localSeatIndex = 2,
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val match = createMatchSnapshot(
                revision = 5L,
            )
            val apiClient = FakeRemoteOnlineApiClient()
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.activatePendingParticipationMatchResume(
                preparation = OnlinePendingParticipationMatchResumePreparation.Ready(
                    binding = binding,
                    roomSnapshot = room,
                    matchSnapshot = match,
                ),
            )

            assertEquals(
                OnlinePendingParticipationMatchResumeActivation.Activated,
                result,
            )
            assertEquals(
                room,
                repository.roomSnapshot.value,
            )
            assertEquals(
                match,
                repository.matchSnapshot.value,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
        }

    @Test
    fun activate_pending_participation_match_resume_starts_polling_once_for_same_room() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val match = createMatchSnapshot(
                revision = 5L,
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val repository = createRepository(
                apiClient = FakeRemoteOnlineApiClient(),
                traceLogger = createTraceLogger(
                    traceBuffer = traceBuffer,
                ),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 60_000L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
            )
            val preparation =
                OnlinePendingParticipationMatchResumePreparation.Ready(
                    binding = binding,
                    roomSnapshot = room,
                    matchSnapshot = match,
                )

            assertEquals(
                OnlinePendingParticipationMatchResumeActivation.Activated,
                repository.activatePendingParticipationMatchResume(
                    preparation = preparation,
                ),
            )
            assertEquals(
                OnlinePendingParticipationMatchResumeActivation.Activated,
                repository.activatePendingParticipationMatchResume(
                    preparation = preparation,
                ),
            )
            val activationTraceEntries = traceBuffer.snapshot()

            assertEquals(
                1,
                activationTraceEntries.count { entry ->
                    entry.event.type == OnlineTraceType.SNAPSHOT_PUBLISHED &&
                            entry.event.attributes["trigger"] ==
                            "pending_participation_resume_activation"
                },
            )
            assertEquals(
                1,
                activationTraceEntries.count { entry ->
                    entry.event.type == OnlineTraceType.SNAPSHOT_IGNORED &&
                            entry.event.attributes["reason"] ==
                            "duplicate_match_revision" &&
                            entry.event.attributes["trigger"] ==
                            "pending_participation_resume_activation"
                },
            )
            assertEquals(
                1,
                activationTraceEntries.count { entry ->
                    entry.event.type == OnlineTraceType.POLLING_STARTED
                },
            )

            repository.leaveRoom()

            assertEquals(
                1,
                traceBuffer.snapshot().count { entry ->
                    entry.event.type == OnlineTraceType.POLLING_STOPPED
                },
            )
        }

    @Test
    fun submit_action_remote_401_emits_invalidation_and_stops_polling() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                submitActionFailure = createUnauthorizedClientRequestException(),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 1_000L,
                ),
                coroutineDispatcher = Dispatchers.Default,
                traceLogger = createTraceLogger(traceBuffer),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val invalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            val action = createOnlinePassTurnAction(
                roomId = room.roomId,
                matchId = match.matchId,
                playerId = session.playerId,
                revision = match.revision,
                actionId = "remote-401-action",
            )

            val result = repository.submitAction(
                action = action,
            )

            assertEquals(
                false,
                result.accepted,
            )
            assertEquals(
                listOf(action),
                apiClient.submitActionRequests,
            )
            assertEquals(
                OnlineActiveMatchSessionInvalidation(
                    roomId = room.roomId,
                    matchId = match.matchId,
                    playerId = session.playerId,
                ),
                withTimeout(2_000L) {
                    invalidationDeferred.await()
                },
            )
            assertTrue(
                traceBuffer.snapshot().any { entry ->
                    entry.event.type ==
                        OnlineTraceType.POLLING_STOPPED &&
                        entry.event.attributes["reason"] ==
                            "active_session_invalidated"
                },
            )

            val requestCountAtStop =
                apiClient.fetchRoomSnapshotRequests.size

            delay(50L)

            assertEquals(
                requestCountAtStop,
                apiClient.fetchRoomSnapshotRequests.size,
            )

            repository.leaveRoom()
        }

    @Test
    fun polling_room_snapshot_remote_401_emits_single_invalidation_and_stops_polling() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                fetchRoomSnapshotFailure =
                    createUnauthorizedClientRequestException(),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 50L,
                ),
                coroutineDispatcher = Dispatchers.Default,
                traceLogger = createTraceLogger(traceBuffer),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val invalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            val invalidation = withTimeout(2_000L) {
                invalidationDeferred.await()
            }

            assertEquals(
                OnlineActiveMatchSessionInvalidation(
                    roomId = room.roomId,
                    matchId = match.matchId,
                    playerId = session.playerId,
                ),
                invalidation,
            )

            withTimeout(2_000L) {
                while (
                    traceBuffer.snapshot().none { entry ->
                        entry.event.type ==
                            OnlineTraceType.POLLING_STOPPED &&
                            entry.event.attributes["reason"] ==
                                "active_session_invalidated"
                    }
                ) {
                    delay(10L)
                }
            }

            assertEquals(
                emptyList<Pair<String, Long>>(),
                apiClient.fetchMatchSnapshotsAfterRequests,
            )

            val roomRequestCountAtStop =
                apiClient.fetchRoomSnapshotRequests.size

            delay(100L)

            assertEquals(
                roomRequestCountAtStop,
                apiClient.fetchRoomSnapshotRequests.size,
            )

            repository.leaveRoom()
        }

    @Test
    fun polling_match_snapshot_remote_401_emits_single_invalidation_and_stops_polling() =
        runBlocking {
            val waitingRoom = createWaitingRoomSnapshot()
            val activeRoom = createInMatchRoomSnapshot()
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = waitingRoom,
                    localSeatIndex = 0,
                ),
                fetchMatchSnapshotFailure =
                    createUnauthorizedClientRequestException(),
                roomSnapshotsById = mutableMapOf(
                    waitingRoom.roomId to activeRoom,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 50L,
                ),
                coroutineDispatcher = Dispatchers.Default,
                traceLogger = createTraceLogger(traceBuffer),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val invalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            assertEquals(
                OnlineActiveMatchSessionInvalidation(
                    roomId = activeRoom.roomId,
                    matchId = requireNotNull(activeRoom.matchId),
                    playerId = session.playerId,
                ),
                withTimeout(2_000L) {
                    invalidationDeferred.await()
                },
            )

            withTimeout(2_000L) {
                while (
                    traceBuffer.snapshot().none { entry ->
                        entry.event.type ==
                            OnlineTraceType.POLLING_STOPPED &&
                            entry.event.attributes["reason"] ==
                                "active_session_invalidated"
                    }
                ) {
                    delay(10L)
                }
            }

            val matchRequestCountAtStop =
                apiClient.fetchMatchSnapshotRequests.size

            delay(100L)

            assertEquals(
                matchRequestCountAtStop,
                apiClient.fetchMatchSnapshotRequests.size,
            )

            repository.leaveRoom()
        }

    @Test
    fun polling_match_updates_remote_401_emits_single_invalidation_and_stops_polling() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                fetchMatchSnapshotsAfterFailure =
                    createUnauthorizedClientRequestException(),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 50L,
                ),
                coroutineDispatcher = Dispatchers.Default,
                traceLogger = createTraceLogger(traceBuffer),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val invalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            assertEquals(
                OnlineActiveMatchSessionInvalidation(
                    roomId = room.roomId,
                    matchId = match.matchId,
                    playerId = session.playerId,
                ),
                withTimeout(2_000L) {
                    invalidationDeferred.await()
                },
            )

            withTimeout(2_000L) {
                while (
                    traceBuffer.snapshot().none { entry ->
                        entry.event.type ==
                            OnlineTraceType.POLLING_STOPPED &&
                            entry.event.attributes["reason"] ==
                                "active_session_invalidated"
                    }
                ) {
                    delay(10L)
                }
            }

            val updatesRequestCountAtStop =
                apiClient.fetchMatchSnapshotsAfterRequests.size

            delay(100L)

            assertEquals(
                updatesRequestCountAtStop,
                apiClient.fetchMatchSnapshotsAfterRequests.size,
            )

            repository.leaveRoom()
        }

    @Test
    fun polling_room_snapshot_remote_404_emits_room_not_found_resource_loss_and_stops_polling() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                fetchRoomSnapshotFailure =
                    createNotFoundClientRequestException(),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 50L,
                ),
                coroutineDispatcher = Dispatchers.Default,
                traceLogger = createTraceLogger(traceBuffer),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val resourceLossDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchResourceLossEvents
                    .first()
            }
            val sessionInvalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            assertEquals(
                OnlineActiveMatchResourceLoss(
                    roomId = room.roomId,
                    matchId = match.matchId,
                    playerId = session.playerId,
                    reason =
                        OnlineActiveMatchResourceLossReason.ROOM_NOT_FOUND,
                ),
                withTimeout(2_000L) {
                    resourceLossDeferred.await()
                },
            )

            withTimeout(2_000L) {
                while (
                    traceBuffer.snapshot().none { entry ->
                        entry.event.type ==
                            OnlineTraceType.POLLING_STOPPED &&
                            entry.event.attributes["reason"] ==
                                "active_resource_lost"
                    }
                ) {
                    delay(10L)
                }
            }

            assertEquals(
                emptyList<Pair<String, Long>>(),
                apiClient.fetchMatchSnapshotsAfterRequests,
            )

            delay(100L)

            assertEquals(
                false,
                sessionInvalidationDeferred.isCompleted,
            )

            sessionInvalidationDeferred.cancel()
            repository.leaveRoom()
        }

    @Test
    fun polling_match_snapshot_remote_404_emits_match_not_found_resource_loss_and_stops_polling() =
        runBlocking {
            val waitingRoom = createWaitingRoomSnapshot()
            val activeRoom = createInMatchRoomSnapshot()
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = waitingRoom,
                    localSeatIndex = 0,
                ),
                fetchMatchSnapshotFailure =
                    createNotFoundClientRequestException(),
                roomSnapshotsById = mutableMapOf(
                    waitingRoom.roomId to activeRoom,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 50L,
                ),
                coroutineDispatcher = Dispatchers.Default,
                traceLogger = createTraceLogger(traceBuffer),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val resourceLossDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchResourceLossEvents
                    .first()
            }

            assertEquals(
                OnlineActiveMatchResourceLoss(
                    roomId = activeRoom.roomId,
                    matchId = requireNotNull(activeRoom.matchId),
                    playerId = session.playerId,
                    reason =
                        OnlineActiveMatchResourceLossReason.MATCH_NOT_FOUND,
                ),
                withTimeout(2_000L) {
                    resourceLossDeferred.await()
                },
            )

            withTimeout(2_000L) {
                while (
                    traceBuffer.snapshot().none { entry ->
                        entry.event.type ==
                            OnlineTraceType.POLLING_STOPPED &&
                            entry.event.attributes["reason"] ==
                                "active_resource_lost"
                    }
                ) {
                    delay(10L)
                }
            }

            val matchRequestCountAtStop =
                apiClient.fetchMatchSnapshotRequests.size

            delay(100L)

            assertEquals(
                matchRequestCountAtStop,
                apiClient.fetchMatchSnapshotRequests.size,
            )

            repository.leaveRoom()
        }

    @Test
    fun polling_match_updates_remote_404_emits_match_not_found_resource_loss_and_stops_polling() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                fetchMatchSnapshotsAfterFailure =
                    createNotFoundClientRequestException(),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 50L,
                ),
                coroutineDispatcher = Dispatchers.Default,
                traceLogger = createTraceLogger(traceBuffer),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val resourceLossDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchResourceLossEvents
                    .first()
            }

            assertEquals(
                OnlineActiveMatchResourceLoss(
                    roomId = room.roomId,
                    matchId = match.matchId,
                    playerId = session.playerId,
                    reason =
                        OnlineActiveMatchResourceLossReason.MATCH_NOT_FOUND,
                ),
                withTimeout(2_000L) {
                    resourceLossDeferred.await()
                },
            )

            withTimeout(2_000L) {
                while (
                    traceBuffer.snapshot().none { entry ->
                        entry.event.type ==
                            OnlineTraceType.POLLING_STOPPED &&
                            entry.event.attributes["reason"] ==
                                "active_resource_lost"
                    }
                ) {
                    delay(10L)
                }
            }

            val updatesRequestCountAtStop =
                apiClient.fetchMatchSnapshotsAfterRequests.size

            delay(100L)

            assertEquals(
                updatesRequestCountAtStop,
                apiClient.fetchMatchSnapshotsAfterRequests.size,
            )

            repository.leaveRoom()
        }

    @Test
    fun submit_action_remote_404_keeps_generic_transport_failure_without_resource_loss() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                submitActionFailure =
                    createNotFoundClientRequestException(),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val resourceLossDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchResourceLossEvents
                    .first()
            }
            val invalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            val action = createOnlinePassTurnAction(
                roomId = room.roomId,
                matchId = match.matchId,
                playerId = session.playerId,
                revision = match.revision,
                actionId = "remote-404-action",
            )

            val result = repository.submitAction(
                action = action,
            )

            assertEquals(
                false,
                result.accepted,
            )
            assertEquals(
                listOf(action),
                apiClient.submitActionRequests,
            )

            delay(100L)

            assertEquals(
                false,
                resourceLossDeferred.isCompleted,
            )
            assertEquals(
                false,
                invalidationDeferred.isCompleted,
            )

            resourceLossDeferred.cancel()
            invalidationDeferred.cancel()
            repository.leaveRoom()
        }

    @Test
    fun remote_500_does_not_emit_active_session_invalidation() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                submitActionFailure = createInternalServerErrorException(),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val invalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            val action = createOnlinePassTurnAction(
                roomId = room.roomId,
                matchId = match.matchId,
                playerId = session.playerId,
                revision = match.revision,
                actionId = "remote-500-action",
            )

            val result = repository.submitAction(
                action = action,
            )

            assertEquals(
                false,
                result.accepted,
            )
            assertEquals(
                listOf(action),
                apiClient.submitActionRequests,
            )

            delay(100L)

            assertEquals(
                false,
                invalidationDeferred.isCompleted,
            )

            invalidationDeferred.cancel()
            repository.leaveRoom()
        }

    @Test
    fun submit_action_local_auth_failure_emits_active_match_session_invalidation() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val sessionStore = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            )
            val sessionRepository =
                OnlineAnonymousSessionRepository(
                    store = sessionStore,
                    nowEpochMillis = { 1_000L },
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
                anonymousSessionRepository =
                    sessionRepository,
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val invalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            sessionStore.clear()

            val action = createOnlinePassTurnAction(
                roomId = room.roomId,
                matchId = match.matchId,
                playerId = session.playerId,
                revision = match.revision,
                actionId = "session-invalidated-action",
            )

            val result = repository.submitAction(
                action = action,
            )

            assertEquals(
                false,
                result.accepted,
            )
            assertEquals(
                emptyList<OnlinePlayerActionDto>(),
                apiClient.submitActionRequests,
            )
            assertEquals(
                OnlineActiveMatchSessionInvalidation(
                    roomId = room.roomId,
                    matchId = match.matchId,
                    playerId = session.playerId,
                ),
                withTimeout(2_000L) {
                    invalidationDeferred.await()
                },
            )
        }

    @Test
    fun polling_local_auth_failure_stops_polling_and_emits_active_match_session_invalidation() =
        runBlocking {
            val room = createInMatchRoomSnapshot()
            val match = createMatchSnapshot(
                revision = 1L,
            )
            val session = createAnonymousSession(
                playerId = "player-1",
            )
            val sessionStore = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            )
            val sessionRepository =
                OnlineAnonymousSessionRepository(
                    store = sessionStore,
                    nowEpochMillis = { 1_000L },
                )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 5L,
                ),
                coroutineDispatcher = Dispatchers.Default,
                traceLogger = createTraceLogger(traceBuffer),
                anonymousSessionRepository =
                    sessionRepository,
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = session.playerId,
                    playerName = "Jogador 1",
                ),
            )

            val invalidationDeferred = async(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                repository
                    .activeMatchSessionInvalidationEvents
                    .first()
            }

            sessionStore.clear()

            val invalidation = withTimeout(2_000L) {
                invalidationDeferred.await()
            }

            assertEquals(
                OnlineActiveMatchSessionInvalidation(
                    roomId = room.roomId,
                    matchId = match.matchId,
                    playerId = session.playerId,
                ),
                invalidation,
            )

            withTimeout(2_000L) {
                while (
                    traceBuffer.snapshot().none { entry ->
                        entry.event.type ==
                            OnlineTraceType.POLLING_STOPPED &&
                            entry.event.attributes["reason"] ==
                                "active_session_invalidated"
                    }
                ) {
                    delay(10L)
                }
            }

            val requestCountAtStop =
                apiClient.fetchRoomSnapshotRequests.size

            delay(30L)

            assertEquals(
                requestCountAtStop,
                apiClient.fetchRoomSnapshotRequests.size,
            )

            repository.leaveRoom()
        }

    @Test
    fun polling_stops_after_finished_room_snapshot() = runBlocking {
        val initialRoom = createInMatchRoomSnapshot()
        val finishedRoom = initialRoom.copy(
            status = OnlineRoomStatusDto.FINISHED,
            updatedAtEpochMillis = 2_000L,
        )
        val match = createMatchSnapshot(
            revision = 1L,
        )
        val traceBuffer = InMemoryOnlineTraceBuffer()
        val apiClient = FakeRemoteOnlineApiClient(
            createRoomResult = OnlineRoomOperationResultDto(
                accepted = true,
                roomSnapshot = initialRoom,
                localSeatIndex = 0,
            ),
            roomSnapshotsById = mutableMapOf(
                initialRoom.roomId to finishedRoom,
            ),
            matchSnapshotsById = mutableMapOf(
                match.matchId to match,
            ),
        )
        val repository = createRepository(
            apiClient = apiClient,
            pollingPolicy = OnlineRemotePollingPolicy(
                enabled = true,
                intervalMillis = 1L,
            ),
            coroutineDispatcher = Dispatchers.Default,
            traceLogger = createTraceLogger(traceBuffer),
        )

        repository.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "Jogador 1",
            ),
        )

        withTimeout(2_000L) {
            while (
                traceBuffer.snapshot().none { entry ->
                    entry.event.type ==
                        OnlineTraceType.POLLING_STOPPED &&
                        entry.event.attributes["reason"] ==
                        "room_finished"
                }
            ) {
                delay(10L)
            }
        }

        val requestCountAtStop =
            apiClient.fetchRoomSnapshotRequests.size
        delay(25L)

        assertEquals(1, requestCountAtStop)
        assertEquals(
            requestCountAtStop,
            apiClient.fetchRoomSnapshotRequests.size,
        )
        assertEquals(
            OnlineRoomStatusDto.FINISHED,
            repository.roomSnapshot.value?.status,
        )

        repository.leaveRoom()
    }

    @Test
    fun activate_pending_participation_match_resume_does_not_publish_when_valid_session_is_absent() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val match = createMatchSnapshot(
                revision = 5L,
            )
            val repository = createRepository(
                apiClient = FakeRemoteOnlineApiClient(),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(),
            )

            val result = repository.activatePendingParticipationMatchResume(
                preparation = OnlinePendingParticipationMatchResumePreparation.Ready(
                    binding = binding,
                    roomSnapshot = room,
                    matchSnapshot = match,
                ),
            )

            assertEquals(
                OnlinePendingParticipationMatchResumeActivation
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .MISSING_VALID_ANONYMOUS_SESSION,
                    ),
                result,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_returns_waiting_room_without_fetching_match_snapshot() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = null,
                localSeatIndex = 1,
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createWaitingRoomSnapshot().copy(
                hostPlayerId = binding.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = binding.playerId,
                        name = "Jogador 1",
                        seatIndex = binding.localSeatIndex,
                        connected = true,
                    ),
                ),
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation.WaitingForPlayers(
                    binding = binding,
                    roomSnapshot = room,
                ),
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun activate_pending_participation_room_resume_publishes_waiting_room_and_starts_polling_without_creating_room() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = null,
                localSeatIndex = 1,
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createWaitingRoomSnapshot().copy(
                hostPlayerId = binding.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = binding.playerId,
                        name = "Jogador 1",
                        seatIndex = binding.localSeatIndex,
                        connected = true,
                    ),
                ),
            )
            val apiClient = FakeRemoteOnlineApiClient()
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val repository = createRepository(
                apiClient = apiClient,
                traceLogger = createTraceLogger(
                    traceBuffer = traceBuffer,
                ),
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
                pollingPolicy = OnlineRemotePollingPolicy(
                    enabled = true,
                    intervalMillis = 60_000L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            val result = repository.activatePendingParticipationRoomResume(
                preparation =
                    OnlinePendingParticipationMatchResumePreparation
                        .WaitingForPlayers(
                            binding = binding,
                            roomSnapshot = room,
                        ),
            )

            assertEquals(
                OnlinePendingParticipationMatchResumeActivation.Activated,
                result,
            )
            assertEquals(
                room,
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                emptyList<CreateOnlineRoomRequestDto>(),
                apiClient.createRoomRequests,
            )
            assertEquals(
                emptyList<JoinOnlineRoomRequestDto>(),
                apiClient.joinRoomRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                1,
                traceBuffer.snapshot().count { entry ->
                    entry.event.type == OnlineTraceType.POLLING_STARTED &&
                            entry.event.context.roomId == room.roomId
                },
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_returns_not_recoverable_when_match_snapshot_room_id_diverges() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val mismatchedMatch = createMatchSnapshot(
                revision = 1L,
            ).copy(
                roomId = "room-2",
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    room.matchId.orEmpty() to mismatchedMatch,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .NoLongerRecoverable(
                        reason =
                            OnlinePendingParticipationMatchResumeInvalidReason
                                .MATCH_SNAPSHOT_ROOM_ID_MISMATCH,
                    ),
                result,
            )
            assertEquals(
                listOf(requireNotNull(room.matchId)),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_returns_not_recoverable_when_match_snapshot_match_id_diverges() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val mismatchedMatch = createMatchSnapshot(
                revision = 1L,
            ).copy(
                matchId = "match-2",
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    requireNotNull(room.matchId) to mismatchedMatch,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .NoLongerRecoverable(
                        reason =
                            OnlinePendingParticipationMatchResumeInvalidReason
                                .MATCH_SNAPSHOT_MATCH_ID_MISMATCH,
                    ),
                result,
            )
            assertEquals(
                listOf(requireNotNull(room.matchId)),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_returns_not_recoverable_when_in_match_has_no_match_id() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = null,
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            ).copy(
                matchId = null,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .NoLongerRecoverable(
                        reason =
                            OnlinePendingParticipationMatchResumeInvalidReason
                                .MISSING_MATCH_ID,
                    ),
                result,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_returns_temporarily_unavailable_when_match_read_fails() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                fetchMatchSnapshotFailure = IllegalStateException(
                    "partida indisponível",
                ),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .TemporarilyUnavailable(
                        reason =
                            "Falha ao preparar retomada de participação pendente online. " +
                                    "partida indisponível",
                    ),
                result,
            )
            assertEquals(
                listOf(requireNotNull(room.matchId)),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_room_404_returns_no_longer_recoverable_room_not_found_without_mutating_local_state() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val sessionStore = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            )
            val sessionRepository = OnlineAnonymousSessionRepository(
                store = sessionStore,
                nowEpochMillis = { 1_000L },
            )
            val bindingRepository = OnlineParticipationBindingRepository(
                store = InMemoryOnlineParticipationBindingStore(),
            )
            bindingRepository.save(
                binding = binding,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                fetchRoomSnapshotFailure = createNotFoundClientRequestException(),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository = sessionRepository,
                onlineParticipationBindingRepository = bindingRepository,
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .NoLongerRecoverable(
                        reason =
                            OnlinePendingParticipationMatchResumeInvalidReason
                                .ROOM_NOT_FOUND,
                    ),
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                session,
                sessionStore.read(),
            )
            assertEquals(
                binding,
                bindingRepository.getValidBindingOrNull(),
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_match_404_returns_no_longer_recoverable_match_not_found_without_mutating_local_state() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val sessionStore = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            )
            val sessionRepository = OnlineAnonymousSessionRepository(
                store = sessionStore,
                nowEpochMillis = { 1_000L },
            )
            val bindingRepository = OnlineParticipationBindingRepository(
                store = InMemoryOnlineParticipationBindingStore(),
            )
            bindingRepository.save(
                binding = binding,
            )

            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val apiClient = FakeRemoteOnlineApiClient(
                fetchMatchSnapshotFailure = createNotFoundClientRequestException(),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository = sessionRepository,
                onlineParticipationBindingRepository = bindingRepository,
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .NoLongerRecoverable(
                        reason =
                            OnlinePendingParticipationMatchResumeInvalidReason
                                .MATCH_NOT_FOUND,
                    ),
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                listOf(requireNotNull(room.matchId)),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                session,
                sessionStore.read(),
            )
            assertEquals(
                binding,
                bindingRepository.getValidBindingOrNull(),
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_returns_remote_session_rejected_when_room_read_returns_401_without_mutating_local_state() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val sessionStore = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            )
            val sessionRepository = OnlineAnonymousSessionRepository(
                store = sessionStore,
                nowEpochMillis = { 1_000L },
            )
            val bindingRepository = OnlineParticipationBindingRepository(
                store = InMemoryOnlineParticipationBindingStore(),
            )
            bindingRepository.save(
                binding = binding,
            )

            val preservedRoom = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val preservedMatch = createMatchSnapshot(
                revision = 5L,
            )
            val apiClient = FakeRemoteOnlineApiClient(
                fetchRoomSnapshotFailure = createUnauthorizedClientRequestException(),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository = sessionRepository,
                onlineParticipationBindingRepository = bindingRepository,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumeActivation.Activated,
                repository.activatePendingParticipationMatchResume(
                    preparation = OnlinePendingParticipationMatchResumePreparation.Ready(
                        binding = binding,
                        roomSnapshot = preservedRoom,
                        matchSnapshot = preservedMatch,
                    ),
                ),
            )

            apiClient.bearerAccessTokenUpdates.clear()
            apiClient.developmentPlayerIdUpdates.clear()

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .RemoteSessionRejected,
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                emptyList<CreateOnlineRoomRequestDto>(),
                apiClient.createRoomRequests,
            )
            assertEquals(
                emptyList<JoinOnlineRoomRequestDto>(),
                apiClient.joinRoomRequests,
            )
            assertEquals(
                emptyList<OnlinePlayerActionDto>(),
                apiClient.submitActionRequests,
            )
            assertEquals(
                0,
                apiClient.createAnonymousSessionCallCount,
            )
            assertEquals(
                preservedRoom,
                repository.roomSnapshot.value,
            )
            assertEquals(
                preservedMatch,
                repository.matchSnapshot.value,
            )
            assertEquals(
                session,
                sessionStore.read(),
            )
            assertEquals(
                binding,
                bindingRepository.getValidBindingOrNull(),
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    session.accessToken,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
            assertEquals(
                listOf(
                    null,
                    null,
                ),
                apiClient.developmentPlayerIdUpdates,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_returns_remote_session_rejected_when_match_read_returns_401_without_mutating_local_state() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val sessionStore = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            )
            val sessionRepository = OnlineAnonymousSessionRepository(
                store = sessionStore,
                nowEpochMillis = { 1_000L },
            )
            val bindingRepository = OnlineParticipationBindingRepository(
                store = InMemoryOnlineParticipationBindingStore(),
            )
            bindingRepository.save(
                binding = binding,
            )

            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val apiClient = FakeRemoteOnlineApiClient(
                fetchMatchSnapshotFailure = createUnauthorizedClientRequestException(),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository = sessionRepository,
                onlineParticipationBindingRepository = bindingRepository,
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .RemoteSessionRejected,
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                listOf(requireNotNull(room.matchId)),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                emptyList<CreateOnlineRoomRequestDto>(),
                apiClient.createRoomRequests,
            )
            assertEquals(
                emptyList<JoinOnlineRoomRequestDto>(),
                apiClient.joinRoomRequests,
            )
            assertEquals(
                emptyList<OnlinePlayerActionDto>(),
                apiClient.submitActionRequests,
            )
            assertEquals(
                0,
                apiClient.createAnonymousSessionCallCount,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                session,
                sessionStore.read(),
            )
            assertEquals(
                binding,
                bindingRepository.getValidBindingOrNull(),
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
            assertEquals(
                listOf(
                    null,
                    null,
                ),
                apiClient.developmentPlayerIdUpdates,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_keeps_server_5xx_as_temporarily_unavailable() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val sessionStore = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            )
            val sessionRepository = OnlineAnonymousSessionRepository(
                store = sessionStore,
                nowEpochMillis = { 1_000L },
            )
            val bindingRepository = OnlineParticipationBindingRepository(
                store = InMemoryOnlineParticipationBindingStore(),
            )
            bindingRepository.save(
                binding = binding,
            )

            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val apiClient = FakeRemoteOnlineApiClient(
                fetchMatchSnapshotFailure = createInternalServerErrorException(),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository = sessionRepository,
                onlineParticipationBindingRepository = bindingRepository,
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertTrue(
                result is OnlinePendingParticipationMatchResumePreparation
                    .TemporarilyUnavailable,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                listOf(requireNotNull(room.matchId)),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                emptyList<CreateOnlineRoomRequestDto>(),
                apiClient.createRoomRequests,
            )
            assertEquals(
                emptyList<JoinOnlineRoomRequestDto>(),
                apiClient.joinRoomRequests,
            )
            assertEquals(
                emptyList<OnlinePlayerActionDto>(),
                apiClient.submitActionRequests,
            )
            assertEquals(
                0,
                apiClient.createAnonymousSessionCallCount,
            )
            assertEquals(
                session,
                sessionStore.read(),
            )
            assertEquals(
                binding,
                bindingRepository.getValidBindingOrNull(),
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_skips_http_when_valid_anonymous_session_is_absent() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val apiClient = FakeRemoteOnlineApiClient()

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(),
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .MISSING_VALID_ANONYMOUS_SESSION,
                    ),
                result,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                emptyList<String?>(),
                apiClient.bearerAccessTokenUpdates,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_skips_http_when_anonymous_session_identity_diverges() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val apiClient = FakeRemoteOnlineApiClient()

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = createAnonymousSession(
                            playerId = "anonymous-player-2",
                        ),
                    ),
            )

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation
                    .NotAttempted(
                        reason = OnlinePendingParticipationRemoteBlockReason
                            .ANONYMOUS_SESSION_IDENTITY_MISMATCH,
                    ),
                result,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                emptyList<String?>(),
                apiClient.bearerAccessTokenUpdates,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun prepare_pending_participation_match_resume_restores_active_participant_authentication() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshotForBinding(
                binding = binding,
            )
            val match = createMatchSnapshot(
                revision = 1L,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = binding.localSeatIndex,
                ),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
                matchSnapshotsById = mutableMapOf(
                    match.matchId to match,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-local",
                    playerName = "Jogador 1",
                ),
            )

            apiClient.bearerAccessTokenUpdates.clear()
            apiClient.developmentPlayerIdUpdates.clear()

            val result = repository.preparePendingParticipationMatchResume(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationMatchResumePreparation.Ready(
                    binding = binding,
                    roomSnapshot = room,
                    matchSnapshot = match,
                ),
                result,
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    session.accessToken,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
            assertEquals(
                listOf(
                    null,
                    null,
                ),
                apiClient.developmentPlayerIdUpdates,
            )
            assertEquals(
                room,
                repository.roomSnapshot.value,
            )
            assertEquals(
                match,
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun inspect_pending_participation_uses_persisted_bearer_and_returns_recoverable_without_publishing_snapshots() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-1",
                localSeatIndex = 2,
            )
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createInMatchRoomSnapshot().copy(
                hostPlayerId = binding.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = binding.playerId,
                        name = "Jogador 1",
                        seatIndex = binding.localSeatIndex,
                        connected = true,
                    ),
                ),
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection.Recoverable(
                    roomSnapshot = room,
                ),
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                0,
                apiClient.createAnonymousSessionCallCount,
            )
            assertEquals(
                emptyList<OnlinePlayerActionDto>(),
                apiClient.submitActionRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
            assertEquals(
                listOf(
                    null,
                    null,
                ),
                apiClient.developmentPlayerIdUpdates,
            )
        }

    @Test
    fun inspect_pending_participation_skips_http_when_valid_anonymous_session_is_absent() =
        runBlocking {
            val binding = createPendingParticipationBinding()

            val apiClient = FakeRemoteOnlineApiClient()

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection.NotAttempted(
                    reason = OnlinePendingParticipationRemoteBlockReason
                        .MISSING_VALID_ANONYMOUS_SESSION,
                ),
                result,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String?>(),
                apiClient.bearerAccessTokenUpdates,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun inspect_pending_participation_skips_http_when_anonymous_session_identity_diverges() =
        runBlocking {
            val binding = createPendingParticipationBinding()

            val apiClient = FakeRemoteOnlineApiClient()

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = createAnonymousSession(
                            playerId = "anonymous-player-2",
                        ),
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection.NotAttempted(
                    reason = OnlinePendingParticipationRemoteBlockReason
                        .ANONYMOUS_SESSION_IDENTITY_MISMATCH,
                ),
                result,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String?>(),
                apiClient.bearerAccessTokenUpdates,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun inspect_pending_participation_returns_not_recoverable_when_room_is_closed() =
        runBlocking {
            val binding = createPendingParticipationBinding()
            val room = createWaitingRoomSnapshot().copy(
                status = OnlineRoomStatusDto.CLOSED,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = createAnonymousSession(
                            playerId = binding.playerId,
                        ),
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .ROOM_CLOSED,
                    ),
                result,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun inspect_pending_participation_returns_not_recoverable_when_local_player_is_missing() =
        runBlocking {
            val binding = createPendingParticipationBinding()
            val room = createWaitingRoomSnapshot()

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = createAnonymousSession(
                            playerId = binding.playerId,
                        ),
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .PLAYER_NOT_FOUND,
                    ),
                result,
            )
        }

    @Test
    fun inspect_pending_participation_returns_not_recoverable_when_local_seat_diverges() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                localSeatIndex = 2,
            )
            val room = createWaitingRoomSnapshot().copy(
                hostPlayerId = binding.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = binding.playerId,
                        name = "Jogador 1",
                        seatIndex = 1,
                        connected = true,
                    ),
                ),
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = createAnonymousSession(
                            playerId = binding.playerId,
                        ),
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .LOCAL_SEAT_MISMATCH,
                    ),
                result,
            )
        }

    @Test
    fun inspect_pending_participation_returns_not_recoverable_when_saved_match_id_diverges() =
        runBlocking {
            val binding = createPendingParticipationBinding(
                matchId = "match-saved",
            )
            val room = createInMatchRoomSnapshot().copy(
                hostPlayerId = binding.playerId,
                matchId = "match-current",
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = binding.playerId,
                        name = "Jogador 1",
                        seatIndex = binding.localSeatIndex,
                        connected = true,
                    ),
                ),
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = createAnonymousSession(
                            playerId = binding.playerId,
                        ),
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .MATCH_ID_MISMATCH,
                    ),
                result,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
        }

    @Test
    fun inspect_pending_participation_returns_remote_session_rejected_on_401_without_mutating_local_state() =
        runBlocking {
            val binding = createPendingParticipationBinding()
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val sessionStore = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            )
            val sessionRepository = OnlineAnonymousSessionRepository(
                store = sessionStore,
                nowEpochMillis = { 1_000L },
            )
            val bindingRepository = OnlineParticipationBindingRepository(
                store = InMemoryOnlineParticipationBindingStore(),
            )
            bindingRepository.save(
                binding = binding,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                fetchRoomSnapshotFailure = createUnauthorizedClientRequestException(),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository = sessionRepository,
                onlineParticipationBindingRepository = bindingRepository,
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection.RemoteSessionRejected,
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertEquals(
                emptyList<String>(),
                apiClient.fetchMatchSnapshotRequests,
            )
            assertEquals(
                emptyList<CreateOnlineRoomRequestDto>(),
                apiClient.createRoomRequests,
            )
            assertEquals(
                emptyList<JoinOnlineRoomRequestDto>(),
                apiClient.joinRoomRequests,
            )
            assertEquals(
                emptyList<OnlinePlayerActionDto>(),
                apiClient.submitActionRequests,
            )
            assertEquals(
                0,
                apiClient.createAnonymousSessionCallCount,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                session,
                sessionStore.read(),
            )
            assertEquals(
                binding,
                bindingRepository.getValidBindingOrNull(),
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
            assertEquals(
                listOf(
                    null,
                    null,
                ),
                apiClient.developmentPlayerIdUpdates,
            )
        }

    @Test
    fun inspect_pending_participation_returns_not_recoverable_when_room_read_returns_404() =
        runBlocking {
            val binding = createPendingParticipationBinding()
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val apiClient = FakeRemoteOnlineApiClient(
                fetchRoomSnapshotFailure = createNotFoundClientRequestException(),
            )
            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .ROOM_NOT_FOUND,
                    ),
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertNull(repository.roomSnapshot.value)
            assertNull(repository.matchSnapshot.value)
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
        }

    @Test
    fun inspect_pending_participation_returns_temporarily_unavailable_when_room_read_fails() =
        runBlocking {
            val binding = createPendingParticipationBinding()
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                fetchRoomSnapshotFailure = IllegalStateException(
                    "servidor indisponível",
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection
                    .TemporarilyUnavailable(
                        reason =
                            "Falha ao consultar participação pendente online. " +
                                    "servidor indisponível",
                    ),
                result,
            )
            assertEquals(
                listOf(binding.roomId),
                apiClient.fetchRoomSnapshotRequests,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    null,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
        }

    @Test
    fun inspect_pending_participation_returns_not_recoverable_when_room_id_diverges() =
        runBlocking {
            val binding = createPendingParticipationBinding()
            val room = createWaitingRoomSnapshot().copy(
                roomId = "room-2",
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    binding.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = createAnonymousSession(
                            playerId = binding.playerId,
                        ),
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .ROOM_ID_MISMATCH,
                    ),
                result,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun inspect_pending_participation_returns_not_recoverable_when_room_is_finished() =
        runBlocking {
            val binding = createPendingParticipationBinding()
            val room = createWaitingRoomSnapshot().copy(
                status = OnlineRoomStatusDto.FINISHED,
            )

            val apiClient = FakeRemoteOnlineApiClient(
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = createAnonymousSession(
                            playerId = binding.playerId,
                        ),
                    ),
            )

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable(
                        reason = OnlinePendingParticipationRemoteInvalidReason
                            .ROOM_FINISHED,
                    ),
                result,
            )
            assertNull(
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    @Test
    fun inspect_pending_participation_restores_active_participant_authentication() =
        runBlocking {
            val binding = createPendingParticipationBinding()
            val session = createAnonymousSession(
                playerId = binding.playerId,
            )
            val room = createWaitingRoomSnapshot().copy(
                hostPlayerId = binding.playerId,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = binding.playerId,
                        name = "Jogador 1",
                        seatIndex = binding.localSeatIndex,
                        connected = true,
                    ),
                ),
            )

            val apiClient = FakeRemoteOnlineApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = binding.localSeatIndex,
                ),
                roomSnapshotsById = mutableMapOf(
                    room.roomId to room,
                ),
            )

            val repository = createRepository(
                apiClient = apiClient,
                anonymousSessionRepository =
                    createAnonymousSessionRepository(
                        session = session,
                    ),
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-local",
                    playerName = "Jogador 1",
                ),
            )

            apiClient.bearerAccessTokenUpdates.clear()
            apiClient.developmentPlayerIdUpdates.clear()

            val result = repository.inspectPendingParticipation(
                binding = binding,
            )

            assertEquals(
                OnlinePendingParticipationRemoteInspection.Recoverable(
                    roomSnapshot = room,
                ),
                result,
            )
            assertEquals(
                listOf(
                    session.accessToken,
                    session.accessToken,
                ),
                apiClient.bearerAccessTokenUpdates,
            )
            assertEquals(
                listOf(
                    null,
                    null,
                ),
                apiClient.developmentPlayerIdUpdates,
            )
            assertEquals(
                room,
                repository.roomSnapshot.value,
            )
            assertNull(
                repository.matchSnapshot.value,
            )
        }

    private fun createPendingParticipationBinding(
        matchId: String? = null,
        localSeatIndex: Int = 0,
    ): OnlineParticipationBinding {
        return OnlineParticipationBinding(
            roomId = "room-1",
            matchId = matchId,
            playerId = "anonymous-player-1",
            localSeatIndex = localSeatIndex,
        )
    }

    private suspend fun createNotFoundClientRequestException(): ClientRequestException {
        val httpClient = HttpClient(
            MockEngine {
                respond(
                    content = "",
                    status = HttpStatusCode.NotFound,
                )
            },
        ) {
            expectSuccess = true
        }

        return try {
            httpClient.get(
                urlString = "http://localhost/not-found",
            )
            error("Expected HTTP 404 to throw ClientRequestException.")
        } catch (error: ClientRequestException) {
            error
        } finally {
            httpClient.close()
        }
    }

    private suspend fun createUnauthorizedClientRequestException(): ClientRequestException {
        val httpClient = HttpClient(
            MockEngine {
                respond(
                    content = "",
                    status = HttpStatusCode.Unauthorized,
                )
            },
        ) {
            expectSuccess = true
        }

        return try {
            httpClient.get(
                urlString = "http://localhost/unauthorized",
            )
            error("A resposta 401 deveria lançar ClientRequestException.")
        } catch (error: ClientRequestException) {
            error
        } finally {
            httpClient.close()
        }
    }

    private suspend fun createInternalServerErrorException(): ServerResponseException {
        val httpClient = HttpClient(
            MockEngine {
                respond(
                    content = "",
                    status = HttpStatusCode.InternalServerError,
                )
            },
        ) {
            expectSuccess = true
        }

        return try {
            httpClient.get(
                urlString = "http://localhost/internal-server-error",
            )
            error("A resposta 500 deveria lançar ServerResponseException.")
        } catch (error: ServerResponseException) {
            error
        } finally {
            httpClient.close()
        }
    }

    private fun createAnonymousSession(
        playerId: String,
    ): OnlineAnonymousSessionDto {
        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = "session-access-token",
            expiresAtEpochMillis = 2_000L,
        )
    }

    private fun createAnonymousSessionRepository(
        session: OnlineAnonymousSessionDto? = null,
    ): OnlineAnonymousSessionRepository {
        return OnlineAnonymousSessionRepository(
            store = InMemoryOnlineAnonymousSessionStore(
                initialSession = session,
            ),
            nowEpochMillis = { 1_000L },
        )
    }

    private fun createRepository(
        apiClient: RemoteOnlineApiClient,
        pollingPolicy: OnlineRemotePollingPolicy =
            OnlineRemotePollingPolicy.Disabled,
        coroutineDispatcher: CoroutineDispatcher =
            Dispatchers.Main.immediate,
        traceLogger: OnlineTraceLogger = OnlineTraceLogger(),
        anonymousSessionRepository: OnlineAnonymousSessionRepository? = null,
        onlineParticipationBindingRepository:
            OnlineParticipationBindingRepository? = null,
    ): RemoteOnlineRoomRepository {
        return RemoteOnlineRoomRepository(
            config = OnlineBackendConfig.remote(
                baseUrl = "http://localhost:8080",
            ),
            apiClient = apiClient,
            pollingPolicy = pollingPolicy,
            coroutineDispatcher = coroutineDispatcher,
            traceLogger = traceLogger,
            nowEpochMillis = { 1_000L },
            anonymousSessionRepository = anonymousSessionRepository,
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
        )
    }

    private fun createTraceBatch(): OnlineTraceBatchDto {
        return OnlineTraceBatchDto(
            entries = listOf(
                OnlineTraceEntry(
                    sequence = 1L,
                    event = OnlineTraceEvent(
                        occurredAtEpochMillis = 1_000L,
                        level = OnlineTraceLevel.INFO,
                        source = OnlineTraceSource.CLIENT_UI,
                        type = OnlineTraceType.ANIMATION_FINISHED,
                        context = OnlineTraceContext(
                            clientSessionId = "android-session-1",
                            roomId = "room-1",
                            matchId = "match-1",
                        ),
                    ),
                ),
            ),
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

    private fun createInMatchRoomSnapshotForBinding(
        binding: OnlineParticipationBinding,
    ): OnlineRoomSnapshotDto {
        return createInMatchRoomSnapshot().copy(
            hostPlayerId = binding.playerId,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = binding.playerId,
                    name = "Jogador 1",
                    seatIndex = binding.localSeatIndex,
                    connected = true,
                ),
            ),
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
        private val anonymousSession: OnlineAnonymousSessionDto? = null,
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
        private val submitTraceBatchResult:
            OnlineTraceBatchResultDto =
            OnlineTraceBatchResultDto(
                accepted = true,
                storedEntryCount = 1,
            ),
        private val createRoomFailure: Throwable? = null,
        private val joinRoomFailure: Throwable? = null,
        private val submitActionFailure: Throwable? = null,
        private val submitTraceBatchFailure: Throwable? = null,
        private val fetchRoomSnapshotFailure: Throwable? = null,
        private val fetchMatchSnapshotFailure: Throwable? = null,
        private val fetchMatchSnapshotsAfterFailure: Throwable? = null,
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
        val submitTraceBatchRequests =
            mutableListOf<OnlineTraceBatchDto>()
        val fetchRoomSnapshotRequests = mutableListOf<String>()
        val fetchMatchSnapshotRequests = mutableListOf<String>()
        val fetchMatchSnapshotsAfterRequests = mutableListOf<Pair<String, Long>>()
        val bearerAccessTokenUpdates = mutableListOf<String?>()
        val developmentPlayerIdUpdates = mutableListOf<String?>()

        var createAnonymousSessionCallCount: Int = 0
            private set

        override fun setDevelopmentPlayerId(
            playerId: String?,
        ) {
            developmentPlayerIdUpdates += playerId
        }

        override fun setBearerAccessToken(
            accessToken: String?,
        ) {
            bearerAccessTokenUpdates += accessToken
        }

        override suspend fun createAnonymousSession(): OnlineAnonymousSessionDto {
            createAnonymousSessionCallCount += 1

            return requireNotNull(anonymousSession) {
                "Sessão anônima não configurada para o teste."
            }
        }

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

        override suspend fun submitTraceBatch(
            batch: OnlineTraceBatchDto,
        ): OnlineTraceBatchResultDto {
            submitTraceBatchRequests += batch

            submitTraceBatchFailure?.let { error ->
                throw error
            }

            return submitTraceBatchResult
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

            fetchMatchSnapshotsAfterFailure?.let { error ->
                throw error
            }

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

    private class InMemoryOnlineParticipationBindingStore :
        OnlineParticipationBindingStore {
        private var storedBinding: OnlineParticipationBinding? = null

        override fun read(): OnlineParticipationBinding? {
            return storedBinding
        }

        override fun write(
            binding: OnlineParticipationBinding,
        ) {
            storedBinding = binding
        }

        override fun clear() {
            storedBinding = null
        }
    }

    private class InMemoryOnlineAnonymousSessionStore(
        initialSession: OnlineAnonymousSessionDto? = null,
    ) : OnlineAnonymousSessionStore {
        private var storedSession: OnlineAnonymousSessionDto? =
            initialSession

        override fun read(): OnlineAnonymousSessionDto? {
            return storedSession
        }

        override fun write(
            session: OnlineAnonymousSessionDto,
        ) {
            storedSession = session
        }

        override fun clear() {
            storedSession = null
        }
    }

}