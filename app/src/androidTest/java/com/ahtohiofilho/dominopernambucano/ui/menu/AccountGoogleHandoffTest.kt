package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountDialogTag
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountPrimaryActionTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountGoogleHandoffTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun google_handoff_closes_account_dialog_and_reopens_after_action_finishes() {
        val actionInProgress = mutableStateOf(false)
        var connectClickCount = 0

        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState.NotRequested,
                pendingOnlineMatchResumeInProgress = false,
                onlineGoogleAccountStatus =
                    OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
                onlineGoogleAccountActionInProgress =
                    actionInProgress.value,
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
                onConnectGoogleAccountClick = {
                    connectClickCount += 1
                    actionInProgress.value = true
                },
            )
        }

        composeRule
            .onNodeWithTag(MainMenuAccountActionTag)
            .performClick()

        composeRule
            .onNodeWithTag(OnlineAccountDialogTag)
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(OnlineAccountPrimaryActionTag)
            .performClick()

        composeRule
            .onAllNodesWithTag(OnlineAccountDialogTag)
            .assertCountEquals(0)

        composeRule.runOnIdle {
            assertEquals(1, connectClickCount)
            actionInProgress.value = false
        }

        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(OnlineAccountDialogTag)
            .assertIsDisplayed()
    }

    @Test
    fun connected_home_identifies_the_signed_in_player() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState.NotRequested,
                pendingOnlineMatchResumeInProgress = false,
                onlineGoogleAccountStatus =
                    OnlineGoogleAccountStatus.CONNECTED,
                onlineAccountDisplayName = "Antonio",
                onlineAccountTableName = "ANT",
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText("Antonio")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Conta conectada · ANT")
            .assertIsDisplayed()
    }

    @Test
    fun play_mode_exposes_connecting_state_instead_of_silent_disabled_button() {
        composeRule.setContent {
            PlayModeScreen(
                onBackClick = {},
                onlineActionInProgress = true,
                onRankedGameClick = {},
                onLocalGameClick = {},
                onCreateOnlineRoomClick = {},
                onJoinOnlineRoomClick = {},
            )
        }

        composeRule
            .onNodeWithText("Conectando...")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Jogar online")
            .assertIsNotEnabled()
    }
}
