package com.ahtohiofilho.dominopernambucano.ui

import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueState

internal enum class PendingOnlineRankedQueueStartupRecoveryAction {
    NONE,
    INSPECT_REMOTE_QUEUE,
    OPEN_RANKED_QUEUE,
}

/*
 * Ranked queue continuity is intentionally subordinate to persisted
 * room/match continuity. A local OnlineParticipationBinding is more specific
 * and must be reconciled first.
 *
 * queueResult == null means the caller has not queried the backend yet.
 */
internal fun resolvePendingOnlineRankedQueueStartupRecoveryAction(
    accountStatus: OnlineGoogleAccountStatus,
    pendingParticipation: OnlinePendingParticipationLocalResolution,
    queueResult: OnlineRankedQueueClientResult?,
): PendingOnlineRankedQueueStartupRecoveryAction {
    if (
        accountStatus != OnlineGoogleAccountStatus.CONNECTED ||
        pendingParticipation !=
            OnlinePendingParticipationLocalResolution
                .NoPendingParticipation
    ) {
        return PendingOnlineRankedQueueStartupRecoveryAction.NONE
    }

    if (queueResult == null) {
        return PendingOnlineRankedQueueStartupRecoveryAction
            .INSPECT_REMOTE_QUEUE
    }

    return when (queueResult) {
        is OnlineRankedQueueClientResult.Failure ->
            PendingOnlineRankedQueueStartupRecoveryAction.NONE

        is OnlineRankedQueueClientResult.Success -> {
            when (queueResult.state) {
                is OnlineRankedQueueState.Waiting,
                is OnlineRankedQueueState.Matched ->
                    PendingOnlineRankedQueueStartupRecoveryAction
                        .OPEN_RANKED_QUEUE

                OnlineRankedQueueState.NotQueued ->
                    PendingOnlineRankedQueueStartupRecoveryAction.NONE
            }
        }
    }
}