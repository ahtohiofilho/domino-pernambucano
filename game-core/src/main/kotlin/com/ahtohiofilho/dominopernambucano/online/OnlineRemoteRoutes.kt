package com.ahtohiofilho.dominopernambucano.online

object OnlineRemoteRoutes {
    const val CREATE_ANONYMOUS_SESSION = "sessions/anonymous"
    const val PROMOTE_ACCOUNT = "accounts/promote"
    const val RECOVER_SYNTHETIC_ACCOUNT =
        "accounts/synthetic/recover"
    const val LINK_GOOGLE_IDENTITY = "accounts/identities/google/link"
    const val RECOVER_GOOGLE_ACCOUNT = "accounts/identities/google/recover"
    const val RANKED_QUEUE = "ranked-queue"
    const val RANKING = "ranking"
    const val RANKING_CYCLES = "ranking/cycles"
    const val RANKING_ACHIEVEMENTS = "ranking/achievements"
    const val ACCOUNT_PROFILE = "accounts/profile"
    const val DELETE_ACCOUNT = "accounts/self"
    const val CREATE_ROOM = "rooms"
    const val JOIN_ROOM = "rooms/join"
    const val PRIVATE_ROOM_SEAT = "rooms/private/seat"
    const val PRIVATE_ROOM_COMPLETE = "rooms/private/complete"
    const val PRIVATE_ROOM_REMOVE_AUTOMATIC =
        "rooms/private/remove-automatic"
    const val PRIVATE_ROOM_START = "rooms/private/start"
    const val PRIVATE_ROOM_LEAVE = "rooms/private/leave"
    const val SUBMIT_ACTION = "matches/actions"
    const val SUBMIT_TRACE_BATCH = "traces"

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
