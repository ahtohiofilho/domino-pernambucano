package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto

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

    /*
     * O backend remoto pode substituir o identificador provisório local pelo
     * playerId emitido na sessão anônima. A implementação fake permanece
     * transparente para testes e desenvolvimento local.
     */
    suspend fun resolveLocalPlayerIdentity(
        identity: OnlinePlayerIdentity,
    ): OnlinePlayerIdentity {
        return identity
    }

    /*
     * Reidrata uma participação persistida sem recriar a sala nem repetir o
     * join. Implementações que não suportam continuidade podem tratá-la como
     * vínculo inativo.
     */
    suspend fun resumeParticipation(
        binding: OnlineParticipationBinding,
    ): OnlineParticipationResumeResult {
        return OnlineParticipationResumeResult.Inactive(
            reason = "A retomada não é suportada por este backend online.",
        )
    }

    /*
     * Pausa de ciclo de vida não representa abandono. Implementações remotas
     * devem interromper apenas o loop de polling e preservar os snapshots e o
     * vínculo persistido do chamador.
     */
    fun pausePollingForBackground() = Unit

    /*
     * A reconciliação de foreground mantém o mesmo contrato autoritativo da
     * retomada persistida. Backends específicos podem restaurar o polling
     * antes de delegar ao fluxo de reconciliação.
     */
    suspend fun refreshAfterForeground(
        binding: OnlineParticipationBinding,
    ): OnlineParticipationResumeResult {
        return resumeParticipation(
            binding = binding,
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

    suspend fun leaveRoom()
}