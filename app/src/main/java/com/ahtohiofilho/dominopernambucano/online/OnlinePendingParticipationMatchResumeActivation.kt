package com.ahtohiofilho.dominopernambucano.online

sealed interface OnlinePendingParticipationMatchResumeActivation {
    data object Activated : OnlinePendingParticipationMatchResumeActivation

    data class NotAttempted(
        val reason: OnlinePendingParticipationRemoteBlockReason,
    ) : OnlinePendingParticipationMatchResumeActivation

    data class TemporarilyUnavailable(
        val reason: String,
    ) : OnlinePendingParticipationMatchResumeActivation
}
