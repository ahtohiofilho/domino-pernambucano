package com.ahtohiofilho.dominopernambucano.competitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingV3RulesTest {
    @Test
    fun v3_uses_timeout_rounds_as_the_last_sporting_tiebreak() {
        val common = RankedLadderStats(
            victories = 6,
            games = 10,
            teamBalance = 10,
            individualPoints = 20,
            assists = 5,
            touchesGiven = 4,
            automaticPlays = 0,
            timeoutRounds = 2,
        )
        val entries = rankEligibleEntries(
            entries = listOf(
                entry("technical-b", common),
                entry(
                    "timeout",
                    common.copy(timeoutRounds = 1),
                ),
                entry(
                    "touches",
                    common.copy(touchesGiven = 5),
                ),
                entry(
                    "assists",
                    common.copy(assists = 6),
                ),
                entry(
                    "points",
                    common.copy(individualPoints = 21),
                ),
                entry(
                    "point-balance",
                    common.copy(teamBalance = 11),
                ),
                entry(
                    "victory-balance",
                    common.copy(victories = 7),
                ),
                entry("technical-a", common),
            ),
            rankingRuleVersion = RANKING_RULE_VERSION_V3,
        )

        assertEquals(
            listOf(
                "victory-balance",
                "point-balance",
                "points",
                "assists",
                "touches",
                "timeout",
                "technical-a",
                "technical-b",
            ),
            entries.map { entry -> entry.technicalId },
        )
    }

    @Test
    fun v2_remains_frozen_on_automatic_plays() {
        val left = entry(
            "left",
            RankedLadderStats(
                victories = 6,
                games = 10,
                teamBalance = 10,
                individualPoints = 20,
                assists = 5,
                touchesGiven = 4,
                automaticPlays = 1,
                timeoutRounds = 99,
            ),
        )
        val right = entry(
            "right",
            left.stats.copy(
                automaticPlays = 2,
                timeoutRounds = 0,
            ),
        )

        val ranked = rankEligibleEntries(
            entries = listOf(right, left),
            rankingRuleVersion = RANKING_RULE_VERSION_V2,
        )

        assertEquals(
            listOf("left", "right"),
            ranked.map { entry -> entry.technicalId },
        )
    }

    @Test
    fun v3_public_tie_ignores_legacy_automatic_play_telemetry() {
        val left = entry(
            "left",
            RankedLadderStats(
                victories = 6,
                games = 10,
                teamBalance = 10,
                individualPoints = 20,
                assists = 5,
                touchesGiven = 4,
                automaticPlays = 1,
                timeoutRounds = 2,
            ),
        )
        val right = entry(
            "right",
            left.stats.copy(
                automaticPlays = 999,
            ),
        )

        assertTrue(
            areRankedLadderEntriesPubliclyTied(
                left = left,
                right = right,
                rankingRuleVersion = RANKING_RULE_VERSION_V3,
            ),
        )
    }

    @Test
    fun v3_is_current_after_coordinated_activation() {
        assertEquals(
            RANKING_RULE_VERSION_V3,
            CURRENT_RANKING_RULE_VERSION,
        )
    }

    private fun entry(
        technicalId: String,
        stats: RankedLadderStats,
    ): RankedLadderEntry {
        return RankedLadderEntry(
            technicalId = technicalId,
            stats = stats,
        )
    }
}