package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto

interface RemoteOnlineApiClient {
    /*
     * O cliente remoto conserva a identidade transitória apenas para anexar o
     * cabeçalho de homologação nas leituras HTTP. Implementações de teste podem
     * manter o no-op padrão.
     */
    fun setDevelopmentPlayerId(
        playerId: String?,
    ) = Unit

    /*
     * Token Bearer attivo per le route online protette.
     *
     * Le implementazioni di test possono mantenere il no-op predefinito.
     * Il token non deve essere registrato in trace o log.
     */
    fun setBearerAccessToken(
        accessToken: String?,
    ) = Unit

    suspend fun createAnonymousSession(): OnlineAnonymousSessionDto {
        throw UnsupportedOperationException(
            "Emissão de sessão anônima não configurada para este cliente remoto.",
        )
    }

    suspend fun promoteAccount(): OnlineAccountSessionDto {
        throw UnsupportedOperationException(
            "Promoção de conta não configurada para este cliente remoto.",
        )
    }

    suspend fun linkGoogleIdentity(
        request: OnlineGoogleIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        throw UnsupportedOperationException(
            "Vinculação Google não configurada para este cliente remoto.",
        )
    }

    suspend fun recoverGoogleAccount(
        request: OnlineGoogleIdentityRequestDto,
    ): OnlineAccountSessionDto {
        throw UnsupportedOperationException(
            "Recuperação Google não configurada para este cliente remoto.",
        )
    }

    suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto

    suspend fun submitTraceBatch(
        batch: OnlineTraceBatchDto,
    ): OnlineTraceBatchResultDto {
        return OnlineTraceBatchResultDto(
            accepted = true,
            storedEntryCount = batch.entries.size,
        )
    }

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
