package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DominoPlayerSeatParticipantTypeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val applicationParticipantLabel: String
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.participant_app_label)

    @Test
    fun application_participant_uses_robot_semantics_without_visible_word_label() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                DominoPlayerSeat(
                    name = "B03",
                    participantType =
                        DominoParticipantType.APPLICATION,
                    pieces = emptyList(),
                    isCurrent = false,
                    orientation =
                        DominoPlayerSeatOrientation.HORIZONTAL,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(
                applicationParticipantLabel,
            )
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText(
                applicationParticipantLabel,
            )
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText("B")
            .assertCountEquals(0)
    }

    @Test
    fun human_participant_uses_descriptive_semantics_without_single_initial() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                DominoPlayerSeat(
                    name = "A1F",
                    participantType =
                        DominoParticipantType.HUMAN,
                    pieces = emptyList(),
                    isCurrent = false,
                    orientation =
                        DominoPlayerSeatOrientation.HORIZONTAL,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("A1F")
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText("A")
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText(
                applicationParticipantLabel,
            )
            .assertCountEquals(0)
    }
}