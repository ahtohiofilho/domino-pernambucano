package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundIntroPresentationNamesTest {
    @Test
    fun round_intro_uses_only_valid_short_table_codes_without_changing_domain_names() {
        val players = listOf(
            DominoPlayer(
                id = 0,
                name = "A1F",
                hand = emptyList(),
            ),
            DominoPlayer(
                id = 1,
                name = "M02",
                hand = emptyList(),
            ),
            DominoPlayer(
                id = 2,
                name = "José da Silva",
                hand = emptyList(),
            ),
            DominoPlayer(
                id = 3,
                name = "J04",
                hand = emptyList(),
            ),
        )

        val presentations = buildRoundIntroTeamPresentations(
            players = players,
            teamScores = listOf(4, 2),
            localPlayerIndex = 0,
        )

        assertEquals(
            listOf("A1F", "\u2014"),
            presentations.topTeam.playerNames,
        )

        assertEquals(
            listOf("M02", "J04"),
            presentations.bottomTeam.playerNames,
        )

        assertEquals(
            listOf(
                "A1F",
                "M02",
                "José da Silva",
                "J04",
            ),
            players.map { player ->
                player.name
            },
        )
    }
}