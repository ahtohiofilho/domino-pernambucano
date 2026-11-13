package com.ahtohiofilho.dominopernambucano.ui.menu

import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.test.localizedUiText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import org.junit.Rule
import org.junit.Test

class MainMenuScreenLocalSessionBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unresolved_remote_participation_does_not_block_safe_main_menu_navigation() {
        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            localSeatIndex = 0,
        )

        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution.ReadyForRemoteReconciliation(
                        binding = binding,
                    ),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState.Completed(
                        result =
                            com.ahtohiofilho.dominopernambucano.online
                                .OnlinePendingParticipationRemoteInspection
                                .TemporarilyUnavailable("network unavailable"),
                    ),
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        // Play routes to offline selection; competing online entry stays gated there.
        composeRule.onNodeWithText(localizedUiText(R.string.main_menu_play)).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun missing_valid_session_hides_resume_and_keeps_play_available() {
        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            localSeatIndex = 0,
        )

        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .BlockedByMissingValidAnonymousSession(
                            binding = binding,
                        ),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState
                        .NotRequested,
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText(
                localizedUiText(R.string.main_menu_previous_participation_unverifiable),
            )
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText(localizedUiText(R.string.main_menu_participation_confirmed))
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText(localizedUiText(R.string.main_menu_check_participation))
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText(localizedUiText(R.string.main_menu_resume_match))
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText(
                localizedUiText(R.string.main_menu_remove_rejected_participation),
            )
            .assertCountEquals(0)

        composeRule
            .onNodeWithText(localizedUiText(R.string.main_menu_play))
            .assertIsDisplayed()
            .assertIsEnabled()
    }
}
