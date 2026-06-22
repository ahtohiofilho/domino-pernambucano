package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.createOnlinePassTurnAction
import com.ahtohiofilho.dominopernambucano.online.createOnlinePlayMoveAction
import com.ahtohiofilho.dominopernambucano.online.createOnlineSnapshotRequestAction
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOnlineServerStoreTest {
    @Test
    fun fourth_player_starts_authoritative_match() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val createRoomResult = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "Jogador 1",
            ),
        )

        assertTrue(createRoomResult.accepted)

        val room = requireNotNull(
            createRoomResult.roomSnapshot,
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-2",
            playerName = "Jogador 2",
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-3",
            playerName = "Jogador 3",
        )

        val fourthPlayerResult = joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-4",
            playerName = "Jogador 4",
        )

        assertTrue(fourthPlayerResult.accepted)

        val startedRoom = requireNotNull(
            fourthPlayerResult.roomSnapshot,
        )

        assertEquals(
            4,
            startedRoom.players.size,
        )

        assertNotNull(startedRoom.matchId)

        val matchSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = requireNotNull(startedRoom.matchId),
            ),
        )

        assertEquals(
            startedRoom.roomId,
            matchSnapshot.roomId,
        )

        assertEquals(
            1L,
            matchSnapshot.revision,
        )
    }

    @Test
    fun second_human_starts_match_with_development_bots_when_enabled() {
        val store = InMemoryOnlineServerStore(
            autoFillDevelopmentBotsAfterTwoHumanPlayers = true,
            nowEpochMillis = { 1_000L },
        )

        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        val secondPlayerResult = joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-2",
            playerName = "Jogador 2",
        )

        assertTrue(secondPlayerResult.accepted)

        val startedRoom = requireNotNull(
            secondPlayerResult.roomSnapshot,
        )

        assertEquals(
            OnlineRoomStatusDto.IN_MATCH,
            startedRoom.status,
        )

        assertEquals(
            listOf(0, 1, 2, 3),
            startedRoom.players.mapNotNull { player ->
                player.seatIndex
            },
        )

        assertEquals(
            "development-bot-seat-2",
            startedRoom.players.first { player ->
                player.seatIndex == 2
            }.playerId,
        )

        assertEquals(
            "development-bot-seat-3",
            startedRoom.players.first { player ->
                player.seatIndex == 3
            }.playerId,
        )

        val matchId = requireNotNull(
            startedRoom.matchId,
        )

        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertEquals(
            1L,
            initialSnapshot.revision,
        )
    }

    @Test
    fun development_bot_advances_after_human_zero_finishes_a_turn() {
        val store = InMemoryOnlineServerStore(
            autoFillDevelopmentBotsAfterTwoHumanPlayers = true,
            nowEpochMillis = { 1_000L },
        )

        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        val startedRoom = requireNotNull(
            joinPlayer(
                store = store,
                roomCode = room.roomCode,
                playerId = "player-2",
                playerName = "Jogador 2",
            ).roomSnapshot,
        )

        val matchId = requireNotNull(
            startedRoom.matchId,
        )

        var snapshot = getSnapshotAtHumanTurn(
            store = store,
            matchId = matchId,
        )

        if (snapshot.gameState.currentPlayerIndex == 1) {
            val playerOneAction = submitCurrentHumanAction(
                store = store,
                roomId = startedRoom.roomId,
                matchId = matchId,
                snapshot = snapshot,
            )

            assertTrue(playerOneAction.accepted)

            snapshot = getSnapshotAtHumanTurn(
                store = store,
                matchId = matchId,
            )
        }

        assertEquals(
            0,
            snapshot.gameState.currentPlayerIndex,
        )

        val playerZeroAction = submitCurrentHumanAction(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
            snapshot = snapshot,
        )

        assertTrue(playerZeroAction.accepted)

        store.advanceAuthoritativeTime()

        val snapshotAfterBotTurn = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertTrue(
            snapshotAfterBotTurn.revision >
                    requireNotNull(playerZeroAction.revision),
        )

        assertTrue(
            snapshotAfterBotTurn.gameState != snapshot.gameState,
        )
    }

    @Test
    fun match_snapshot_read_is_observational_even_when_clock_has_elapsed() {
        var now = 1_000L

        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )

        val startedRoom = startFourHumanMatch(
            store = store,
        )

        val matchId = requireNotNull(startedRoom.matchId)
        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        now += 31_000L

        val firstRead = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )
        val secondRead = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertEquals(initialSnapshot, firstRead)
        assertEquals(firstRead, secondRead)
        assertEquals(1L, secondRead.revision)
    }

    @Test
    fun snapshot_request_is_observational_after_clock_has_expired() {
        var now = 1_000L

        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )

        val startedRoom = startFourHumanMatch(
            store = store,
        )

        val matchId = requireNotNull(startedRoom.matchId)
        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        now += 31_000L

        val result = store.submitAction(
            createOnlineSnapshotRequestAction(
                roomId = startedRoom.roomId,
                matchId = matchId,
                playerId = "player-1",
                revision = initialSnapshot.revision,
            ),
        )

        val snapshotAfterRequest = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertTrue(result.accepted)
        assertEquals(initialSnapshot.revision, result.revision)
        assertEquals(initialSnapshot, snapshotAfterRequest)
    }

    @Test
    fun authoritative_tick_resolves_expired_turn_without_client_request() {
        var now = 1_000L

        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )

        val startedRoom = startFourHumanMatch(
            store = store,
        )

        val matchId = requireNotNull(startedRoom.matchId)
        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        now += 31_000L
        store.advanceAuthoritativeTime()

        val snapshotAfterTick = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertTrue(snapshotAfterTick.revision > initialSnapshot.revision)
        assertTrue(
            snapshotAfterTick.gameState != initialSnapshot.gameState,
        )
        assertTrue(
            initialSnapshot.gameState.currentPlayerIndex in
                    snapshotAfterTick.automaticPlayerIndexes,
        )
    }

    @Test
    fun repeated_action_id_returns_cached_result_without_advancing_revision() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-2",
            playerName = "Jogador 2",
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-3",
            playerName = "Jogador 3",
        )

        val startedRoom = requireNotNull(
            joinPlayer(
                store = store,
                roomCode = room.roomCode,
                playerId = "player-4",
                playerName = "Jogador 4",
            ).roomSnapshot,
        )

        val matchId = requireNotNull(startedRoom.matchId)

        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        val action = createOnlineSnapshotRequestAction(
            roomId = startedRoom.roomId,
            matchId = matchId,
            playerId = "player-1",
            revision = initialSnapshot.revision,
            actionId = "repeated-action",
        )

        val firstResult = store.submitAction(
            action = action,
        )

        val repeatedResult = store.submitAction(
            action = action,
        )

        assertTrue(firstResult.accepted)
        assertEquals(firstResult, repeatedResult)
    }

    @Test
    fun authoritative_tick_records_timeout_resolution_and_snapshot_publication() {
        var now = 1_000L

        val traceSink = RecordingOnlineTraceSink()

        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            traceLogger = OnlineTraceLogger(
                sink = traceSink,
                nowEpochMillis = { now },
            ),
        )

        val startedRoom = startFourHumanMatch(
            store = store,
        )

        val matchId = requireNotNull(
            startedRoom.matchId,
        )

        traceSink.clear()

        now += 31_000L

        store.advanceAuthoritativeTime()

        assertEquals(
            listOf(
                OnlineTraceType.CLOCK_EXPIRED,
                OnlineTraceType.AUTOMATIC_TURN_RESOLVED,
                OnlineTraceType.SNAPSHOT_PUBLISHED,
                OnlineTraceType.AUTHORITATIVE_TICK,
            ),
            traceSink.events.map { event ->
                event.type
            },
        )

        assertEquals(
            matchId,
            traceSink.events
                .first { event ->
                    event.type == OnlineTraceType.SNAPSHOT_PUBLISHED
                }
                .context
                .matchId,
        )
    }

    @Test
    fun repeated_action_id_records_deduplicated_action_without_new_revision() {
        val traceSink = RecordingOnlineTraceSink()

        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            traceLogger = OnlineTraceLogger(
                sink = traceSink,
                nowEpochMillis = { 1_000L },
            ),
        )

        val startedRoom = startFourHumanMatch(
            store = store,
        )

        val matchId = requireNotNull(
            startedRoom.matchId,
        )

        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        traceSink.clear()

        val action = createOnlineSnapshotRequestAction(
            roomId = startedRoom.roomId,
            matchId = matchId,
            playerId = "player-1",
            revision = initialSnapshot.revision,
            actionId = "deduplicated-action",
        )

        val firstResult = store.submitAction(
            action = action,
        )

        val repeatedResult = store.submitAction(
            action = action,
        )

        assertEquals(
            firstResult,
            repeatedResult,
        )

        assertEquals(
            listOf(
                OnlineTraceType.ACTION_SUBMITTED,
                OnlineTraceType.ACTION_ACCEPTED,
                OnlineTraceType.ACTION_SUBMITTED,
                OnlineTraceType.ACTION_DEDUPLICATED,
            ),
            traceSink.events.map { event ->
                event.type
            },
        )

        assertEquals(
            "deduplicated-action",
            traceSink.events.last().context.actionId,
        )
    }

    private fun getSnapshotAtHumanTurn(
        store: InMemoryOnlineServerStore,
        matchId: String,
    ): OnlineMatchSnapshotDto {
        repeat(4) {
            val snapshot = requireNotNull(
                store.getMatchSnapshot(
                    matchId = matchId,
                ),
            )

            if (snapshot.gameState.currentPlayerIndex in 0..1) {
                return snapshot
            }

            store.advanceAuthoritativeTime()
        }

        error(
            "A partida não alcançou uma vez humana após o processamento dos bots.",
        )
    }

    private fun submitCurrentHumanAction(
        store: InMemoryOnlineServerStore,
        roomId: String,
        matchId: String,
        snapshot: OnlineMatchSnapshotDto,
    ) = snapshot.toRuntimeState(
        localPlayerIndex = snapshot.gameState.currentPlayerIndex,
    ).let { runtimeState ->
        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        require(
            currentPlayerIndex in 0..1,
        ) {
            "A ação de teste só pode ser enviada por um jogador humano."
        }

        val playerId = "player-${currentPlayerIndex + 1}"

        val move = findBasicBotMove(
            state = runtimeState.gameState,
        )

        val action = if (move != null) {
            createOnlinePlayMoveAction(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                revision = snapshot.revision,
                move = move,
            )
        } else {
            createOnlinePassTurnAction(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                revision = snapshot.revision,
            )
        }

        store.submitAction(
            action = action,
        )
    }

    private fun startFourHumanMatch(
        store: InMemoryOnlineServerStore,
    ): OnlineRoomSnapshotDto {
        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-2",
            playerName = "Jogador 2",
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-3",
            playerName = "Jogador 3",
        )

        return requireNotNull(
            joinPlayer(
                store = store,
                roomCode = room.roomCode,
                playerId = "player-4",
                playerName = "Jogador 4",
            ).roomSnapshot,
        )
    }

    private fun joinPlayer(
        store: InMemoryOnlineServerStore,
        roomCode: String,
        playerId: String,
        playerName: String,
    ) = store.joinRoom(
        JoinOnlineRoomRequestDto(
            roomCode = roomCode,
            localPlayerId = playerId,
            playerName = playerName,
        ),
    )

    private class RecordingOnlineTraceSink : OnlineTraceSink {
        val events = mutableListOf<OnlineTraceEvent>()

        override fun record(
            event: OnlineTraceEvent,
        ) {
            events += event
        }

        fun clear() {
            events.clear()
        }
    }
}