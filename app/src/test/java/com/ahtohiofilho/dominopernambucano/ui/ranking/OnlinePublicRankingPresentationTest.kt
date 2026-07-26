package com.ahtohiofilho.dominopernambucano.ui.ranking

import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingFailureKind
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePublicRankingPresentationTest {
    @Test
    fun cycle_labels_are_direct_and_complete() {
        assertEquals(
            listOf(
                "Diário",
                "Semanal",
                "Mensal",
                "Anual",
            ),
            PublicRankingCycleDto.values().map { cycle ->
                cycle.publicLabel()
            },
        )
    }

    @Test
    fun entry_title_never_renders_technical_competitor_id() {
        val entry = entry(
            rank = 7,
            competitorId = "competitor-secret",
            displayName = null,
        )

        assertEquals(
            "7º · Jogador 7",
            entry.publicTitle(),
        )
        assertFalse(
            entry.publicTitle().contains(
                entry.competitorId,
            ),
        )
    }

    @Test
    fun public_name_and_exact_score_fraction_are_preserved() {
        val entry = entry(
            rank = 1,
            competitorId = "competitor-opaque",
            displayName = "Antônio Filho",
        )

        assertEquals(
            "1º · Antônio Filho",
            entry.publicTitle(),
        )
        assertEquals(
            "2/3",
            entry.publicScoreText(),
        )
    }

    @Test
    fun failure_messages_do_not_promise_ranked_participation() {
        val messages = OnlinePublicRankingFailureKind.values().map {
                failure ->
            failure.publicMessage()
        }

        assertTrue(
            messages.all { message ->
                message.isNotBlank()
            },
        )
        assertFalse(
            messages.any { message ->
                message.contains(
                    "partida",
                    ignoreCase = true,
                )
            },
        )
    }

    private fun entry(
        rank: Int,
        competitorId: String,
        displayName: String?,
    ): PublicRankingEntryDto {
        return PublicRankingEntryDto(
            rank = rank,
            competitorId = competitorId,
            displayName = displayName,
            victories = 1,
            games = 1,
            scoreNumerator = 2,
            scoreDenominator = 3,
            teamBalance = 6,
            individualPoints = 4,
            touchesGiven = 2,
            automaticRounds = 0,
        )
    }
}
