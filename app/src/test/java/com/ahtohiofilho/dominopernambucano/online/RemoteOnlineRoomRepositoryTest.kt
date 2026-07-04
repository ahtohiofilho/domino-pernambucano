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
    fun prepare_pending_participation_match_resume_returns_not_in_match_without_fetching_match_snapshot() =
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
                OnlinePendingParticipationMatchResumePreparation.NotInMatch(
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
            traceLogger = traceLogger,
            nowEpochMillis = { 1_000L },
            anonymousSessionRepository = anonymousSessionRepository,
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
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