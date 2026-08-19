package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import org.junit.Assert.assertEquals
import org.junit.Test

class DominoMatchHeaderTest {
    @Test
    fun valid_user_selected_table_code_is_preserved() {
        assertEquals(
            "A1F",
            resolveDominoMatchHeaderCode(
                name = "a1f",
                participantType = DominoParticipantType.HUMAN,
                playerIndex = 0,
            ),
        )
    }

    @Test
    fun application_valid_table_code_is_preserved() {
        assertEquals(
            "B03",
            resolveDominoMatchHeaderCode(
                name = "b03",
                participantType = DominoParticipantType.APPLICATION,
                playerIndex = 1,
            ),
        )
    }

    @Test
    fun legacy_human_full_name_uses_neutral_dash() {
        assertEquals(
            "\u2014",
            resolveDominoMatchHeaderCode(
                name = "Antônio Filho",
                participantType = DominoParticipantType.HUMAN,
                playerIndex = 2,
            ),
        )
    }

    @Test
    fun legacy_application_name_uses_neutral_dash() {
        assertEquals(
            "\u2014",
            resolveDominoMatchHeaderCode(
                name = "Aplicativo",
                participantType = DominoParticipantType.APPLICATION,
                playerIndex = 3,
            ),
        )
    }

    @Test
    fun team_labels_follow_local_partner_against_left_right() {
        assertEquals(
            DominoMatchHeaderTeamLabels(
                localTeam = "VCE·JG3",
                opponentTeam = "JG2·JG4",
            ),
            buildDominoMatchHeaderTeamLabels(
                playerCodes = listOf(
                    "VCE",
                    "JG2",
                    "JG3",
                    "JG4",
                ),
                localPlayerIndex = 0,
            ),
        )
    }

    @Test
    fun team_labels_rotate_with_local_player_without_changing_team_parity() {
        assertEquals(
            DominoMatchHeaderTeamLabels(
                localTeam = "JG2·JG4",
                opponentTeam = "JG3·VCE",
            ),
            buildDominoMatchHeaderTeamLabels(
                playerCodes = listOf(
                    "VCE",
                    "JG2",
                    "JG3",
                    "JG4",
                ),
                localPlayerIndex = 1,
            ),
        )
    }
}