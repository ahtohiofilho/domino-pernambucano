package com.ahtohiofilho.dominopernambucano.online

sealed interface OnlineParticipationResumeResult {
    data class WaitingRoom(
        val roomSnapshot: OnlineRoomSnapshotDto,
        val localSeatIndex: Int,
    ) : OnlineParticipationResumeResult

    data class ActiveMatch(
        val roomSnapshot: OnlineRoomSnapshotDto,
        val matchSnapshot: OnlineMatchSnapshotDto,
        val localSeatIndex: Int,
    ) : OnlineParticipationResumeResult

    data class Inactive(
        val reason: String,
    ) : OnlineParticipationResumeResult

    data class Unavailable(
        val reason: String,
    ) : OnlineParticipationResumeResult
}
