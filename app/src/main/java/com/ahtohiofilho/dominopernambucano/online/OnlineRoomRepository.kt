package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.flow.StateFlow

interface OnlineRoomRepository {
    val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?>

    val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?>

    suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto

    suspend fun leaveRoom()
}