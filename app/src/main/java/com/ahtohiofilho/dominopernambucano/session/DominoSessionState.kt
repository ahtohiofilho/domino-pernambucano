package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution

sealed interface DominoSessionState {
    data class MainMenu(
        val pendingOnlineParticipation:
            OnlinePendingParticipationLocalResolution,
        val pendingOnlineParticipationInspection:
            OnlinePendingParticipationInspectionState =
                OnlinePendingParticipationInspectionState
                    .NotRequested,
        val pendingOnlineParticipationSessionRejection:
            OnlinePendingParticipationSessionRejection =
                OnlinePendingParticipationSessionRejection
                    .NotRejected,
    ) : DominoSessionState {
        val hasPendingOnlineParticipation: Boolean
            get() = when (pendingOnlineParticipation) {
                OnlinePendingParticipationLocalResolution.NoPendingParticipation -> false
                else -> true
            }
    }

    data object PlayModeSelection : DominoSessionState

    data object PublicRanking : DominoSessionState

    data class LocalMatch(
        val matchCoordinator: DominoMatchCoordinator,
    ) : DominoSessionState

    data object OnlineRankedQueue : DominoSessionState

    data object OnlineCreateRoom : DominoSessionState

    data object OnlineJoinRoom : DominoSessionState

    data class OnlineResumedRoom(
        val binding: OnlineParticipationBinding,
    ) : DominoSessionState

    data class OnlineMatch(
        val matchCoordinator: OnlineDominoMatchCoordinator,
    ) : DominoSessionState
}