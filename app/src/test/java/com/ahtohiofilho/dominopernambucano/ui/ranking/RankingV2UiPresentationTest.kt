package com.ahtohiofilho.dominopernambucano.ui.ranking

import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V2
import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V3
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
            PublicRankingTiebreakCriterion.ASSISTS,
            publicRankingTiebreakCriterion(
                previous = previous,
                current = current,
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
            ),
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
            PublicRankingTiebreakCriterion.TOUCHES,
            publicRankingTiebreakCriterion(
                previous = previous,
                current = current,
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
            ),
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
            PublicRankingTiebreakCriterion.AUTOMATIC_PLAYS,
            publicRankingTiebreakCriterion(
                previous = previous,
                current = current,
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
            ),
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
            publicRankingTiebreakCriterion(
                previous = previous,
                current = current,
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
            ),
        )
    }

    @Test
    fun complete_sporting_tie_does_not_expose_technical_order_as_tiebreak() {
        val previous = entry()
        val current = entry(
            competitorId = "competitor-z",
        )

        assertNull(
            publicRankingTiebreakCriterion(
                previous = previous,
                current = current,
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
            ),
        )
    }

    @Test
    fun v3_uses_timeout_rounds_instead_of_automatic_plays() {
        val previous = entry(
            automaticPlays = 999,
            timeoutRounds = 1,
        )
        val current = entry(
            competitorId = "competitor-b",
            automaticPlays = 0,
            timeoutRounds = 2,
        )

        assertEquals(
            PublicRankingTiebreakCriterion.TIMEOUT_ROUNDS,
            publicRankingTiebreakCriterion(
                previous = previous,
                current = current,
                rankingRuleVersion = RANKING_RULE_VERSION_V3,
            ),
        )
    }

    @Test
    fun v3_ignores_automatic_play_count_when_timeout_rounds_tie() {
        val previous = entry(
            automaticPlays = 1,
            timeoutRounds = 2,
        )
        val current = entry(
            competitorId = "competitor-b",
            automaticPlays = 999,
            timeoutRounds = 2,
        )

        assertNull(
            publicRankingTiebreakCriterion(
                previous = previous,
                current = current,
                rankingRuleVersion = RANKING_RULE_VERSION_V3,
            ),
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
        timeoutRounds: Long = 0,
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
            timeoutRounds = timeoutRounds,
        )
    }
}