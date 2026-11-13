package com.ahtohiofilho.dominopernambucano.ui.menu

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
        composeRule.onNodeWithText("Jogar online").assertIsNotEnabled()
        composeRule.onNodeWithText("Criar sala").assertIsNotEnabled()
        composeRule.onNodeWithText("Entrar em sala").assertIsNotEnabled()
        composeRule.onNodeWithText("Jogar offline").assertIsEnabled().performClick()
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
        composeRule.onNodeWithText("Jogar online").assertIsEnabled()
        composeRule.onNodeWithText("Criar sala").assertIsEnabled()
        composeRule.onNodeWithText("Entrar em sala").assertIsEnabled()
        composeRule.onNodeWithText("Jogar offline").assertIsEnabled()
    }
}
