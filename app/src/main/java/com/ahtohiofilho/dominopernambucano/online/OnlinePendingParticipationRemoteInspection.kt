package com.ahtohiofilho.dominopernambucano.online

sealed interface OnlinePendingParticipationRemoteInspection {
    data class Recoverable(
        val roomSnapshot: OnlineRoomSnapshotDto,
    ) : OnlinePendingParticipationRemoteInspection

    data class NoLongerRecoverable(
        val reason: OnlinePendingParticipationRemoteInvalidReason,
    ) : OnlinePendingParticipationRemoteInspection

    data class NotAttempted(
        val reason: OnlinePendingParticipationRemoteBlockReason,
    ) : OnlinePendingParticipationRemoteInspection

    data class TemporarilyUnavailable(
        val reason: String,
    ) : OnlinePendingParticipationRemoteInspection
}

enum class OnlinePendingParticipationRemoteInvalidReason {
    ROOM_ID_MISMATCH,
    ROOM_CLOSED,
    ROOM_FINISHED,
    PLAYER_NOT_FOUND,
    LOCAL_SEAT_MISMATCH,
    MATCH_ID_MISMATCH,
}

enum class OnlinePendingParticipationRemoteBlockReason {
    MISSING_VALID_ANONYMOUS_SESSION,
    ANONYMOUS_SESSION_IDENTITY_MISMATCH,
}
