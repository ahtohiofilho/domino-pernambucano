package com.ahtohiofilho.dominopernambucano.ui.menu

import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.test.localizedUiText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayModePendingOnlineParticipationContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pending_remote_binding_preserves_offline_play_and_blocks_duplicate_online_entry() {
        var offlineClicks = 0
        var onlineClicks = 0
        composeRule.setContent {
            DominoPernambucanoTheme {
                PlayModeScreen(
                    onBackClick = {},
                    onlineParticipationPending = true,
                    onRankedGameClick = { onlineClicks++ },
                    onLocalGameClick = { offlineClicks++ },
                    onCreateOnlineRoomClick = { onlineClicks++ },
                    onJoinOnlineRoomClick = { onlineClicks++ },
                )
            }
        }
        composeRule.onNodeWithText(localizedUiText(R.string.play_mode_ranked)).assertIsNotEnabled()
        composeRule.onNodeWithText(localizedUiText(R.string.play_mode_create_room)).assertIsNotEnabled()
        composeRule.onNodeWithText(localizedUiText(R.string.play_mode_join_room)).assertIsNotEnabled()
        composeRule.onNodeWithText(localizedUiText(R.string.play_mode_local)).assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, offlineClicks)
            assertEquals(0, onlineClicks)
        }
    }

    @Test
    fun online_modes_become_available_when_server_owned_binding_is_cleared() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                PlayModeScreen(
                    onBackClick = {},
                    onlineParticipationPending = false,
                    onRankedGameClick = {},
                    onLocalGameClick = {},
                    onCreateOnlineRoomClick = {},
                    onJoinOnlineRoomClick = {},
                )
            }
        }
        composeRule.onNodeWithText(localizedUiText(R.string.play_mode_ranked)).assertIsEnabled()
        composeRule.onNodeWithText(localizedUiText(R.string.play_mode_create_room)).assertIsEnabled()
        composeRule.onNodeWithText(localizedUiText(R.string.play_mode_join_room)).assertIsEnabled()
        composeRule.onNodeWithText(localizedUiText(R.string.play_mode_local)).assertIsEnabled()
    }
}
