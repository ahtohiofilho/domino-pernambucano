package com.ahtohiofilho.dominopernambucano.ui

import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueState
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingOnlineRankedQueueStartupRecoveryPolicyTest {
    @Test
    fun connected_account_without_room_binding_requests_queue_inspection() {
        assertEquals(
            PendingOnlineRankedQueueStartupRecoveryAction
                .INSPECT_REMOTE_QUEUE,
            resolvePendingOnlineRankedQueueStartupRecoveryAction(
                accountStatus =
                    OnlineGoogleAccountStatus.CONNECTED,
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                queueResult = null,
            ),
        )
    }

    @Test
    fun waiting_queue_opens_ranked_route() {
        assertEquals(
            PendingOnlineRankedQueueStartupRecoveryAction
                .OPEN_RANKED_QUEUE,
            resolvePendingOnlineRankedQueueStartupRecoveryAction(
                accountStatus =
                    OnlineGoogleAccountStatus.CONNECTED,
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                queueResult =
                    OnlineRankedQueueClientResult.Success(
                        state =
                            OnlineRankedQueueState.Waiting(
                                queuePosition = 1,
                                participantCodes =
                                    listOf("ABC"),
                            ),
                    ),
            ),
        )
    }

    @Test
    fun matched_queue_opens_ranked_route() {
        assertEquals(
            PendingOnlineRankedQueueStartupRecoveryAction
                .OPEN_RANKED_QUEUE,
            resolvePendingOnlineRankedQueueStartupRecoveryAction(
                accountStatus =
                    OnlineGoogleAccountStatus.CONNECTED,
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                queueResult =
                    OnlineRankedQueueClientResult.Success(
                        state =
                            OnlineRankedQueueState.Matched(
                                matchId = "match-1",
                            ),
                    ),
            ),
        )
    }

    @Test
    fun not_queued_keeps_main_menu() {
        assertEquals(
            PendingOnlineRankedQueueStartupRecoveryAction.NONE,
            resolvePendingOnlineRankedQueueStartupRecoveryAction(
                accountStatus =
                    OnlineGoogleAccountStatus.CONNECTED,
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                queueResult =
                    OnlineRankedQueueClientResult.Success(
                        state =
                            OnlineRankedQueueState.NotQueued,
                    ),
            ),
        )
    }

    @Test
    fun queue_failure_keeps_main_menu_without_looping() {
        assertEquals(
            PendingOnlineRankedQueueStartupRecoveryAction.NONE,
            resolvePendingOnlineRankedQueueStartupRecoveryAction(
                accountStatus =
                    OnlineGoogleAccountStatus.CONNECTED,
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                queueResult =
                    OnlineRankedQueueClientResult.Failure(
                        kind =
                            OnlineRankedQueueFailureKind
                                .UNAVAILABLE,
                        retryable = true,
                    ),
            ),
        )
    }

    @Test
    fun persisted_room_binding_has_priority_over_ranked_queue() {
        assertEquals(
            PendingOnlineRankedQueueStartupRecoveryAction.NONE,
            resolvePendingOnlineRankedQueueStartupRecoveryAction(
                accountStatus =
                    OnlineGoogleAccountStatus.CONNECTED,
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .ReadyForRemoteReconciliation(
                            binding =
                                OnlineParticipationBinding(
                                    roomId = "room-1",
                                    matchId = "match-1",
                                    playerId = "player-1",
                                    localSeatIndex = 0,
                                ),
                        ),
                queueResult = null,
            ),
        )
    }

    @Test
    fun recovery_required_account_waits_for_credential_recovery_first() {
        assertEquals(
            PendingOnlineRankedQueueStartupRecoveryAction.NONE,
            resolvePendingOnlineRankedQueueStartupRecoveryAction(
                accountStatus =
                    OnlineGoogleAccountStatus.RECOVERY_REQUIRED,
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                queueResult = null,
            ),
        )
    }
}