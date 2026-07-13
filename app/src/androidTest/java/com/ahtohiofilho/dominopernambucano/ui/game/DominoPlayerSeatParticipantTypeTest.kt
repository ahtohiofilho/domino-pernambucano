package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DominoPlayerSeatParticipantTypeTest {
    @get:Rule
    val composeRule = createComposeRule()

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
                DOMINO_APPLICATION_PARTICIPANT_LABEL,
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
                DOMINO_APPLICATION_PARTICIPANT_LABEL,
            )
            .assertCountEquals(0)
    }
}
