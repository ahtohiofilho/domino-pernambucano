package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineProviderChoiceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disconnected_online_action_offers_play_games_and_google() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val playOnline =
            context.getString(R.string.play_mode_ranked)
        val playGames =
            context.getString(R.string.account_play_games_continue)
        val google =
            context.getString(R.string.account_google_continue)
        var playGamesClicks = 0
        var googleClicks = 0

        composeRule.setContent {
            DominoPernambucanoTheme {
                PlayModeScreen(
                    onBackClick = {},
                    onlineAccountStatus =
                        OnlineGoogleAccountStatus
                            .NO_LOCAL_CREDENTIAL,
                    playGamesAvailable = true,
                    googleAvailable = true,
                    onConnectPlayGamesClick = {
                        playGamesClicks += 1
                    },
                    onConnectGoogleClick = {
                        googleClicks += 1
                    },
                    onRankedGameClick = {},
                    onLocalGameClick = {},
                    onCreateOnlineRoomClick = {},
                    onJoinOnlineRoomClick = {},
                )
            }
        }

        composeRule
            .onNodeWithText(playOnline)
            .performClick()

        composeRule
            .onNodeWithTag(PlayModeAccountChoiceDialogTag)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(playGames)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, playGamesClicks)
            assertEquals(0, googleClicks)
        }

        composeRule
            .onNodeWithText(google)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, playGamesClicks)
            assertEquals(1, googleClicks)
        }
    }
}
