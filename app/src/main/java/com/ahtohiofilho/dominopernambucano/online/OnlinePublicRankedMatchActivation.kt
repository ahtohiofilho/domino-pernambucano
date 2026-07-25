package com.ahtohiofilho.dominopernambucano.online

enum class OnlinePublicRankedMatchActivationFailureKind {
    AUTHENTICATION_REQUIRED,
    ACCOUNT_REQUIRED,
    SESSION_REJECTED,
    INVALID_MATCH,
    UNAVAILABLE,
}

sealed interface OnlinePublicRankedMatchActivation {
    data class Ready(
        val playerId: String,
        val roomId: String,
        val matchId: String,
        val localSeatIndex: Int,
        val initialSnapshot: OnlineMatchSnapshotDto,
    ) : OnlinePublicRankedMatchActivation

    data class Failure(
        val kind: OnlinePublicRankedMatchActivationFailureKind,
    ) : OnlinePublicRankedMatchActivation
}
