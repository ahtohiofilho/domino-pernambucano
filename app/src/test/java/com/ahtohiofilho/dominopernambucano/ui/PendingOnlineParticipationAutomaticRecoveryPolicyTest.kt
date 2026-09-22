package com.ahtohiofilho.dominopernambucano.ui

import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationSessionRejection
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingOnlineParticipationAutomaticRecoveryPolicyTest {
    @Test
    fun no_pending_participation_does_nothing() {
        assertEquals(
            PendingOnlineParticipationAutomaticRecoveryAction.NONE,
            resolvePendingOnlineParticipationAutomaticRecoveryAction(
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                inspectionState =
                    OnlinePendingParticipationInspectionState.NotRequested,
                sessionRejection =
                    OnlinePendingParticipationSessionRejection.NotRejected,
            ),
        )
    }

    @Test
    fun ready_uninspected_participation_is_inspected_automatically() {
        assertEquals(
            PendingOnlineParticipationAutomaticRecoveryAction.INSPECT,
            resolvePendingOnlineParticipationAutomaticRecoveryAction(
                pendingParticipation = readyParticipation(),
                inspectionState =
                    OnlinePendingParticipationInspectionState.NotRequested,
                sessionRejection =
                    OnlinePendingParticipationSessionRejection.NotRejected,
            ),
        )
    }

    @Test
    fun in_progress_inspection_does_not_start_another_operation() {
        assertEquals(
            PendingOnlineParticipationAutomaticRecoveryAction.NONE,
            resolvePendingOnlineParticipationAutomaticRecoveryAction(
                pendingParticipation = readyParticipation(),
                inspectionState =
                    OnlinePendingParticipationInspectionState.InProgress,
                sessionRejection =
                    OnlinePendingParticipationSessionRejection.NotRejected,
            ),
        )
    }

    @Test
    fun confirmed_recoverable_participation_resumes_automatically() {
        assertEquals(
            PendingOnlineParticipationAutomaticRecoveryAction.RESUME,
            resolvePendingOnlineParticipationAutomaticRecoveryAction(
                pendingParticipation = readyParticipation(),
                inspectionState =
                    OnlinePendingParticipationInspectionState.Completed(
                        result =
                            OnlinePendingParticipationRemoteInspection
                                .Recoverable(
                                    roomSnapshot = recoverableRoom(),
                                ),
                    ),
                sessionRejection =
                    OnlinePendingParticipationSessionRejection.NotRejected,
            ),
        )
    }

    @Test
    fun transient_inspection_failure_does_not_auto_loop() {
        assertEquals(
            PendingOnlineParticipationAutomaticRecoveryAction.NONE,
            resolvePendingOnlineParticipationAutomaticRecoveryAction(
                pendingParticipation = readyParticipation(),
                inspectionState =
                    OnlinePendingParticipationInspectionState.Completed(
                        result =
                            OnlinePendingParticipationRemoteInspection
                                .TemporarilyUnavailable(
                                    reason = "offline",
                                ),
                    ),
                sessionRejection =
                    OnlinePendingParticipationSessionRejection.NotRejected,
            ),
        )
    }

    @Test
    fun matching_remote_session_rejection_suppresses_automatic_resume() {
        val binding = binding()

        assertEquals(
            PendingOnlineParticipationAutomaticRecoveryAction.NONE,
            resolvePendingOnlineParticipationAutomaticRecoveryAction(
                pendingParticipation =
                    OnlinePendingParticipationLocalResolution
                        .ReadyForRemoteReconciliation(
                            binding = binding,
                        ),
                inspectionState =
                    OnlinePendingParticipationInspectionState.Completed(
                        result =
                            OnlinePendingParticipationRemoteInspection
                                .Recoverable(
                                    roomSnapshot = recoverableRoom(),
                                ),
                    ),
                sessionRejection =
                    OnlinePendingParticipationSessionRejection
                        .RemoteSessionRejected(
                            binding = binding,
                        ),
            ),
        )
    }

    private fun readyParticipation():
        OnlinePendingParticipationLocalResolution {
        return OnlinePendingParticipationLocalResolution
            .ReadyForRemoteReconciliation(
                binding = binding(),
            )
    }

    private fun binding(): OnlineParticipationBinding {
        return OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            localSeatIndex = 0,
        )
    }

    private fun recoverableRoom(): OnlineRoomSnapshotDto {
        return OnlineRoomSnapshotDto(
            roomId = "room-1",
            roomCode = "123456",
            hostPlayerId = "player-1",
            status = OnlineRoomStatusDto.IN_MATCH,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = "player-1",
                    name = "P1",
                    seatIndex = 0,
                    connected = true,
                ),
            ),
            matchId = "match-1",
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
    }
}