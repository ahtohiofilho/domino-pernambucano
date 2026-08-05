package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineParticipantTypeLabelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun application_participant_is_identified_transparently() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val applicationParticipantLabel =
            context.getString(
                R.string.online_participant_app_controlled,
            )

        composeRule.setContent {
            DominoPernambucanoTheme {
                OnlineParticipantTypeLabel(
                    participantType =
                        OnlineParticipantTypeDto.APPLICATION,
                )
            }
        }

        composeRule
            .onNodeWithText(applicationParticipantLabel)
            .assertIsDisplayed()
    }

    @Test
    fun human_participant_has_no_application_label() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val applicationParticipantLabel =
            context.getString(
                R.string.online_participant_app_controlled,
            )

        composeRule.setContent {
            DominoPernambucanoTheme {
                OnlineParticipantTypeLabel(
                    participantType =
                        OnlineParticipantTypeDto.HUMAN,
                )
            }
        }

        composeRule
            .onAllNodesWithText(applicationParticipantLabel)
            .assertCountEquals(0)
    }
}
