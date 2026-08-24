package com.ahtohiofilho.dominopernambucano.ui.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateRoomLobbyUiPolicyTest {
    @Test
    fun full_connected_host_can_start_but_guest_cannot() {
        val room = waitingRoom(
            players = listOf(
                player("host", 0),
                player("p2", 1),
                player("p3", 2),
                player("p4", 3),
            ),
        )

        assertTrue(
            canStartPrivateRoomFromLobby(
                roomSnapshot = room,
                localPlayerId = "host",
            ),
        )
        assertFalse(
            canStartPrivateRoomFromLobby(
                roomSnapshot = room,
                localPlayerId = "p2",
            ),
        )
    }

    @Test
    fun full_room_readiness_requires_waiting_four_connected_valid_seats() {
        val fullRoom = waitingRoom(
            players = listOf(
                player("host", 0),
                player("p2", 1),
                player("p3", 2),
                player("p4", 3),
            ),
        )
        assertTrue(isPrivateRoomFullAndReady(fullRoom))

        val onlyThree = waitingRoom(
            players = fullRoom.players.take(3),
        )
        assertFalse(isPrivateRoomFullAndReady(onlyThree))

        val disconnected = fullRoom.copy(
            players = fullRoom.players.map { player ->
                if (player.playerId == "p4") {
                    player.copy(connected = false)
                } else {
                    player
                }
            },
        )
        assertFalse(isPrivateRoomFullAndReady(disconnected))

        val inMatch = fullRoom.copy(
            status = OnlineRoomStatusDto.IN_MATCH,
            matchId = "match-1",
        )
        assertFalse(isPrivateRoomFullAndReady(inMatch))
    }

    @Test
    fun host_cannot_start_until_four_connected_valid_seats_exist() {
        val onlyThree = waitingRoom(
            players = listOf(
                player("host", 0),
                player("p2", 1),
                player("p3", 2),
            ),
        )
        assertFalse(
            canStartPrivateRoomFromLobby(
                roomSnapshot = onlyThree,
                localPlayerId = "host",
            ),
        )

        val disconnected = waitingRoom(
            players = listOf(
                player("host", 0),
                player("p2", 1),
                player("p3", 2),
                player("p4", 3, connected = false),
            ),
        )
        assertFalse(
            canStartPrivateRoomFromLobby(
                roomSnapshot = disconnected,
                localPlayerId = "host",
            ),
        )
    }

    @Test
    fun seat_actions_distinguish_current_empty_and_occupied_destinations() {
        val room = waitingRoom(
            players = listOf(
                player("host", 0),
                player("p2", 2),
            ),
        )

        assertEquals(
            PrivateRoomSeatUiAction.CURRENT,
            privateRoomSeatUiAction(room, "host", 0),
        )
        assertEquals(
            PrivateRoomSeatUiAction.CHOOSE,
            privateRoomSeatUiAction(room, "host", 1),
        )
        assertEquals(
            PrivateRoomSeatUiAction.SWAP,
            privateRoomSeatUiAction(room, "host", 2),
        )
    }

    @Test
    fun seat_actions_are_disabled_after_match_start() {
        val room = waitingRoom(
            players = listOf(
                player("host", 0),
                player("p2", 1),
                player("p3", 2),
                player("p4", 3),
            ),
        ).copy(
            status = OnlineRoomStatusDto.IN_MATCH,
            matchId = "match-1",
        )

        assertEquals(
            PrivateRoomSeatUiAction.NONE,
            privateRoomSeatUiAction(room, "host", 1),
        )
        assertFalse(
            canStartPrivateRoomFromLobby(room, "host"),
        )
    }

    private fun waitingRoom(
        players: List<OnlineRoomPlayerDto>,
    ) = OnlineRoomSnapshotDto(
        roomId = "room-1",
        roomCode = "0001",
        hostPlayerId = "host",
        status = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
        players = players,
        matchMode = DominoMatchMode.PRIVATE_UNRANKED,
    )

    private fun player(
        playerId: String,
        seatIndex: Int,
        connected: Boolean = true,
    ) = OnlineRoomPlayerDto(
        playerId = playerId,
        name = playerId,
        seatIndex = seatIndex,
        connected = connected,
    )
}
