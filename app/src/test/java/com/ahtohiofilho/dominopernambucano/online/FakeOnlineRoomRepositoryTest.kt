package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOnlineRoomRepositoryTest {
    @Test
    fun create_room_starts_waiting_for_players() = runBlocking {
        val repository = FakeOnlineRoomRepository(
            nowEpochMillis = { 1_000L },
        )

        val result = repository.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "Jogador 1",
            )
        )

        assertTrue(result.accepted)
        assertEquals(0, result.localSeatIndex)

        val roomSnapshot = repository.roomSnapshot.value

        assertNotNull(roomSnapshot)
        assertEquals(
            OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            roomSnapshot?.status,
        )
        assertEquals(1, roomSnapshot?.players?.size)
        assertEquals(null, repository.matchSnapshot.value)
    }

    @Test
    fun fourth_player_join_starts_match() = runBlocking {
        val repository = createFilledRoomRepository()

        val roomSnapshot = repository.roomSnapshot.value
        val matchSnapshot = repository.matchSnapshot.value

        assertNotNull(roomSnapshot)
        assertNotNull(matchSnapshot)

        assertEquals(
            OnlineRoomStatusDto.IN_MATCH,
            roomSnapshot?.status,
        )
        assertEquals(4, roomSnapshot?.players?.size)
        assertEquals(roomSnapshot?.matchId, matchSnapshot?.matchId)
        assertEquals(1L, matchSnapshot?.revision)
    }

    @Test
    fun valid_move_is_accepted_and_advances_revision() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)

        val currentRoomPlayer = findCurrentRoomPlayer(
            room = room,
            snapshot = snapshot,
        )

        val move = findFirstPlayableMove(
            snapshot = snapshot,
        )

        val result = repository.submitAction(
            createOnlinePlayMoveAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = currentRoomPlayer.playerId,
                revision = snapshot.revision,
                move = move,
            )
        )

        assertTrue(result.accepted)
        assertEquals(2L, result.revision)
        assertEquals(2L, repository.matchSnapshot.value?.revision)
    }

    @Test
    fun duplicated_action_id_returns_cached_result_without_advancing_revision_again() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)

        val currentRoomPlayer = findCurrentRoomPlayer(
            room = room,
            snapshot = snapshot,
        )

        val move = findFirstPlayableMove(
            snapshot = snapshot,
        )

        val action = createOnlinePlayMoveAction(
            roomId = snapshot.roomId,
            matchId = snapshot.matchId,
            playerId = currentRoomPlayer.playerId,
            revision = snapshot.revision,
            move = move,
            actionId = "test-action-1",
        )

        val firstResult = repository.submitAction(action)
        val snapshotAfterFirstSubmit = repository.matchSnapshot.value

        val secondResult = repository.submitAction(action)
        val snapshotAfterSecondSubmit = repository.matchSnapshot.value

        assertTrue(firstResult.accepted)
        assertTrue(secondResult.accepted)

        assertEquals("test-action-1", firstResult.actionId)
        assertEquals(firstResult, secondResult)

        assertEquals(2L, firstResult.revision)
        assertEquals(2L, snapshotAfterFirstSubmit?.revision)
        assertEquals(snapshotAfterFirstSubmit, snapshotAfterSecondSubmit)
    }

    @Test
    fun stale_revision_is_rejected() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)

        val currentRoomPlayer = findCurrentRoomPlayer(
            room = room,
            snapshot = snapshot,
        )

        val move = findFirstPlayableMove(
            snapshot = snapshot,
        )

        val result = repository.submitAction(
            createOnlinePlayMoveAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = currentRoomPlayer.playerId,
                revision = snapshot.revision - 1L,
                move = move,
            )
        )

        assertFalse(result.accepted)
        assertEquals(snapshot.revision, repository.matchSnapshot.value?.revision)
    }

    @Test
    fun out_of_turn_player_is_rejected() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)

        val runtimeState = snapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex
        val outOfTurnPlayer = requireNotNull(
            room.players.firstOrNull { player ->
                player.seatIndex != currentPlayerIndex
            }
        )

        val move = findFirstPlayableMove(
            snapshot = snapshot,
        )

        val result = repository.submitAction(
            createOnlinePlayMoveAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = outOfTurnPlayer.playerId,
                revision = snapshot.revision,
                move = move,
            )
        )

        assertFalse(result.accepted)
        assertEquals(snapshot.revision, repository.matchSnapshot.value?.revision)
    }

    @Test
    fun disconnected_player_is_rejected() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)

        val currentRoomPlayer = findCurrentRoomPlayer(
            room = room,
            snapshot = snapshot,
        )

        val leaveResult = repository.submitAction(
            createOnlineLeaveRoomAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = currentRoomPlayer.playerId,
                revision = snapshot.revision,
                actionId = "leave-current-player",
            )
        )

        assertTrue(leaveResult.accepted)

        val move = findFirstPlayableMove(
            snapshot = snapshot,
        )

        val playResult = repository.submitAction(
            createOnlinePlayMoveAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = currentRoomPlayer.playerId,
                revision = snapshot.revision,
                move = move,
                actionId = "disconnected-player-move",
            )
        )

        assertFalse(playResult.accepted)
        assertEquals(snapshot.revision, repository.matchSnapshot.value?.revision)
    }

    @Test
    fun snapshot_request_for_human_turn_does_not_advance_revision() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)

        val currentRoomPlayer = findCurrentRoomPlayer(
            room = room,
            snapshot = snapshot,
        )

        val result = repository.submitAction(
            createOnlineSnapshotRequestAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = currentRoomPlayer.playerId,
                revision = snapshot.revision,
                actionId = "snapshot-request-human-turn",
            )
        )

        assertTrue(result.accepted)
        assertEquals(snapshot.revision, result.revision)
        assertEquals(snapshot.revision, repository.matchSnapshot.value?.revision)
        assertEquals(snapshot, repository.matchSnapshot.value)
    }

    @Test
    fun start_new_match_before_game_finished_is_rejected() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)

        val currentRoomPlayer = findCurrentRoomPlayer(
            room = room,
            snapshot = snapshot,
        )

        val result = repository.submitAction(
            createOnlineStartNewMatchAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = currentRoomPlayer.playerId,
                revision = snapshot.revision,
                actionId = "start-new-match-too-early",
            )
        )

        assertFalse(result.accepted)
        assertEquals(snapshot.revision, result.revision)
        assertEquals(snapshot.revision, repository.matchSnapshot.value?.revision)
        assertEquals(snapshot, repository.matchSnapshot.value)
    }

    @Test
    fun start_next_round_before_round_finished_is_rejected_and_preserves_snapshot() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)
        val player = findAnyConnectedRoomPlayer(room)

        val result = repository.submitAction(
            createOnlineStartNextRoundAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = player.playerId,
                revision = snapshot.revision,
                actionId = "start-next-round-too-early",
            )
        )

        assertFalse(result.accepted)
        assertEquals("A rodada ainda não terminou.", result.reason)
        assertEquals(snapshot.revision, result.revision)
        assertEquals(snapshot, repository.matchSnapshot.value)
    }

    @Test
    fun rejected_action_with_same_action_id_returns_cached_result() = runBlocking {
        val repository = createFilledRoomRepository()
        val snapshot = requireNotNull(repository.matchSnapshot.value)
        val room = requireNotNull(repository.roomSnapshot.value)
        val player = findAnyConnectedRoomPlayer(room)

        val action = createOnlineStartNextRoundAction(
            roomId = snapshot.roomId,
            matchId = snapshot.matchId,
            playerId = player.playerId,
            revision = snapshot.revision,
            actionId = "duplicated-rejected-action",
        )

        val firstResult = repository.submitAction(action)
        val snapshotAfterFirstSubmit = repository.matchSnapshot.value

        val secondResult = repository.submitAction(action)
        val snapshotAfterSecondSubmit = repository.matchSnapshot.value

        assertFalse(firstResult.accepted)
        assertFalse(secondResult.accepted)

        assertEquals("duplicated-rejected-action", firstResult.actionId)
        assertEquals(firstResult, secondResult)

        assertEquals(snapshot, snapshotAfterFirstSubmit)
        assertEquals(snapshotAfterFirstSubmit, snapshotAfterSecondSubmit)
    }

    @Test
    fun play_move_after_round_finished_is_rejected_and_preserves_snapshot() = runBlocking {
        val repository = createFilledRoomRepository()
        val initialSnapshot = requireNotNull(repository.matchSnapshot.value)
        val initialMove = findFirstPlayableMove(
            snapshot = initialSnapshot,
        )

        val finishedRoundSnapshot = playUntilRoundFinished(
            repository = repository,
            actionIdPrefix = "play-after-round-finished-setup",
        )

        val room = requireNotNull(repository.roomSnapshot.value)
        val player = findAnyConnectedRoomPlayer(room)

        val result = repository.submitAction(
            createOnlinePlayMoveAction(
                roomId = finishedRoundSnapshot.roomId,
                matchId = finishedRoundSnapshot.matchId,
                playerId = player.playerId,
                revision = finishedRoundSnapshot.revision,
                move = initialMove,
                actionId = "play-after-round-finished",
            )
        )

        assertFalse(result.accepted)
        assertEquals("A rodada já terminou.", result.reason)
        assertEquals(finishedRoundSnapshot.revision, result.revision)
        assertEquals(finishedRoundSnapshot, repository.matchSnapshot.value)
    }

    @Test
    fun pass_turn_after_round_finished_is_rejected_and_preserves_snapshot() = runBlocking {
        val repository = createFilledRoomRepository()

        val finishedRoundSnapshot = playUntilRoundFinished(
            repository = repository,
            actionIdPrefix = "pass-after-round-finished-setup",
        )

        val room = requireNotNull(repository.roomSnapshot.value)
        val player = findAnyConnectedRoomPlayer(room)

        val result = repository.submitAction(
            createOnlinePassTurnAction(
                roomId = finishedRoundSnapshot.roomId,
                matchId = finishedRoundSnapshot.matchId,
                playerId = player.playerId,
                revision = finishedRoundSnapshot.revision,
                actionId = "pass-after-round-finished",
            )
        )

        assertFalse(result.accepted)
        assertEquals("A rodada já terminou.", result.reason)
        assertEquals(finishedRoundSnapshot.revision, result.revision)
        assertEquals(finishedRoundSnapshot, repository.matchSnapshot.value)
    }

    @Test
    fun start_next_round_after_game_finished_is_rejected_and_preserves_snapshot() = runBlocking {
        val repository = createFilledRoomRepository()

        val finishedGameSnapshot = playUntilGameFinished(
            repository = repository,
        )

        val room = requireNotNull(repository.roomSnapshot.value)
        val player = findAnyConnectedRoomPlayer(room)

        val result = repository.submitAction(
            createOnlineStartNextRoundAction(
                roomId = finishedGameSnapshot.roomId,
                matchId = finishedGameSnapshot.matchId,
                playerId = player.playerId,
                revision = finishedGameSnapshot.revision,
                actionId = "start-next-round-after-game-finished",
            )
        )

        assertFalse(result.accepted)
        assertEquals("A partida já terminou.", result.reason)
        assertEquals(finishedGameSnapshot.revision, result.revision)
        assertEquals(finishedGameSnapshot, repository.matchSnapshot.value)
    }

    private suspend fun createFilledRoomRepository(): FakeOnlineRoomRepository {
        val repository = FakeOnlineRoomRepository(
            nowEpochMillis = { 1_000L },
        )

        val createResult = repository.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "Jogador 1",
            )
        )

        val roomCode = requireNotNull(createResult.roomSnapshot?.roomCode)

        repository.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = roomCode,
                localPlayerId = "player-2",
                playerName = "Jogador 2",
            )
        )

        repository.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = roomCode,
                localPlayerId = "player-3",
                playerName = "Jogador 3",
            )
        )

        repository.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = roomCode,
                localPlayerId = "player-4",
                playerName = "Jogador 4",
            )
        )

        return repository
    }

    private fun findCurrentRoomPlayer(
        room: OnlineRoomSnapshotDto,
        snapshot: OnlineMatchSnapshotDto,
    ): OnlineRoomPlayerDto {
        val runtimeState = snapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        return requireNotNull(
            room.players.firstOrNull { player ->
                player.seatIndex == currentPlayerIndex
            }
        )
    }

    private fun findAnyConnectedRoomPlayer(
        room: OnlineRoomSnapshotDto,
    ): OnlineRoomPlayerDto {
        return requireNotNull(
            room.players.firstOrNull { player ->
                player.connected && player.seatIndex != null
            }
        )
    }

    private fun findFirstPlayableMove(
        snapshot: OnlineMatchSnapshotDto,
    ): PlayableMove {
        return requireNotNull(
            findFirstPlayableMoveOrNull(
                snapshot = snapshot,
            )
        )
    }

    private fun findFirstPlayableMoveOrNull(
        snapshot: OnlineMatchSnapshotDto,
    ): PlayableMove? {
        val runtimeState = snapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        val gameState = runtimeState.gameState
        val currentPlayer = gameState.players[gameState.currentPlayerIndex]

        return currentPlayer.hand.firstNotNullOfOrNull { piece ->
            getPlayableMoves(
                board = gameState.board,
                piece = piece,
                openingPiece = gameState.openingPiece,
            ).firstOrNull()
        }
    }

    private suspend fun playUntilRoundFinished(
        repository: FakeOnlineRoomRepository,
        actionIdPrefix: String = "round",
        maxActions: Int = 120,
    ): OnlineMatchSnapshotDto {
        repeat(maxActions) { actionIndex ->
            val snapshot = requireNotNull(repository.matchSnapshot.value)
            val runtimeState = snapshot.toRuntimeState(
                localPlayerIndex = 0,
            )

            if (isRoundFinished(runtimeState.gameState)) {
                return snapshot
            }

            submitCurrentTurnAction(
                repository = repository,
                snapshot = snapshot,
                actionId = "$actionIdPrefix-action-$actionIndex",
            )
        }

        error("A rodada não terminou após $maxActions ações.")
    }

    private suspend fun playUntilGameFinished(
        repository: FakeOnlineRoomRepository,
        maxRounds: Int = 40,
    ): OnlineMatchSnapshotDto {
        repeat(maxRounds) { roundIndex ->
            val roundSnapshot = playUntilRoundFinished(
                repository = repository,
                actionIdPrefix = "game-round-$roundIndex",
            )

            val runtimeState = roundSnapshot.toRuntimeState(
                localPlayerIndex = 0,
            )

            if (isGameFinished(runtimeState.gameState)) {
                return roundSnapshot
            }

            val room = requireNotNull(repository.roomSnapshot.value)
            val player = findAnyConnectedRoomPlayer(room)

            val startNextRoundResult = repository.submitAction(
                createOnlineStartNextRoundAction(
                    roomId = roundSnapshot.roomId,
                    matchId = roundSnapshot.matchId,
                    playerId = player.playerId,
                    revision = roundSnapshot.revision,
                    actionId = "game-start-next-round-$roundIndex",
                )
            )

            assertTrue(
                "Falha ao iniciar próxima rodada: ${startNextRoundResult.reason}",
                startNextRoundResult.accepted,
            )
        }

        error("A partida não terminou após $maxRounds rodadas.")
    }

    private suspend fun submitCurrentTurnAction(
        repository: FakeOnlineRoomRepository,
        snapshot: OnlineMatchSnapshotDto,
        actionId: String,
    ) {
        val room = requireNotNull(repository.roomSnapshot.value)
        val currentRoomPlayer = findCurrentRoomPlayer(
            room = room,
            snapshot = snapshot,
        )

        val move = findFirstPlayableMoveOrNull(
            snapshot = snapshot,
        )

        val result = if (move != null) {
            repository.submitAction(
                createOnlinePlayMoveAction(
                    roomId = snapshot.roomId,
                    matchId = snapshot.matchId,
                    playerId = currentRoomPlayer.playerId,
                    revision = snapshot.revision,
                    move = move,
                    actionId = actionId,
                )
            )
        } else {
            repository.submitAction(
                createOnlinePassTurnAction(
                    roomId = snapshot.roomId,
                    matchId = snapshot.matchId,
                    playerId = currentRoomPlayer.playerId,
                    revision = snapshot.revision,
                    actionId = actionId,
                )
            )
        }

        assertTrue(
            "Ação de avanço recusada: ${result.reason}",
            result.accepted,
        )
    }
}