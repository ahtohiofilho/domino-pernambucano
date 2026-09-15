package com.ahtohiofilho.dominopernambucano.ui.ranking

import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankingV2UiPresentationTest {
    @Test
    fun victory_balance_is_wins_minus_losses() {
        val entry = entry(
            victories = 7,
            games = 10,
        )

        assertEquals(4L, entry.publicVictoryBalance())
        assertEquals(3L, entry.publicDefeats())
    }

    @Test
    fun assists_are_the_first_hidden_tiebreak() {
        val previous = entry(
            assists = 5,
            touches = 2,
            automaticPlays = 4,
        )
        val current = entry(
            competitorId = "competitor-b",
            assists = 4,
            touches = 99,
            automaticPlays = 0,
        )

        assertEquals(
            PublicRankingV2TiebreakCriterion.ASSISTS,
            publicV2TiebreakCriterion(previous, current),
        )
    }

    @Test
    fun touches_decide_only_after_primary_metrics_and_assists_tie() {
        val previous = entry(
            assists = 5,
            touches = 7,
            automaticPlays = 4,
        )
        val current = entry(
            competitorId = "competitor-b",
            assists = 5,
            touches = 6,
            automaticPlays = 0,
        )

        assertEquals(
            PublicRankingV2TiebreakCriterion.TOUCHES,
            publicV2TiebreakCriterion(previous, current),
        )
    }

    @Test
    fun fewer_automatic_plays_is_the_last_sporting_tiebreak() {
        val previous = entry(
            assists = 5,
            touches = 7,
            automaticPlays = 1,
        )
        val current = entry(
            competitorId = "competitor-b",
            assists = 5,
            touches = 7,
            automaticPlays = 2,
        )

        assertEquals(
            PublicRankingV2TiebreakCriterion.AUTOMATIC_PLAYS,
            publicV2TiebreakCriterion(previous, current),
        )
    }

    @Test
    fun primary_metric_difference_does_not_show_hidden_tiebreak_note() {
        val previous = entry(
            teamBalance = 9,
            assists = 5,
        )
        val current = entry(
            competitorId = "competitor-b",
            teamBalance = 8,
            assists = 1,
        )

        assertNull(
            publicV2TiebreakCriterion(previous, current),
        )
    }

    @Test
    fun complete_sporting_tie_does_not_expose_technical_order_as_tiebreak() {
        val previous = entry()
        val current = entry(
            competitorId = "competitor-z",
        )

        assertNull(
            publicV2TiebreakCriterion(previous, current),
        )
    }

    private fun entry(
        competitorId: String = "competitor-a",
        victories: Long = 7,
        games: Long = 10,
        teamBalance: Long = 8,
        individualPoints: Long = 6,
        assists: Long = 5,
        touches: Long = 4,
        automaticPlays: Long = 3,
    ): PublicRankingEntryDto {
        return PublicRankingEntryDto(
            rank = 1,
            competitorId = competitorId,
            displayName = "Player",
            victories = victories,
            games = games,
            scoreNumerator = victories + 1,
            scoreDenominator = games + 2,
            teamBalance = teamBalance,
            individualPoints = individualPoints,
            touchesGiven = touches,
            automaticRounds = 0,
            assists = assists,
            automaticPlays = automaticPlays,
        )
    }
}