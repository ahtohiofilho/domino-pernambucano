package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto

interface OnlineServerStore : AutoCloseable {
    fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto

    /**
     * Avança o relógio autoritativo e informa se o estado persistível mudou.
     */
    fun advanceAuthoritativeTime(): Boolean

    fun getRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto?

    fun isRoomParticipant(
        roomId: String,
        playerId: String,
    ): Boolean

    fun isMatchParticipant(
        matchId: String,
        playerId: String,
    ): Boolean

    fun getMatchSnapshotForParticipant(
        matchId: String,
        playerId: String,
    ): OnlineMatchSnapshotDto?

    fun getMatchSnapshotsAfterForParticipant(
        matchId: String,
        playerId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>?

    fun getMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto?

    fun getMatchSnapshotsAfter(
        matchId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>?

    override fun close() = Unit
}
