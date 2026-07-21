package com.ahtohiofilho.dominopernambucano.competitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedLadderRulesTest {
    @Test
    fun prior_centers_a_new_profile_but_does_not_make_it_eligible() {
        val stats = RankedLadderStats()

        assertEquals(1L, stats.scoreNumerator)
        assertEquals(2L, stats.scoreDenominator)
        assertFalse(stats.isEligible)
    }

    @Test
    fun score_distinguishes_perfect_and_winless_campaigns_by_volume() {
        val entries = rankEligibleEntries(
            listOf(
                entry("perfect-short", victories = 1, games = 1),
                entry("perfect-long", victories = 10, games = 10),
                entry("winless-short", victories = 0, games = 1),
                entry("winless-long", victories = 0, games = 10),
            ),
        )

        assertEquals(
            listOf(
                "perfect-long",
                "perfect-short",
                "winless-short",
                "winless-long",
            ),
            entries.map { entry -> entry.technicalId },
        )
    }

    @Test
    fun tie_breakers_follow_the_canonical_order() {
        val common = RankedLadderStats(
            victories = 4,
            games = 8,
        )

        val entries = rankEligibleEntries(
            listOf(
                RankedLadderEntry(
                    technicalId = "technical-b",
                    stats = common.copy(
                        teamBalance = 6,
                        individualPoints = 4,
                        touchesGiven = 3,
                        automaticRounds = 1,
                    ),
                ),
                RankedLadderEntry(
                    technicalId = "balance",
                    stats = common.copy(teamBalance = 7),
                ),
                RankedLadderEntry(
                    technicalId = "points",
                    stats = common.copy(
                        teamBalance = 6,
                        individualPoints = 5,
                    ),
                ),
                RankedLadderEntry(
                    technicalId = "touches",
                    stats = common.copy(
                        teamBalance = 6,
                        individualPoints = 4,
                        touchesGiven = 4,
                    ),
                ),
                RankedLadderEntry(
                    technicalId = "stability",
                    stats = common.copy(
                        teamBalance = 6,
                        individualPoints = 4,
                        touchesGiven = 3,
                        automaticRounds = 0,
                    ),
                ),
                RankedLadderEntry(
                    technicalId = "technical-a",
                    stats = common.copy(
                        teamBalance = 6,
                        individualPoints = 4,
                        touchesGiven = 3,
                        automaticRounds = 1,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "balance",
                "points",
                "touches",
                "stability",
                "technical-a",
                "technical-b",
            ),
            entries.map { entry -> entry.technicalId },
        )
    }

    @Test
    fun only_profiles_with_a_completed_game_are_ranked() {
        val entries = rankEligibleEntries(
            listOf(
                RankedLadderEntry(
                    technicalId = "inactive",
                    stats = RankedLadderStats(),
                ),
                entry("active", victories = 0, games = 1),
            ),
        )

        assertEquals(listOf("active"), entries.map { it.technicalId })
        assertTrue(entries.single().stats.isEligible)
    }

    private fun entry(
        technicalId: String,
        victories: Long,
        games: Long,
    ): RankedLadderEntry {
        return RankedLadderEntry(
            technicalId = technicalId,
            stats = RankedLadderStats(
                victories = victories,
                games = games,
            ),
        )
    }
}
