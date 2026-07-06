package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding

sealed interface OnlinePendingParticipationSessionRejection {
    data object NotRejected :
        OnlinePendingParticipationSessionRejection

    data class RemoteSessionRejected(
        val binding: OnlineParticipationBinding,
    ) : OnlinePendingParticipationSessionRejection
}
