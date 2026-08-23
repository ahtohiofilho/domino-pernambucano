package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomSeatChangeRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateRoomLobbyAuthorityTest {
    @Test
    fun fourth_join_waits_swap_is_atomic_and_only_host_starts() {
        var now = 1_000L
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now++ },
        )

        val created = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "P1",
            ),
        )
        assertTrue(created.accepted)
        val room = requireNotNull(created.roomSnapshot)

        (2..4).forEach { number ->
            val joined = store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "player-$number",
                    playerName = "P$number",
                ),
            )
            assertTrue(joined.accepted)
        }

        val waiting = requireNotNull(
            store.getRoomSnapshot(room.roomId),
        )
        assertEquals(
            OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            waiting.status,
        )
        assertEquals(4, waiting.players.size)
        assertNull(waiting.matchId)

        val seat0Before = waiting.players.single { player ->
            player.seatIndex == 0
        }.playerId
        val seat2Before = waiting.players.single { player ->
            player.seatIndex == 2
        }.playerId
        assertEquals("player-1", seat0Before)

        val moved = store.movePrivateRoomSeat(
            PrivateRoomSeatChangeRequestDto(
                roomId = room.roomId,
                localPlayerId = "player-1",
                targetSeatIndex = 2,
            ),
        )
        assertTrue(moved.accepted)
        val movedRoom = requireNotNull(moved.roomSnapshot)
        assertEquals(
            2,
            movedRoom.players.single { player ->
                player.playerId == "player-1"
            }.seatIndex,
        )
        assertEquals(
            0,
            movedRoom.players.single { player ->
                player.playerId == seat2Before
            }.seatIndex,
        )
        assertEquals(
            listOf(0, 1, 2, 3),
            movedRoom.players.mapNotNull { player ->
                player.seatIndex
            }.sorted(),
        )

        val nonHostStart = store.startPrivateRoom(
            PrivateRoomStartRequestDto(
                roomId = room.roomId,
                localPlayerId = "player-2",
            ),
        )
        assertFalse(nonHostStart.accepted)
        assertNull(
            store.getRoomSnapshot(room.roomId)?.matchId,
        )

        val started = store.startPrivateRoom(
            PrivateRoomStartRequestDto(
                roomId = room.roomId,
                localPlayerId = "player-1",
            ),
        )
        assertTrue(started.accepted)
        val startedRoom = requireNotNull(started.roomSnapshot)
        assertEquals(
            OnlineRoomStatusDto.IN_MATCH,
            startedRoom.status,
        )
        val matchId = startedRoom.matchId
        assertNotNull(matchId)
        assertNotNull(
            store.getMatchSnapshot(requireNotNull(matchId)),
        )

        val moveAfterStart = store.movePrivateRoomSeat(
            PrivateRoomSeatChangeRequestDto(
                roomId = room.roomId,
                localPlayerId = "player-1",
                targetSeatIndex = 1,
            ),
        )
        assertFalse(moveAfterStart.accepted)
    }

    @Test
    fun host_cannot_start_before_four_connected_participants() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 2_000L },
        )
        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "host",
                    playerName = "Host",
                ),
            ).roomSnapshot,
        )

        (2..3).forEach { number ->
            assertTrue(
                store.joinRoom(
                    JoinOnlineRoomRequestDto(
                        roomCode = room.roomCode,
                        localPlayerId = "player-$number",
                        playerName = "P$number",
                    ),
                ).accepted,
            )
        }

        val result = store.startPrivateRoom(
            PrivateRoomStartRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
            ),
        )

        assertFalse(result.accepted)
        assertEquals(
            OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            store.getRoomSnapshot(room.roomId)?.status,
        )
        assertNull(store.getRoomSnapshot(room.roomId)?.matchId)
    }

    @Test
    fun invalid_target_seat_is_rejected_without_mutation() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 3_000L },
        )
        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "host",
                    playerName = "Host",
                ),
            ).roomSnapshot,
        )

        val before = store.getRoomSnapshot(room.roomId)
        val result = store.movePrivateRoomSeat(
            PrivateRoomSeatChangeRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
                targetSeatIndex = 9,
            ),
        )

        assertFalse(result.accepted)
        assertEquals(before, store.getRoomSnapshot(room.roomId))
    }
}