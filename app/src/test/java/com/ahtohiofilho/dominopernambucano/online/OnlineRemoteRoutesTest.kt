package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRemoteRoutesTest {
    @Test
    fun static_routes_match_backend_contract() {
        assertEquals(
            "rooms",
            OnlineRemoteRoutes.CREATE_ROOM,
        )

        assertEquals(
            "rooms/join",
            OnlineRemoteRoutes.JOIN_ROOM,
        )

        assertEquals(
            "matches/actions",
            OnlineRemoteRoutes.SUBMIT_ACTION,
        )
    }

    @Test
    fun snapshot_routes_are_created_from_identifiers() {
        assertEquals(
            "rooms/room-1",
            OnlineRemoteRoutes.roomSnapshot("room-1"),
        )

        assertEquals(
            "matches/match-1",
            OnlineRemoteRoutes.matchSnapshot("match-1"),
        )
    }

    @Test
    fun snapshot_routes_trim_accidental_slashes() {
        assertEquals(
            "rooms/room-1",
            OnlineRemoteRoutes.roomSnapshot("/room-1/"),
        )

        assertEquals(
            "matches/match-1",
            OnlineRemoteRoutes.matchSnapshot("/match-1/"),
        )
    }
}