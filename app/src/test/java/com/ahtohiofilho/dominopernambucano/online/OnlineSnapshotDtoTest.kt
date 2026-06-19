package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineSnapshotDtoTest {
    @Test
    fun room_snapshot_round_trips_through_json_contract() {
        val snapshot = OnlineRoomSnapshotDto(
            roomId = "room-1",
            roomCode = "ABC123",
            hostPlayerId = "player-1",
            status = OnlineRoomStatusDto.IN_MATCH,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = "player-1",
                    name = "Você",
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
                    connected = false,
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
            updatedAtEpochMillis = 2_000L,
        )

        val json = createOnlineJson()
        val encoded = json.encodeToString(snapshot)
        val decoded = json.decodeFromString<OnlineRoomSnapshotDto>(encoded)

        assertEquals(snapshot, decoded)
    }

    @Test
    fun room_operation_result_can_carry_snapshot_and_local_seat() {
        val roomSnapshot = OnlineRoomSnapshotDto(
            roomId = "room-1",
            roomCode = "ABC123",
            hostPlayerId = "player-1",
            status = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = "player-1",
                    name = "Você",
                    seatIndex = 0,
                    connected = true,
                ),
            ),
            matchId = null,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )

        val result = OnlineRoomOperationResultDto(
            accepted = true,
            roomSnapshot = roomSnapshot,
            localSeatIndex = 0,
            reason = null,
        )

        val json = createOnlineJson()
        val encoded = json.encodeToString(result)
        val decoded = json.decodeFromString<OnlineRoomOperationResultDto>(encoded)

        assertEquals(result, decoded)
        assertEquals(true, decoded.accepted)
        assertEquals(0, decoded.localSeatIndex)
        assertEquals(roomSnapshot, decoded.roomSnapshot)
        assertNull(decoded.reason)
    }

    @Test
    fun rejected_room_operation_result_can_carry_reason_without_snapshot() {
        val result = OnlineRoomOperationResultDto(
            accepted = false,
            roomSnapshot = null,
            localSeatIndex = null,
            reason = "Sala não encontrada.",
        )

        val json = createOnlineJson()
        val encoded = json.encodeToString(result)
        val decoded = json.decodeFromString<OnlineRoomOperationResultDto>(encoded)

        assertEquals(result, decoded)
        assertEquals(false, decoded.accepted)
        assertNull(decoded.roomSnapshot)
        assertNull(decoded.localSeatIndex)
        assertEquals("Sala não encontrada.", decoded.reason)
    }

    @Test
    fun match_snapshot_round_trips_through_json_contract() {
        val runtimeState = DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 3,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.RoundIntro,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
        )

        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = 12L,
            serverEpochMillis = 10_000L,
        )

        val json = createOnlineJson()
        val encoded = json.encodeToString(snapshot)
        val decoded = json.decodeFromString<OnlineMatchSnapshotDto>(encoded)

        assertEquals(snapshot, decoded)
        assertEquals("room-1", decoded.roomId)
        assertEquals("match-1", decoded.matchId)
        assertEquals(12L, decoded.revision)
        assertEquals(3, decoded.roundNumber)
        assertEquals(OnlineMatchClockPolicyDto.ONLINE_PER_PLAYER_ROUND, decoded.clockPolicy)
        assertEquals(10_000L, decoded.serverEpochMillis)
    }

    @Test
    fun match_snapshot_restores_runtime_state_from_contract() {
        val runtimeState = DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 2,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.WaitingForLocalMove,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
        )

        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = 5L,
            serverEpochMillis = 8_000L,
        )

        val restoredRuntimeState = snapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        assertEquals(runtimeState.gameState, restoredRuntimeState.gameState)
        assertEquals(runtimeState.roundNumber, restoredRuntimeState.roundNumber)
        assertEquals(runtimeState.localPlayerIndex, restoredRuntimeState.localPlayerIndex)
        assertEquals(runtimeState.phase, restoredRuntimeState.phase)
        assertEquals(runtimeState.clockPolicy, restoredRuntimeState.clockPolicy)
        assertEquals(runtimeState.playerClockMillis, restoredRuntimeState.playerClockMillis)
    }

    @Test
    fun presenting_move_phase_round_trips_inside_match_snapshot() {
        val move = PlayableMove(
            piece = DominoPiece(
                left = 6,
                right = 3,
            ),
            side = BoardSide.LEFT,
            flipped = true,
        )

        val runtimeState = DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.PresentingMove(
                playerIndex = 2,
                move = move,
            ),
            clockPolicy = DominoMatchClockPolicy.Disabled,
        )

        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = 9L,
            serverEpochMillis = 4_000L,
        )

        val json = createOnlineJson()
        val encoded = json.encodeToString(snapshot)
        val decoded = json.decodeFromString<OnlineMatchSnapshotDto>(encoded)

        val restoredRuntimeState = decoded.toRuntimeState(
            localPlayerIndex = 0,
        )

        assertEquals(snapshot, decoded)
        assertEquals(runtimeState.phase, restoredRuntimeState.phase)
    }

    @Test
    fun presenting_pass_phase_round_trips_inside_match_snapshot() {
        val runtimeState = DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.PresentingPass(
                playerIndex = 3,
            ),
            clockPolicy = DominoMatchClockPolicy.Disabled,
        )

        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = 10L,
            serverEpochMillis = 5_000L,
        )

        val json = createOnlineJson()
        val encoded = json.encodeToString(snapshot)
        val decoded = json.decodeFromString<OnlineMatchSnapshotDto>(encoded)

        val restoredRuntimeState = decoded.toRuntimeState(
            localPlayerIndex = 0,
        )

        assertEquals(snapshot, decoded)
        assertEquals(runtimeState.phase, restoredRuntimeState.phase)
    }
}