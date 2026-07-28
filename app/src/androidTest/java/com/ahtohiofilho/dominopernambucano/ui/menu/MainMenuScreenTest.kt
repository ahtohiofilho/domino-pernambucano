package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInvalidReason
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationSessionRejection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainMenuScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ready_participation_without_completed_recoverable_inspection_shows_verification_only() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyParticipation(),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState.NotRequested,
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText("Verificar participação online")
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText("Retomar partida online")
            .assertCountEquals(0)
    }

    @Test
    fun completed_recoverable_inspection_shows_resume_and_hides_verification() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyParticipation(),
                pendingOnlineParticipationInspection =
                    recoverableInspection(),
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText("Retomar partida online")
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText("Verificar participação online")
            .assertCountEquals(0)
    }

    @Test
    fun completed_recoverable_waiting_inspection_shows_resume_room_and_hides_resume_match() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyWaitingParticipation(),
                pendingOnlineParticipationInspection =
                    recoverableWaitingInspection(),
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText(
                "Participação online confirmada. " +
                        "A sala ainda está aguardando jogadores.",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Retomar sala online")
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText("Retomar partida online")
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText("Verificar participação online")
            .assertCountEquals(0)
    }

    @Test
    fun remote_session_rejected_resume_feedback_hides_resume_and_shows_explicit_discard() {
        var discardClickCount = 0

        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyParticipation(),
                pendingOnlineParticipationInspection =
                    recoverableInspection(),
                pendingOnlineParticipationSessionRejection =
                    OnlinePendingParticipationSessionRejection
                        .RemoteSessionRejected(
                            binding = readyBinding(),
                        ),
                pendingOnlineMatchResumeInProgress = false,
                pendingOnlineMatchResumeFeedbackMessage =
                    "N\u00e3o foi poss\u00edvel retomar a partida: " +
                            "a sess\u00e3o online deste dispositivo foi rejeitada.",
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onDiscardRejectedPendingOnlineParticipationClick = {
                    discardClickCount += 1
                },
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText(
                "N\u00e3o foi poss\u00edvel retomar a partida: " +
                        "a sess\u00e3o online deste dispositivo foi rejeitada.",
            )
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText("Participa\u00e7\u00e3o online confirmada.")
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText("Retomar partida online")
            .assertCountEquals(0)

        composeRule
            .onNodeWithText(
                "Remover participa\u00e7\u00e3o online rejeitada",
            )
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, discardClickCount)
        }
    }

    @Test
    fun remote_session_rejected_inspection_shows_specific_message_without_resume() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyParticipation(),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState.Completed(
                        result =
                            OnlinePendingParticipationRemoteInspection
                                .RemoteSessionRejected,
                    ),
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText(
                "N\u00e3o foi poss\u00edvel verificar a participa\u00e7\u00e3o: " +
                        "a sess\u00e3o online deste dispositivo foi rejeitada.",
            )
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText("Participa\u00e7\u00e3o online confirmada.")
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText("Retomar partida online")
            .assertCountEquals(0)

        composeRule
            .onNodeWithText("Verificar participa\u00e7\u00e3o online")
            .assertIsDisplayed()
            .assertIsEnabled()

        composeRule
            .onNodeWithText("Jogar")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun rejected_binding_shows_explicit_discard_only_when_it_matches_pending_participation() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyParticipation(),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState.Completed(
                        result =
                            OnlinePendingParticipationRemoteInspection
                                .RemoteSessionRejected,
                    ),
                pendingOnlineParticipationSessionRejection =
                    OnlinePendingParticipationSessionRejection
                        .RemoteSessionRejected(
                            binding = readyBinding(),
                        ),
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onDiscardRejectedPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText(
                "Remover participa\u00e7\u00e3o online rejeitada",
            )
            .assertIsDisplayed()
            .assertIsEnabled()

        composeRule
            .onNodeWithText("Verificar participa\u00e7\u00e3o online")
            .assertIsDisplayed()
            .assertIsEnabled()

        composeRule
            .onNodeWithText("Jogar")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun completed_non_recoverable_inspection_does_not_show_resume() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyParticipation(),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState.Completed(
                        result =
                            OnlinePendingParticipationRemoteInspection
                                .NoLongerRecoverable(
                                    reason =
                                        OnlinePendingParticipationRemoteInvalidReason
                                            .ROOM_FINISHED,
                                ),
                    ),
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onAllNodesWithText("Retomar partida online")
            .assertCountEquals(0)

        composeRule
            .onNodeWithText("Verificar participação online")
            .assertIsDisplayed()
    }

    @Test
    fun resume_callback_runs_only_after_explicit_resume_click() {
        var resumeClickCount = 0

        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyParticipation(),
                pendingOnlineParticipationInspection =
                    recoverableInspection(),
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {
                    resumeClickCount += 1
                },
            )
        }

        composeRule.runOnIdle {
            assertEquals(
                0,
                resumeClickCount,
            )
        }

        composeRule
            .onNodeWithText("Retomar partida online")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                1,
                resumeClickCount,
            )
        }
    }

    @Test
    fun resume_in_progress_blocks_resume_and_play_actions() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation = readyParticipation(),
                pendingOnlineParticipationInspection =
                    recoverableInspection(),
                pendingOnlineMatchResumeInProgress = true,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText("Retomando...")
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText("Jogar")
            .assertIsNotEnabled()
    }

    private fun readyParticipation():
        OnlinePendingParticipationLocalResolution {
        return OnlinePendingParticipationLocalResolution
            .ReadyForRemoteReconciliation(
                binding = readyBinding(),
            )
    }

    private fun readyWaitingParticipation():
        OnlinePendingParticipationLocalResolution {
        return OnlinePendingParticipationLocalResolution
            .ReadyForRemoteReconciliation(
                binding = readyBinding(
                    matchId = null,
                ),
            )
    }

    private fun readyBinding(
        matchId: String? = "match-1",
    ): OnlineParticipationBinding {
        return OnlineParticipationBinding(
            roomId = "room-1",
            matchId = matchId,
            playerId = "player-1",
            localSeatIndex = 0,
        )
    }

    private fun recoverableWaitingInspection():
        OnlinePendingParticipationInspectionState {
        return OnlinePendingParticipationInspectionState.Completed(
            result = OnlinePendingParticipationRemoteInspection.Recoverable(
                roomSnapshot = OnlineRoomSnapshotDto(
                    roomId = "room-1",
                    roomCode = "123456",
                    hostPlayerId = "player-1",
                    status = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
                    players = listOf(
                        OnlineRoomPlayerDto(
                            playerId = "player-1",
                            name = "Jogador 1",
                            seatIndex = 0,
                            connected = true,
                        ),
                    ),
                    matchId = null,
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            ),
        )
    }

    private fun recoverableInspection():
        OnlinePendingParticipationInspectionState {
        return OnlinePendingParticipationInspectionState.Completed(
            result = OnlinePendingParticipationRemoteInspection.Recoverable(
                roomSnapshot = OnlineRoomSnapshotDto(
                    roomId = "room-1",
                    roomCode = "123456",
                    hostPlayerId = "player-1",
                    status = OnlineRoomStatusDto.IN_MATCH,
                    players = listOf(
                        OnlineRoomPlayerDto(
                            playerId = "player-1",
                            name = "Jogador 1",
                            seatIndex = 0,
                            connected = true,
                        ),
                    ),
                    matchId = "match-1",
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            ),
        )
    }
}
