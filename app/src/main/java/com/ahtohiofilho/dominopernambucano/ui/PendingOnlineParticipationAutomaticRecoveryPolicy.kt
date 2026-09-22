package com.ahtohiofilho.dominopernambucano.ui

import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationSessionRejection

internal enum class PendingOnlineParticipationAutomaticRecoveryAction {
    NONE,
    INSPECT,
    RESUME,
}

/*
 * Automatic continuity is deliberately one-shot per authoritative state
 * transition. A transient remote failure does not immediately loop: the
 * existing UI remains available as an explicit retry path.
 */
internal fun resolvePendingOnlineParticipationAutomaticRecoveryAction(
    pendingParticipation: OnlinePendingParticipationLocalResolution,
    inspectionState: OnlinePendingParticipationInspectionState,
    sessionRejection: OnlinePendingParticipationSessionRejection,
): PendingOnlineParticipationAutomaticRecoveryAction {
    val readyParticipation =
        pendingParticipation as?
            OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation
            ?: return PendingOnlineParticipationAutomaticRecoveryAction.NONE

    val rejectedBinding = (
        sessionRejection as?
            OnlinePendingParticipationSessionRejection
                .RemoteSessionRejected
        )?.binding

    if (rejectedBinding == readyParticipation.binding) {
        return PendingOnlineParticipationAutomaticRecoveryAction.NONE
    }

    return when (inspectionState) {
        OnlinePendingParticipationInspectionState.NotRequested ->
            PendingOnlineParticipationAutomaticRecoveryAction.INSPECT

        OnlinePendingParticipationInspectionState.InProgress ->
            PendingOnlineParticipationAutomaticRecoveryAction.NONE

        is OnlinePendingParticipationInspectionState.Completed ->
            if (
                inspectionState.result is
                    OnlinePendingParticipationRemoteInspection.Recoverable
            ) {
                PendingOnlineParticipationAutomaticRecoveryAction.RESUME
            } else {
                PendingOnlineParticipationAutomaticRecoveryAction.NONE
            }
    }
}