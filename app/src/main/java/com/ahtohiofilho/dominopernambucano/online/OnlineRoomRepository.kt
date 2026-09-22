package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull

data class OnlineActiveMatchSessionInvalidation(
    val roomId: String,
    val matchId: String,
    val playerId: String,
)

enum class OnlineActiveMatchResourceLossReason {
    ROOM_NOT_FOUND,
    MATCH_NOT_FOUND,
}

data class OnlineActiveMatchResourceLoss(
    val roomId: String,
    val matchId: String,
    val playerId: String,
    val reason: OnlineActiveMatchResourceLossReason,
)

enum class OnlineActiveMatchParticipationAuthorizationLossReason {
    ROOM_PARTICIPATION_FORBIDDEN,
    MATCH_PARTICIPATION_FORBIDDEN,
    ACTION_IDENTITY_FORBIDDEN,
}

data class OnlineActiveMatchParticipationAuthorizationLoss(
    val roomId: String,
    val matchId: String,
    val playerId: String,
    val reason: OnlineActiveMatchParticipationAuthorizationLossReason,
)

interface OnlineRoomRepository {
    val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?>

    val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?>

    /*
     * Fluxo destinado à apresentação sequencial. Implementações remotas
     * substituem o default por um SharedFlow para não conflar revisões.
     */
    val matchSnapshotEvents: Flow<OnlineMatchSnapshotDto>
        get() = matchSnapshot.filterNotNull()

    val activeMatchSessionInvalidationEvents:
        Flow<OnlineActiveMatchSessionInvalidation>
        get() = emptyFlow()

    val activeMatchResourceLossEvents:
        Flow<OnlineActiveMatchResourceLoss>
        get() = emptyFlow()

    val activeMatchParticipationAuthorizationLossEvents:
        Flow<OnlineActiveMatchParticipationAuthorizationLoss>
        get() = emptyFlow()

    suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    suspend fun movePrivateRoomSeat(
        targetSeatIndex: Int,
    ): OnlineRoomOperationResultDto {
        return OnlineRoomOperationResultDto(
            accepted = false,
            reason = "Alteração de posição não disponível neste repositório.",
        )
    }

    suspend fun startPrivateRoom(): OnlineRoomOperationResultDto {
        return OnlineRoomOperationResultDto(
            accepted = false,
            reason = "Início explícito não disponível neste repositório.",
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

    suspend fun activatePendingParticipationRoomResume(
        preparation:
            OnlinePendingParticipationMatchResumePreparation
                .WaitingForPlayers,
    ): OnlinePendingParticipationMatchResumeActivation {
        return OnlinePendingParticipationMatchResumeActivation
            .TemporarilyUnavailable(
                reason =
                    "Ativação remota de retomada de sala pendente não configurada.",
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

    suspend fun activatePublicRankedMatch(
        matchId: String,
        localSeatIndex: Int,
    ): OnlinePublicRankedMatchActivation {
        return OnlinePublicRankedMatchActivation.Failure(
            kind = OnlinePublicRankedMatchActivationFailureKind.UNAVAILABLE,
        )
    }

    /*
     * Confirma apenas que a superfície terminal autoritativa foi efetivamente
     * apresentada ao participante. A implementação deve remover somente o
     * vínculo persistido correspondente àquela partida, sem descartar o
     * snapshot que a tela final ainda está usando.
     */
    fun acknowledgeCompletedMatchPresentedLocally(
        binding: OnlineParticipationBinding,
    ) = Unit

    /*
     * Libera apenas o estado local de uma partida online já concluída.
     * Não envia ação remota e não representa abandono de partida ativa.
     */
    fun releaseCompletedMatchLocally() = Unit

    suspend fun leaveRoom()
}