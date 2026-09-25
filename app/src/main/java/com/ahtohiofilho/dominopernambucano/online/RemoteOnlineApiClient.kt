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

    suspend fun requestEmailCode(
        request: OnlineEmailCodeRequestDto,
    ): OnlineEmailCodeRequestResponseDto {
        throw UnsupportedOperationException(
            "Solicitação de código por e-mail não configurada para este cliente remoto.",
        )
    }

    suspend fun linkEmailIdentity(
        request: OnlineEmailIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        throw UnsupportedOperationException(
            "Vinculação por e-mail não configurada para este cliente remoto.",
        )
    }

    suspend fun recoverEmailAccount(
        request: OnlineEmailIdentityRequestDto,
    ): OnlineAccountSessionDto {
        throw UnsupportedOperationException(
            "Recuperação por e-mail não configurada para este cliente remoto.",
        )
    }

    suspend fun registerEmailPassword(
        request: OnlinePasswordRegisterRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        throw UnsupportedOperationException(
            "Cadastro por e-mail e senha não configurado para este cliente remoto.",
        )
    }

    suspend fun loginEmailPassword(
        request: OnlinePasswordLoginRequestDto,
    ): OnlineAccountSessionDto {
        throw UnsupportedOperationException(
            "Login por e-mail e senha não configurado para este cliente remoto.",
        )
    }

    suspend fun resetEmailPassword(
        request: OnlinePasswordResetRequestDto,
    ): OnlineAccountSessionDto {
        throw UnsupportedOperationException(
            "Redefinição de senha não configurada para este cliente remoto.",
        )
    }

    suspend fun enqueuePublicRankedQueue(
        request: PublicRankedQueueEnterRequestDto,
    ): PublicRankedQueueHttpResponseDto {
        throw UnsupportedOperationException(
            "Entrada na fila rankeada não configurada para este cliente remoto.",
        )
    }

    suspend fun fetchPublicRankedQueueStatus():
        PublicRankedQueueHttpResponseDto {
        throw UnsupportedOperationException(
            "Consulta da fila rankeada não configurada para este cliente remoto.",
        )
    }

    suspend fun cancelPublicRankedQueue():
        PublicRankedQueueHttpResponseDto {
        throw UnsupportedOperationException(
            "Cancelamento da fila rankeada não configurado para este cliente remoto.",
        )
    }

    suspend fun fetchPublicRanking(
        cycle: PublicRankingCycleDto,
        offset: Int = 0,
        limit: Int = 50,
        rankingRevision: String? = null,
    ): PublicRankingResponseDto {
        throw UnsupportedOperationException(
            "Consulta do ranking público não configurada para este cliente remoto.",
        )
    }

    suspend fun fetchPublicRankingRevalidated(
        cycle: PublicRankingCycleDto,
        offset: Int = 0,
        limit: Int = 50,
        rankingRevision: String? = null,
    ): PublicRankingResponseDto {
        return fetchPublicRanking(
            cycle = cycle,
            offset = offset,
            limit = limit,
            rankingRevision = rankingRevision,
        )
    }

    suspend fun fetchHistoricalPublicRanking(
        cycle: PublicRankingCycleDto,
        cycleId: String,
        offset: Int = 0,
        limit: Int = 50,
    ): PublicRankingResponseDto {
        throw UnsupportedOperationException(
            "Consulta do ranking hist├│rico n├úo configurada para este cliente remoto.",
        )
    }

    suspend fun fetchPublicRankingCycles(
        cycle: PublicRankingCycleDto,
        offset: Int = 0,
        limit: Int = 50,
    ): PublicRankingCyclesResponseDto {
        throw UnsupportedOperationException(
            "Consulta dos ciclos encerrados n├úo configurada para este cliente remoto.",
        )
    }

    suspend fun fetchAccountProfile():
        OnlineAccountProfileResponseDto {
        throw UnsupportedOperationException(
            "Consulta do perfil da conta não configurada para este cliente remoto.",
        )
    }

    suspend fun updateAccountProfile(
        request: OnlineAccountProfileUpdateRequestDto,
    ): OnlineAccountProfileResponseDto {
        throw UnsupportedOperationException(
            "Atualização do perfil da conta não configurada para este cliente remoto.",
        )
    }

    suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun movePrivateRoomSeat(
        request: PrivateRoomSeatChangeRequestDto,
    ): OnlineRoomOperationResultDto {
        throw UnsupportedOperationException(
            "Alteração de posição da sala privada não configurada.",
        )
    }

    suspend fun leavePrivateRoom(
        request: PrivateRoomLeaveRequestDto,
    ): OnlineRoomOperationResultDto {
        throw UnsupportedOperationException(
            "Saída do lobby privado não configurada.",
        )
    }

    suspend fun completePrivateRoom(
        request: PrivateRoomCompleteRequestDto,
    ): OnlineRoomOperationResultDto {
        throw UnsupportedOperationException(
            "Preenchimento automático da sala privada não configurado.",
        )
    }

    suspend fun removePrivateRoomAutomaticPlayer(
        request: PrivateRoomRemoveAutomaticPlayerRequestDto,
    ): OnlineRoomOperationResultDto {
        throw UnsupportedOperationException(
            "Liberação de lugar automático não configurada.",
        )
    }

    suspend fun startPrivateRoom(
        request: PrivateRoomStartRequestDto,
    ): OnlineRoomOperationResultDto {
        throw UnsupportedOperationException(
            "Início explícito da sala privada não configurado.",
        )
    }

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
