package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.AutomaticDecisionCadencePolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
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
    fun created_room_host_is_explicitly_human() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val result = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "human-host",
                playerName = "Anfitrião humano",
            ),
        )

        assertTrue(result.accepted)

        val room = requireNotNull(
            result.roomSnapshot,
        )

        val host = room.players.single()

        assertEquals(
            "human-host",
            host.playerId,
        )

        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            host.participantType,
        )
    }

    @Test
    fun joined_player_is_explicitly_human_without_id_inference() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "human-host",
                    playerName = "Anfitrião humano",
                ),
            ).roomSnapshot,
        )

        val botLikeHumanPlayerId =
            "development-bot-seat-2-manual-human"

        val joinResult = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = botLikeHumanPlayerId,
                playerName = "Humano com identificador opaco",
            ),
        )

        assertTrue(joinResult.accepted)

        val joinedPlayer = requireNotNull(
            joinResult.roomSnapshot
                ?.players
                ?.firstOrNull { player ->
                    player.playerId == botLikeHumanPlayerId
                },
        )

        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            joinedPlayer.participantType,
        )
    }

    @Test
    fun human_reconnection_preserves_permanent_participant_type() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "human-host",
                    playerName = "Anfitrião humano",
                ),
            ).roomSnapshot,
        )

        val firstJoin = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "human-player-2",
                playerName = "Humano original",
            ),
        )

        assertTrue(firstJoin.accepted)

        val originalPlayer = requireNotNull(
            firstJoin.roomSnapshot
                ?.players
                ?.firstOrNull { player ->
                    player.playerId == "human-player-2"
                },
        )

        val reconnectResult = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "human-player-2",
                playerName = "Humano reconectado",
            ),
        )

        assertTrue(reconnectResult.accepted)

        val reconnectedPlayer = requireNotNull(
            reconnectResult.roomSnapshot
                ?.players
                ?.firstOrNull { player ->
                    player.playerId == "human-player-2"
                },
        )

        assertEquals(
            originalPlayer.seatIndex,
            reconnectedPlayer.seatIndex,
        )

        assertEquals(
            "Humano reconectado",
            reconnectedPlayer.name,
        )

        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            reconnectedPlayer.participantType,
        )
    }

    @Test
    fun fourth_player_waits_until_host_starts_authoritative_match() {
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

        val fullWaitingRoom = requireNotNull(
            fourthPlayerResult.roomSnapshot,
        )

        assertEquals(
            4,
            fullWaitingRoom.players.size,
        )
        assertEquals(
            OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            fullWaitingRoom.status,
        )
        assertEquals(null, fullWaitingRoom.matchId)

        val startResult = store.startPrivateRoom(
            PrivateRoomStartRequestDto(
                roomId = fullWaitingRoom.roomId,
                localPlayerId = "player-1",
            ),
        )
        assertTrue(startResult.accepted)

        val startedRoom = requireNotNull(
            startResult.roomSnapshot,
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
    fun accepted_human_move_reloads_main_clock_from_authoritative_reserve() {
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
        val initialRuntimeState = initialSnapshot.toRuntimeState(
            localPlayerIndex = initialSnapshot.gameState.currentPlayerIndex,
        )
        val currentPlayerIndex = initialRuntimeState.gameState.currentPlayerIndex
        val currentPlayerId = requireNotNull(
            startedRoom.players.firstOrNull { player ->
                player.seatIndex == currentPlayerIndex
            },
        ).playerId
        val move = requireNotNull(
            findBasicBotMove(
                state = initialRuntimeState.gameState,
            ),
        )

        val playableSnapshot = releaseRoundIntroForTest(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
        )

        now += 10_000L

        val result = store.submitAction(
            action = createOnlinePlayMoveAction(
                roomId = startedRoom.roomId,
                matchId = matchId,
                playerId = currentPlayerId,
                revision = playableSnapshot.revision,
                move = move,
                actionId = "clock-reload-after-ten-seconds",
            ),
        )

        assertTrue(result.accepted)

        val updatedSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertEquals(
            20_000L,
            updatedSnapshot.playerClockMillis[currentPlayerIndex],
        )
        assertEquals(
            10_000L,
            updatedSnapshot.playerClockReserveMillis[currentPlayerIndex],
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

        assertEquals(
            listOf(2, 3),
            startedRoom.players
                .filter { player ->
                    player.participantType ==
                        OnlineParticipantTypeDto.APPLICATION
                }
                .mapNotNull { player ->
                    player.seatIndex
                },
        )

        assertTrue(
            startedRoom.players
                .filter { player ->
                    player.seatIndex in 0..1
                }
                .all { player ->
                    player.participantType ==
                        OnlineParticipantTypeDto.HUMAN
                },
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

        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            initialSnapshot.gameState.players[0].participantType,
        )

        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            initialSnapshot.gameState.players[1].participantType,
        )

        assertEquals(
            OnlineParticipantTypeDto.APPLICATION,
            initialSnapshot.gameState.players[2].participantType,
        )

        assertEquals(
            OnlineParticipantTypeDto.APPLICATION,
            initialSnapshot.gameState.players[3].participantType,
        )

        assertTrue(
            initialSnapshot.automaticPlayerIndexes.isEmpty(),
        )
    }

    @Test
    fun development_bot_advances_after_human_zero_finishes_a_turn() {
        var now = 1_000L
        val store = InMemoryOnlineServerStore(
            autoFillDevelopmentBotsAfterTwoHumanPlayers = true,
            nowEpochMillis = { now },
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

        val snapshot = getSnapshotAtHumanSeat(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
            playerIndex = 0,
            advanceServerControlledClock = {
                now +=
                    AutomaticDecisionCadencePolicy
                        .MaxDecisionDelayMillis
            },
        )

        val playerZeroAction = submitCurrentHumanAction(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
            snapshot = snapshot,
        )

        assertTrue(playerZeroAction.accepted)

        now +=
            AutomaticDecisionCadencePolicy
                .MaxDecisionDelayMillis

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
    fun human_with_bot_like_player_id_is_not_controlled_by_application() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val waitingRoom = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        joinPlayer(
            store = store,
            roomCode = waitingRoom.roomCode,
            playerId = "player-2",
            playerName = "Jogador 2",
        )

        val botLikeHumanPlayerId =
            "development-bot-seat-2"

        joinPlayer(
            store = store,
            roomCode = waitingRoom.roomCode,
            playerId = botLikeHumanPlayerId,
            playerName = "Humano no assento 3",
        )

        val fullWaitingRoom = requireNotNull(
            joinPlayer(
                store = store,
                roomCode = waitingRoom.roomCode,
                playerId = "player-4",
                playerName = "Jogador 4",
            ).roomSnapshot,
        )
        val startedRoom = requireNotNull(
            store.startPrivateRoom(
                PrivateRoomStartRequestDto(
                    roomId = fullWaitingRoom.roomId,
                    localPlayerId = "player-1",
                ),
            ).roomSnapshot,
        )

        val botLikeHuman = requireNotNull(
            startedRoom.players.firstOrNull { player ->
                player.playerId == botLikeHumanPlayerId
            },
        )

        assertEquals(
            2,
            botLikeHuman.seatIndex,
        )

        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            botLikeHuman.participantType,
        )

        val matchId = requireNotNull(
            startedRoom.matchId,
        )

        repeat(80) {
            val snapshot = requireNotNull(
                store.getMatchSnapshot(
                    matchId = matchId,
                ),
            )

            val runtimeState = snapshot.toRuntimeState(
                localPlayerIndex =
                    snapshot.gameState.currentPlayerIndex,
            )

            val currentPlayerIndex =
                snapshot.gameState.currentPlayerIndex

            if (runtimeState.phase == DominoMatchPhase.RoundIntro) {
                releaseRoundIntroForTest(
                    store = store,
                    roomId = startedRoom.roomId,
                    matchId = matchId,
                )
                return@repeat
            }

            if (
                currentPlayerIndex == 2 &&
                runtimeState.phase ==
                    DominoMatchPhase.WaitingForLocalMove
            ) {
                assertEquals(
                    OnlineParticipantTypeDto.HUMAN,
                    snapshot
                        .gameState
                        .players[2]
                        .participantType,
                )

                assertTrue(
                    2 !in snapshot.automaticPlayerIndexes,
                )

                store.advanceAuthoritativeTime()

                val snapshotAfterTick = requireNotNull(
                    store.getMatchSnapshot(
                        matchId = matchId,
                    ),
                )

                assertEquals(
                    snapshot,
                    snapshotAfterTick,
                )

                return
            }

            if (
                runtimeState.phase ==
                    DominoMatchPhase.WaitingForLocalMove
            ) {
                val currentPlayerId = requireNotNull(
                    startedRoom.players.firstOrNull { player ->
                        player.seatIndex == currentPlayerIndex
                    },
                ).playerId

                val move = findBasicBotMove(
                    state = runtimeState.gameState,
                )

                val actionResult = if (move != null) {
                    store.submitAction(
                        createOnlinePlayMoveAction(
                            roomId = startedRoom.roomId,
                            matchId = matchId,
                            playerId = currentPlayerId,
                            revision = snapshot.revision,
                            move = move,
                        ),
                    )
                } else {
                    store.submitAction(
                        createOnlinePassTurnAction(
                            roomId = startedRoom.roomId,
                            matchId = matchId,
                            playerId = currentPlayerId,
                            revision = snapshot.revision,
                        ),
                    )
                }

                assertTrue(actionResult.accepted)
            } else {
                store.advanceAuthoritativeTime()
            }
        }

        error(
            "A partida nao alcancou um turno jogavel do humano com ID semelhante ao de bot.",
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
        requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        val playableSnapshot = releaseRoundIntroForTest(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
        )

        now += 31_000L

        val result = store.submitAction(
            createOnlineSnapshotRequestAction(
                roomId = startedRoom.roomId,
                matchId = matchId,
                playerId = "player-1",
                revision = playableSnapshot.revision,
            ),
        )

        val snapshotAfterRequest = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertTrue(result.accepted)
        assertEquals(playableSnapshot.revision, result.revision)
        assertEquals(playableSnapshot, snapshotAfterRequest)
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

        releaseRoundIntroForTest(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
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
    fun reconnected_human_reclaims_temporary_automatic_control() {
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
        val expiredSeatIndex =
            initialSnapshot.gameState.currentPlayerIndex
        val reconnectingPlayer = requireNotNull(
            startedRoom.players.firstOrNull { player ->
                player.seatIndex == expiredSeatIndex
            },
        )

        releaseRoundIntroForTest(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
        )

        now += 31_000L
        store.advanceAuthoritativeTime()

        val automaticSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertTrue(
            expiredSeatIndex in
                    automaticSnapshot.automaticPlayerIndexes,
        )

        val reconnectResult = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = startedRoom.roomCode,
                localPlayerId = reconnectingPlayer.playerId,
                playerName = reconnectingPlayer.name,
            ),
        )

        assertTrue(reconnectResult.accepted)

        val reconnectedRoomPlayer = requireNotNull(
            reconnectResult.roomSnapshot
                ?.players
                ?.firstOrNull { player ->
                    player.playerId == reconnectingPlayer.playerId
                },
        )
        val reclaimedSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertTrue(reconnectedRoomPlayer.connected)
        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            reconnectedRoomPlayer.participantType,
        )
        assertTrue(
            expiredSeatIndex !in
                    reclaimedSnapshot.automaticPlayerIndexes,
        )
        assertEquals(
            automaticSnapshot.revision + 1L,
            reclaimedSnapshot.revision,
        )
        assertEquals(
            automaticSnapshot.gameState,
            reclaimedSnapshot.gameState,
        )
        assertEquals(
            automaticSnapshot.serverEpochMillis,
            reclaimedSnapshot.serverEpochMillis,
        )

        val publishedReclaim = requireNotNull(
            store.getMatchSnapshotsAfter(
                matchId = matchId,
                afterRevision = automaticSnapshot.revision,
            ),
        )

        assertEquals(
            listOf(reclaimedSnapshot),
            publishedReclaim,
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

        val fullWaitingRoom = requireNotNull(
            joinPlayer(
                store = store,
                roomCode = room.roomCode,
                playerId = "player-4",
                playerName = "Jogador 4",
            ).roomSnapshot,
        )
        assertEquals(
            OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            fullWaitingRoom.status,
        )
        assertEquals(null, fullWaitingRoom.matchId)

        val startedRoom = requireNotNull(
            store.startPrivateRoom(
                PrivateRoomStartRequestDto(
                    roomId = fullWaitingRoom.roomId,
                    localPlayerId = "player-1",
                ),
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

        releaseRoundIntroForTest(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
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

        requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        val playableSnapshot = releaseRoundIntroForTest(
            store = store,
            roomId = startedRoom.roomId,
            matchId = matchId,
        )

        traceSink.clear()

        val action = createOnlineSnapshotRequestAction(
            roomId = startedRoom.roomId,
            matchId = matchId,
            playerId = "player-1",
            revision = playableSnapshot.revision,
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

    @Test
    fun default_finalized_room_retention_is_one_hour() {
        assertEquals(
            1L * 60L * 60L * 1_000L,
            OnlineServerStoreResourcePolicy.Default
                .finalizedRoomRetentionMillis,
        )
    }

    @Test
    fun finalized_room_pruning_removes_match_and_action_results_at_boundary() {
        var nowEpochMillis = 1_000L
        val store = InMemoryOnlineServerStore(
            resourcePolicy = OnlineServerStoreResourcePolicy(
                maxRoomCount = 1,
                maxActionResultCount = 8,
                finalizedRoomRetentionMillis = 1_000L,
                pruneIntervalMillis = 1L,
            ),
            nowEpochMillis = { nowEpochMillis },
        )

        val startedRoom = startFourHumanMatch(
            store = store,
        )
        val matchId = requireNotNull(
            startedRoom.matchId,
        )
        val snapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        val cachedResult = store.submitAction(
            createOnlineSnapshotRequestAction(
                roomId = startedRoom.roomId,
                matchId = matchId,
                playerId = "player-1",
                revision = snapshot.revision,
                actionId = "finalized-retention-cache",
            ),
        )

        assertTrue(cachedResult.accepted)

        val persistedState = store.snapshotPersistentState()

        assertEquals(
            1,
            persistedState.actionResults.count { storedAction ->
                storedAction.matchId == matchId
            },
        )

        store.restorePersistentState(
            persistedState.copy(
                rooms = persistedState.rooms.map { room ->
                    if (room.roomId == startedRoom.roomId) {
                        room.copy(
                            status = OnlineRoomStatusDto.FINISHED,
                            updatedAtEpochMillis = nowEpochMillis,
                        )
                    } else {
                        room
                    }
                },
            ),
        )

        nowEpochMillis += 999L

        val beforeBoundary = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-before-boundary",
                playerName = "Antes do limite",
            ),
        )

        assertTrue(!beforeBoundary.accepted)
        assertTrue(
            store.getRoomSnapshot(startedRoom.roomId) != null,
        )
        assertTrue(
            store.getMatchSnapshot(matchId) != null,
        )

        nowEpochMillis += 1L

        val replacement = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-after-boundary",
                playerName = "Apos o limite",
            ),
        )

        assertTrue(replacement.accepted)
        assertEquals(
            null,
            store.getRoomSnapshot(startedRoom.roomId),
        )
        assertEquals(
            null,
            store.getMatchSnapshot(matchId),
        )

        val stateAfterPrune = store.snapshotPersistentState()

        assertEquals(
            0,
            stateAfterPrune.actionResults.count { storedAction ->
                storedAction.matchId == matchId
            },
        )
    }
    private fun getSnapshotAtHumanSeat(
        store: InMemoryOnlineServerStore,
        roomId: String,
        matchId: String,
        playerIndex: Int,
        advanceServerControlledClock: () -> Unit = {},
    ): OnlineMatchSnapshotDto {
        repeat(80) {
            val snapshot = requireNotNull(
                store.getMatchSnapshot(
                    matchId = matchId,
                ),
            )

            val runtimeState = snapshot.toRuntimeState(
                localPlayerIndex = snapshot.gameState.currentPlayerIndex,
            )

            if (runtimeState.phase == DominoMatchPhase.RoundIntro) {
                releaseRoundIntroForTest(
                    store = store,
                    roomId = roomId,
                    matchId = matchId,
                )
                return@repeat
            }

            if (
                snapshot.gameState.currentPlayerIndex == playerIndex &&
                runtimeState.phase == DominoMatchPhase.WaitingForLocalMove
            ) {
                return snapshot
            }

            if (
                snapshot.gameState.currentPlayerIndex in 0..1 &&
                runtimeState.phase == DominoMatchPhase.WaitingForLocalMove
            ) {
                val actionResult = submitCurrentHumanAction(
                    store = store,
                    roomId = roomId,
                    matchId = matchId,
                    snapshot = snapshot,
                )

                assertTrue(actionResult.accepted)
            } else {
                advanceServerControlledClock()
                store.advanceAuthoritativeTime()
            }
        }

        error(
            "A partida não alcançou um turno jogável do humano $playerIndex.",
        )
    }

    private fun getSnapshotAtHumanTurn(
        store: InMemoryOnlineServerStore,
        matchId: String,
    ): OnlineMatchSnapshotDto {
        repeat(80) {
            val snapshot = requireNotNull(
                store.getMatchSnapshot(
                    matchId = matchId,
                ),
            )

            val runtimeState = snapshot.toRuntimeState(
                localPlayerIndex = snapshot.gameState.currentPlayerIndex,
            )

            if (
                snapshot.gameState.currentPlayerIndex in 0..1 &&
                runtimeState.phase == DominoMatchPhase.WaitingForLocalMove
            ) {
                return snapshot
            }

            store.advanceAuthoritativeTime()
        }

        error(
            "A partida não alcançou um turno humano com jogada disponível.",
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

        val fullWaitingRoom = requireNotNull(
            joinPlayer(
                store = store,
                roomCode = room.roomCode,
                playerId = "player-4",
                playerName = "Jogador 4",
            ).roomSnapshot,
        )

        return requireNotNull(
            store.startPrivateRoom(
                PrivateRoomStartRequestDto(
                    roomId = fullWaitingRoom.roomId,
                    localPlayerId = "player-1",
                ),
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
