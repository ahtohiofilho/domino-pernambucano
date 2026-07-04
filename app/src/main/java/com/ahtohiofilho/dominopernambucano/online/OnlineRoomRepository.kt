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

    suspend fun inspectPendingParticipation(
        binding: OnlineParticipationBinding,
    ): OnlinePendingParticipationRemoteInspection {
        return OnlinePendingParticipationRemoteInspection
            .TemporarilyUnavailable(
                reason =
                    "Inspeção remota de participação pendente não configurada.",
            )
    }

    suspend fun preparePendingParticipationMatchResume(
        binding: OnlineParticipationBinding,
    ): OnlinePendingParticipationMatchResumePreparation {
        return OnlinePendingParticipationMatchResumePreparation
            .TemporarilyUnavailable(
                reason =
                    "Preparação remota de retomada de participação pendente não configurada.",
            )
    }

    suspend fun activatePendingParticipationMatchResume(
        preparation: OnlinePendingParticipationMatchResumePreparation.Ready,
    ): OnlinePendingParticipationMatchResumeActivation {
        return OnlinePendingParticipationMatchResumeActivation
            .TemporarilyUnavailable(
                reason =
                    "Ativação remota de retomada de participação pendente não configurada.",
            )
    }

    suspend fun leaveRoom()
}