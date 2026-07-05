package com.ahtohiofilho.dominopernambucano.online

sealed interface OnlinePendingParticipationMatchResumePreparation {
    data class Ready(
        val binding: OnlineParticipationBinding,
        val roomSnapshot: OnlineRoomSnapshotDto,
        val matchSnapshot: OnlineMatchSnapshotDto,
    ) : OnlinePendingParticipationMatchResumePreparation

    data class NotInMatch(
        val roomSnapshot: OnlineRoomSnapshotDto,
    ) : OnlinePendingParticipationMatchResumePreparation

    data class NoLongerRecoverable(
        val reason: OnlinePendingParticipationMatchResumeInvalidReason,
    ) : OnlinePendingParticipationMatchResumePreparation

    data class NotAttempted(
        val reason: OnlinePendingParticipationRemoteBlockReason,
    ) : OnlinePendingParticipationMatchResumePreparation

    data object RemoteSessionRejected : OnlinePendingParticipationMatchResumePreparation

    data class TemporarilyUnavailable(
        val reason: String,
    ) : OnlinePendingParticipationMatchResumePreparation
}

enum class OnlinePendingParticipationMatchResumeInvalidReason {
    ROOM_ID_MISMATCH,
    ROOM_CLOSED,
    ROOM_FINISHED,
    PLAYER_NOT_FOUND,
    LOCAL_SEAT_MISMATCH,
    MATCH_ID_MISMATCH,
    MISSING_MATCH_ID,
    MATCH_SNAPSHOT_ROOM_ID_MISMATCH,
    MATCH_SNAPSHOT_MATCH_ID_MISMATCH,
}
