package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundIntroPresentationNamesTest {
    @Test
    fun round_intro_uses_short_table_names_without_changing_domain_names() {
        val players = listOf(
            DominoPlayer(
                id = 0,
                name = "Antônio Filho",
                hand = emptyList(),
            ),
            DominoPlayer(
                id = 1,
                name = "Maria Eduarda",
                hand = emptyList(),
            ),
            DominoPlayer(
                id = 2,
                name = "José da Silva",
                hand = emptyList(),
            ),
            DominoPlayer(
                id = 3,
                name = "João Pedro",
                hand = emptyList(),
            ),
        )

        val presentations = buildRoundIntroTeamPresentations(
            players = players,
            teamScores = listOf(4, 2),
            localPlayerIndex = 0,
        )

        assertEquals(
            listOf("ANTÔNIO", "JOSÉ"),
            presentations.topTeam.playerNames,
        )

        assertEquals(
            listOf("MARIA", "JOÃO"),
            presentations.bottomTeam.playerNames,
        )

        assertEquals(
            listOf(
                "Antônio Filho",
                "Maria Eduarda",
                "José da Silva",
                "João Pedro",
            ),
            players.map { player ->
                player.name
            },
        )
    }
}
