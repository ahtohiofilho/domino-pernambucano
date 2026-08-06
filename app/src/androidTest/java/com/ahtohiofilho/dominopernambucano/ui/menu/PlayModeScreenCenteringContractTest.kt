package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PlayModeStandardViewportTag =
    "play_mode_standard_viewport"
private const val PlayModeShortViewportTag =
    "play_mode_short_viewport"

@RunWith(AndroidJUnit4::class)
class PlayModeScreenCenteringContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun play_mode_content_is_vertically_centered_on_standard_phone() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(720.dp)
                        .testTag(PlayModeStandardViewportTag),
                ) {
                    PlayModeScreen(
                        onBackClick = {},
                        onRankedGameClick = {},
                        onLocalGameClick = {},
                        onCreateOnlineRoomClick = {},
                        onJoinOnlineRoomClick = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()

        val viewportBounds =
            composeRule
                .onNodeWithTag(PlayModeStandardViewportTag)
                .fetchSemanticsNode()
                .boundsInRoot

        val contentBounds =
            composeRule
                .onNodeWithTag(PlayModeContentGroupTag)
                .fetchSemanticsNode()
                .boundsInRoot

        val contentCenterY =
            (contentBounds.top + contentBounds.bottom) / 2f

        val relativeCenter =
            (contentCenterY - viewportBounds.top) /
                viewportBounds.height

        assertTrue(
            "A tela Jogar continua excessivamente concentrada no topo.",
            relativeCenter >= 0.42f,
        )
        assertTrue(
            "A tela Jogar foi deslocada excessivamente para baixo.",
            relativeCenter <= 0.58f,
        )
    }

    @Test
    fun play_mode_keeps_back_action_reachable_on_short_phone() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val backLabel =
            context.getString(R.string.common_back)

        composeRule.setContent {
            DominoPernambucanoTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(360.dp)
                        .testTag(PlayModeShortViewportTag),
                ) {
                    PlayModeScreen(
                        onBackClick = {},
                        onRankedGameClick = {},
                        onLocalGameClick = {},
                        onCreateOnlineRoomClick = {},
                        onJoinOnlineRoomClick = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithText(backLabel)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
