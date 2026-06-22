package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

interface OnlineRoomRepository {
    val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?>

    val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?>

    /*
     * Fluxo destinado à apresentação sequencial. Implementações remotas
     * substituem o default por um SharedFlow para não conflar revisões.
     */
    val matchSnapshotEvents: Flow<OnlineMatchSnapshotDto>
        get() = matchSnapshot.filterNotNull()

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