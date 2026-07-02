package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection

sealed interface OnlinePendingParticipationInspectionState {
    data object NotRequested :
        OnlinePendingParticipationInspectionState

    data object InProgress :
        OnlinePendingParticipationInspectionState

    data class Completed(
        val result: OnlinePendingParticipationRemoteInspection,
    ) : OnlinePendingParticipationInspectionState
}
