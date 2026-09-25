package com.ahtohiofilho.dominopernambucano.ui.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateRoomAutomaticCompletionUiPolicyTest {
    @Test
    fun only_host_sees_completion_while_empty_seats_exist() {
        val room = room(
            players = listOf(
                human("host", 0),
                human("guest", 1),
            ),
        )

        assertTrue(
            canCompletePrivateRoomWithAutomaticPlayers(
                roomSnapshot = room,
                localPlayerId = "host",
            ),
        )
        assertFalse(
            canCompletePrivateRoomWithAutomaticPlayers(
                roomSnapshot = room,
                localPlayerId = "guest",
            ),
        )
        assertFalse(
            canCompletePrivateRoomWithAutomaticPlayers(
                roomSnapshot = room.copy(
                    players = listOf(
                        human("host", 0),
                        human("guest", 1),
                        automatic("auto-2", 2),
                        automatic("auto-3", 3),
                    ),
                ),
                localPlayerId = "host",
            ),
        )
    }

    @Test
    fun host_tapping_automatic_seat_releases_it_but_guest_can_swap() {
        val room = room(
            players = listOf(
                human("host", 0),
                human("guest", 1),
                automatic("auto-2", 2),
            ),
        )

        assertEquals(
            PrivateRoomSeatUiAction.RELEASE_AUTOMATIC,
            privateRoomSeatUiAction(
                roomSnapshot = room,
                localPlayerId = "host",
                targetSeatIndex = 2,
            ),
        )
        assertEquals(
            PrivateRoomSeatUiAction.SWAP,
            privateRoomSeatUiAction(
                roomSnapshot = room,
                localPlayerId = "guest",
                targetSeatIndex = 2,
            ),
        )
    }

    private fun room(
        players: List<OnlineRoomPlayerDto>,
    ) = OnlineRoomSnapshotDto(
        roomId = "room-1",
        roomCode = "0343",
        hostPlayerId = "host",
        status = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
        players = players,
        matchMode = DominoMatchMode.PRIVATE_UNRANKED,
    )

    private fun human(
        playerId: String,
        seatIndex: Int,
    ) = OnlineRoomPlayerDto(
        playerId = playerId,
        name = playerId,
        seatIndex = seatIndex,
        connected = true,
        participantType = OnlineParticipantTypeDto.HUMAN,
    )

    private fun automatic(
        playerId: String,
        seatIndex: Int,
    ) = OnlineRoomPlayerDto(
        playerId = playerId,
        name = playerId,
        seatIndex = seatIndex,
        connected = true,
        participantType =
            OnlineParticipantTypeDto.APPLICATION,
    )
}