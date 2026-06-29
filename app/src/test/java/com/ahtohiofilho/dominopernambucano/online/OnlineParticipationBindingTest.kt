package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineParticipationBindingTest {
    @Test
    fun remote_backend_scope_normalizes_trailing_slash() {
        val scope = OnlineBackendConfig.remote(
            baseUrl = " http://192.168.0.3:8080/ ",
        ).toOnlineParticipationBackendScope()

        assertEquals(
            "remote:http://192.168.0.3:8080",
            scope,
        )
    }

    @Test
    fun create_binding_preserves_authoritative_room_match_and_seat() {
        val room = OnlineRoomSnapshotDto(
            roomId = " room-1 ",
            roomCode = "1234",
            hostPlayerId = "player-1",
            status = OnlineRoomStatusDto.IN_MATCH,
            players = emptyList(),
            matchId = " match-1 ",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val binding = createOnlineParticipationBinding(
            backendScope = " remote:http://localhost:8080 ",
            roomSnapshot = room,
            playerId = " player-1 ",
            seatIndex = 2,
        )

        assertEquals(
            OnlineParticipationBinding(
                backendScope = "remote:http://localhost:8080",
                roomId = "room-1",
                matchId = "match-1",
                playerId = "player-1",
                seatIndex = 2,
            ),
            binding,
        )
    }

    @Test
    fun in_memory_store_discards_invalid_binding() {
        val store = InMemoryOnlineParticipationStore()

        store.write(
            OnlineParticipationBinding(
                backendScope = "remote:http://localhost:8080",
                roomId = "room-1",
                matchId = null,
                playerId = "player-1",
                seatIndex = 9,
            ),
        )

        assertNull(store.read())
    }
}
