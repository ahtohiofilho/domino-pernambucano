package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
    fun application_participant_is_identified_on_game_table() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                DominoPlayerSeat(
                    name = "Bot 3",
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
            .onNodeWithText("Bot 3")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                applicationParticipantLabel,
            )
            .assertIsDisplayed()
    }

    @Test
    fun human_participant_has_no_application_label_on_game_table() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                DominoPlayerSeat(
                    name = "Jogador humano",
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
            .onNodeWithText("Jogador humano")
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText(
                applicationParticipantLabel,
            )
            .assertCountEquals(0)
    }
}
