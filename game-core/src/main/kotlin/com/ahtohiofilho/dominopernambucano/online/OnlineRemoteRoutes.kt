package com.ahtohiofilho.dominopernambucano.online

object OnlineRemoteRoutes {
    const val CREATE_ROOM = "rooms"
    const val JOIN_ROOM = "rooms/join"
    const val SUBMIT_ACTION = "matches/actions"

    fun roomSnapshot(
        roomId: String,
    ): String {
        return "rooms/${roomId.toRoutePathSegment()}"
    }

    fun matchSnapshot(
        matchId: String,
    ): String {
        return "matches/${matchId.toRoutePathSegment()}"
    }

    fun matchSnapshotUpdates(
        matchId: String,
    ): String {
        return "matches/${matchId.toRoutePathSegment()}/updates"
    }

    private fun String.toRoutePathSegment(): String {
        return trim().trim('/')
    }
}