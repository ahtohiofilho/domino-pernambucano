package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto

enum class PublicRankedQueueStatus {
    QUEUED,
    MATCHED,
    NOT_QUEUED,
    REJECTED,
}

data class PublicRankedQueueResult(
    val accepted: Boolean,
    val status: PublicRankedQueueStatus,
    val queuePosition: Int? = null,
    val roomSnapshot: OnlineRoomSnapshotDto? = null,
    val localSeatIndex: Int? = null,
    val reason: String? = null,
)
