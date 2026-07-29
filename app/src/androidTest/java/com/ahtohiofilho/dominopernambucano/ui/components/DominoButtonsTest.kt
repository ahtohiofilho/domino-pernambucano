package com.ahtohiofilho.dominopernambucano.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DominoButtonsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primary_button_has_accessible_height_and_dispatches_click() {
        var clicks = 0

        composeRule.setContent {
            DominoPernambucanoTheme {
                DominoPrimaryButton(
                    modifier = Modifier.testTag("primary"),
                    text = "Jogar",
                    onClick = {
                        clicks += 1
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag("primary")
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun loading_button_is_disabled_and_exposes_progress_semantics() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                DominoPrimaryButton(
                    modifier = Modifier.testTag("loading"),
                    text = "Salvando",
                    onClick = {},
                    loading = true,
                )
            }
        }

        composeRule
            .onNodeWithTag("loading")
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate,
                ),
            )
    }

    @Test
    fun secondary_and_text_actions_keep_accessible_minimum_height() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                Column {
                    DominoSecondaryButton(
                        modifier = Modifier.testTag("secondary"),
                        text = "Voltar",
                        onClick = {},
                    )
                    DominoTextAction(
                        modifier = Modifier.testTag("text"),
                        text = "Fechar",
                        onClick = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("secondary")
            .assertHeightIsAtLeast(48.dp)

        composeRule
            .onNodeWithTag("text")
            .assertHeightIsAtLeast(48.dp)
    }
}
