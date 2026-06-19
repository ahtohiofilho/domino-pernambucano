package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlayerActionDtoTest {
    @Test
    fun play_move_action_maps_metadata_and_move_payload() {
        val move = PlayableMove(
            piece = DominoPiece(
                left = 6,
                right = 4,
            ),
            side = BoardSide.RIGHT,
            flipped = true,
        )

        val action = createOnlinePlayMoveAction(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            revision = 7L,
            move = move,
            actionId = "action-1",
        )

        assertEquals("room-1", action.roomId)
        assertEquals("match-1", action.matchId)
        assertEquals("player-1", action.playerId)
        assertEquals(7L, action.revision)
        assertEquals(OnlinePlayerActionTypeDto.PLAY_MOVE, action.type)
        assertEquals("action-1", action.actionId)

        assertNotNull(action.move)

        val onlineMove = action.move
            ?: error("A ação PLAY_MOVE deveria carregar uma jogada.")

        assertEquals(6, onlineMove.piece.left)
        assertEquals(4, onlineMove.piece.right)
        assertEquals(OnlineBoardSideDto.RIGHT, onlineMove.side)
        assertEquals(true, onlineMove.flipped)
    }

    @Test
    fun pass_turn_action_does_not_attach_move_payload() {
        val action = createOnlinePassTurnAction(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            revision = 7L,
            actionId = "action-pass",
        )

        assertEquals("room-1", action.roomId)
        assertEquals("match-1", action.matchId)
        assertEquals("player-1", action.playerId)
        assertEquals(7L, action.revision)
        assertEquals(OnlinePlayerActionTypeDto.PASS_TURN, action.type)
        assertEquals("action-pass", action.actionId)
        assertNull(action.move)
    }

    @Test
    fun control_actions_do_not_attach_move_payload() {
        val actions = listOf(
            createOnlineStartNextRoundAction(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "player-1",
                revision = 7L,
                actionId = "action-next-round",
            ) to OnlinePlayerActionTypeDto.START_NEXT_ROUND,

            createOnlineStartNewMatchAction(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "player-1",
                revision = 7L,
                actionId = "action-new-match",
            ) to OnlinePlayerActionTypeDto.START_NEW_MATCH,

            createOnlineSnapshotRequestAction(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "player-1",
                revision = 7L,
                actionId = "action-snapshot",
            ) to OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT,

            createOnlineLeaveRoomAction(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "player-1",
                revision = 7L,
                actionId = "action-leave",
            ) to OnlinePlayerActionTypeDto.LEAVE_ROOM,
        )

        actions.forEach { (action, expectedType) ->
            assertEquals("room-1", action.roomId)
            assertEquals("match-1", action.matchId)
            assertEquals("player-1", action.playerId)
            assertEquals(7L, action.revision)
            assertEquals(expectedType, action.type)
            assertNull(action.move)
        }
    }

    @Test
    fun generated_action_ids_are_not_blank_and_are_unique() {
        val firstAction = createOnlinePassTurnAction(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            revision = 1L,
        )

        val secondAction = createOnlinePassTurnAction(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            revision = 1L,
        )

        assertTrue(firstAction.actionId.isNotBlank())
        assertTrue(secondAction.actionId.isNotBlank())
        assertNotEquals(firstAction.actionId, secondAction.actionId)
    }

    @Test
    fun action_result_can_carry_action_id_for_idempotency() {
        val result = OnlineActionResultDto(
            accepted = true,
            revision = 2L,
            actionId = "action-1",
            reason = null,
        )

        assertEquals(true, result.accepted)
        assertEquals(2L, result.revision)
        assertEquals("action-1", result.actionId)
        assertNull(result.reason)
    }

    @Test
    fun action_dto_round_trips_through_json_contract() {
        val action = createOnlinePlayMoveAction(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            revision = 7L,
            move = PlayableMove(
                piece = DominoPiece(
                    left = 5,
                    right = 3,
                ),
                side = BoardSide.LEFT,
                flipped = false,
            ),
            actionId = "action-json",
        )

        val json = createOnlineJson()
        val encoded = json.encodeToString(action)
        val decoded = json.decodeFromString<OnlinePlayerActionDto>(encoded)

        assertEquals(action, decoded)
    }
}