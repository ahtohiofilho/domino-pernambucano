package com.ahtohiofilho.dominopernambucano.online

interface RemoteOnlineApiClient {
    suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto

    suspend fun fetchRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto

    suspend fun fetchMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto

    /*
     * Compatibilidade para clientes de teste e integrações antigas. O cliente
     * HTTP remoto substitui este fallback pelo endpoint incremental.
     */
    suspend fun fetchMatchSnapshotsAfter(
        matchId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto> {
        return listOf(
            fetchMatchSnapshot(matchId),
        ).filter { snapshot ->
            snapshot.revision > afterRevision
        }
    }
}