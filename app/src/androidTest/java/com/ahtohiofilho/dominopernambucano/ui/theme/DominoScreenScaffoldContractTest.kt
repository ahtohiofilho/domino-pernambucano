package com.ahtohiofilho.dominopernambucano.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val CenteredViewportTag =
    "harmony_centered_viewport"
private const val CenteredGroupTag =
    "harmony_centered_group"
private const val CenteredTitleTag =
    "harmony_centered_title"
private const val CenteredStatusTag =
    "harmony_centered_status"
private const val ShortViewportTag =
    "harmony_short_viewport"
private const val LastActionTag =
    "harmony_last_action"

@RunWith(AndroidJUnit4::class)
class DominoScreenScaffoldContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun centered_layout_centers_title_status_and_actions_as_one_group() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(720.dp)
                        .testTag(CenteredViewportTag),
                ) {
                    DominoScreenScaffold(
                        title = "Título",
                        layout = DominoScreenLayout.Centered,
                        contentModifier = Modifier.testTag(
                            CenteredGroupTag,
                        ),
                        titleModifier = Modifier.testTag(
                            CenteredTitleTag,
                        ),
                        statusMessage = "Estado",
                        statusModifier = Modifier.testTag(
                            CenteredStatusTag,
                        ),
                    ) {
                        DominoSecondaryActionCard(
                            title = "Primeira ação",
                            supportingText = null,
                            accent = DominoBrandAccent.Blue,
                            onClick = {},
                        )
                        DominoSecondaryActionCard(
                            title = "Última ação",
                            supportingText = null,
                            accent = DominoBrandAccent.Green,
                            onClick = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()

        val viewport =
            composeRule
                .onNodeWithTag(CenteredViewportTag)
                .fetchSemanticsNode()
                .boundsInRoot

        val group =
            composeRule
                .onNodeWithTag(CenteredGroupTag)
                .fetchSemanticsNode()
                .boundsInRoot

        val title =
            composeRule
                .onNodeWithTag(CenteredTitleTag)
                .fetchSemanticsNode()
                .boundsInRoot

        val groupCenterY =
            (group.top + group.bottom) / 2f
        val relativeCenterY =
            (groupCenterY - viewport.top) /
                viewport.height

        assertTrue(
            "O bloco harmonizado não ficou centralizado.",
            relativeCenterY in 0.42f..0.58f,
        )

        val groupCenterX =
            (group.left + group.right) / 2f
        val titleCenterX =
            (title.left + title.right) / 2f

        assertTrue(
            "O título não acompanha o centro horizontal do bloco.",
            abs(groupCenterX - titleCenterX) <= 1f,
        )

        composeRule
            .onNodeWithTag(CenteredStatusTag)
            .assertIsDisplayed()
    }

    @Test
    fun top_layout_keeps_last_action_reachable_on_short_height() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(320.dp)
                        .testTag(ShortViewportTag),
                ) {
                    DominoScreenScaffold(
                        title = "Formulário",
                        layout = DominoScreenLayout.Top,
                    ) {
                        DominoSecondaryActionCard(
                            title = "Ação 1",
                            supportingText = null,
                            accent = DominoBrandAccent.Blue,
                            onClick = {},
                        )
                        DominoSecondaryActionCard(
                            title = "Ação 2",
                            supportingText = null,
                            accent = DominoBrandAccent.Green,
                            onClick = {},
                        )
                        DominoSecondaryActionCard(
                            title = "Ação 3",
                            supportingText = null,
                            accent = DominoBrandAccent.Red,
                            onClick = {},
                        )
                        DominoSecondaryActionCard(
                            title = "Voltar",
                            supportingText = null,
                            accent = DominoBrandAccent.Blue,
                            onClick = {},
                            modifier = Modifier.testTag(
                                LastActionTag,
                            ),
                        )
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag(LastActionTag)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
