package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomLeaveRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateRoomLobbyLeaveAuthorityTest {
    @Test
    fun non_host_leave_removes_only_requesting_participant_and_frees_seat() {
        val store = InMemoryOnlineServerStore()
        val created = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "host",
                playerName = "Host",
            ),
        )
        val room = requireNotNull(created.roomSnapshot)

        store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "guest-1",
                playerName = "Guest 1",
            ),
        )

        val leave = store.leavePrivateRoom(
            PrivateRoomLeaveRequestDto(
                roomId = room.roomId,
                localPlayerId = "guest-1",
            ),
        )

        assertTrue(leave.accepted)
        assertEquals(
            listOf("host"),
            requireNotNull(leave.roomSnapshot).players.map { it.playerId },
        )

        val replacement = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "guest-2",
                playerName = "Guest 2",
            ),
        )

        assertTrue(replacement.accepted)
        assertEquals(1, replacement.localSeatIndex)
    }

    @Test
    fun host_leave_closes_lobby_without_transferring_host_and_is_idempotent() {
        val store = InMemoryOnlineServerStore()
        val created = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "host",
                playerName = "Host",
            ),
        )
        val room = requireNotNull(created.roomSnapshot)

        store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "guest",
                playerName = "Guest",
            ),
        )

        val leave = store.leavePrivateRoom(
            PrivateRoomLeaveRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
            ),
        )

        assertTrue(leave.accepted)
        assertNull(leave.roomSnapshot)
        assertNull(store.getRoomSnapshot(room.roomId))

        val joinClosed = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "late",
                playerName = "Late",
            ),
        )
        assertFalse(joinClosed.accepted)

        val retry = store.leavePrivateRoom(
            PrivateRoomLeaveRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
            ),
        )
        assertTrue(retry.accepted)
    }

    @Test
    fun nonparticipant_idempotent_leave_does_not_disclose_or_change_room() {
        val store = InMemoryOnlineServerStore()
        val created = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "host",
                playerName = "Host",
            ),
        )
        val room = requireNotNull(created.roomSnapshot)

        store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "guest",
                playerName = "Guest",
            ),
        )
        val before = requireNotNull(
            store.getRoomSnapshot(room.roomId),
        )

        val outsiderLeave = store.leavePrivateRoom(
            PrivateRoomLeaveRequestDto(
                roomId = room.roomId,
                localPlayerId = "outsider",
            ),
        )

        assertTrue(outsiderLeave.accepted)
        assertNull(outsiderLeave.roomSnapshot)
        assertNull(outsiderLeave.localSeatIndex)
        assertEquals(
            before,
            store.getRoomSnapshot(room.roomId),
        )
    }

    @Test
    fun private_lobby_leave_route_is_rejected_after_match_start() {
        val store = InMemoryOnlineServerStore()
        val created = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "host",
                playerName = "Host",
            ),
        )
        val room = requireNotNull(created.roomSnapshot)

        (1..3).forEach { index ->
            store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "guest-$index",
                    playerName = "Guest $index",
                ),
            )
        }

        val started = store.startPrivateRoom(
            PrivateRoomStartRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
            ),
        )
        assertTrue(started.accepted)

        val leaveAfterStart = store.leavePrivateRoom(
            PrivateRoomLeaveRequestDto(
                roomId = room.roomId,
                localPlayerId = "guest-1",
            ),
        )
        assertFalse(leaveAfterStart.accepted)
    }
}