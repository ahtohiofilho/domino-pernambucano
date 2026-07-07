package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import org.junit.Rule
import org.junit.Test

class MainMenuScreenLocalSessionBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missing_valid_session_hides_resume_and_keeps_play_available() {
        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "player-1",
            localSeatIndex = 0,
        )

        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .BlockedByMissingValidAnonymousSession(
                            binding = binding,
                        ),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState
                        .NotRequested,
                pendingOnlineMatchResumeInProgress = false,
                onPlayClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithText(
                "A participa\u00e7\u00e3o online anterior n\u00e3o pode ser verificada neste dispositivo.",
            )
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText("Participa\u00e7\u00e3o online confirmada.")
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText("Verificar participa\u00e7\u00e3o online")
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText("Retomar partida online")
            .assertCountEquals(0)

        composeRule
            .onAllNodesWithText(
                "Remover participa\u00e7\u00e3o online rejeitada",
            )
            .assertCountEquals(0)

        composeRule
            .onNodeWithText("Jogar")
            .assertIsDisplayed()
            .assertIsEnabled()
    }
}
