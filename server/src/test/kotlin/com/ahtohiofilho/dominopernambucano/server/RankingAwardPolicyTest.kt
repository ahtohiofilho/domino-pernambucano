package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.PublicRankingAwardTierDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankingAwardPolicyTest {
    @Test
    fun open_cycle_never_materializes_provisional_awards() {
        val decision = RankingAwardPolicy.evaluate(
            isClosed = false,
            publicationDecision = publishedDecision(),
            retainedRankingSize = 100,
        )

        assertEquals(
            CURRENT_RANKING_AWARD_RULE_VERSION,
            decision.awardRuleVersion,
        )
        assertEquals(0, decision.awardedRankingSize)
        assertNull(decision.tierFor(1))
    }

    @Test
    fun closed_cycle_below_threshold_has_no_awards() {
        val decision = RankingAwardPolicy.evaluate(
            isClosed = true,
            publicationDecision = RankingPublicationDecision(
                publicationThreshold = 100,
                totalEligiblePlayers = 99,
            ),
            retainedRankingSize = 99,
        )

        assertEquals(0, decision.awardedRankingSize)
        assertNull(decision.tierFor(1))
    }

    @Test
    fun closed_published_cycle_uses_canonical_tiers() {
        val decision = RankingAwardPolicy.evaluate(
            isClosed = true,
            publicationDecision = publishedDecision(),
            retainedRankingSize = 100,
        )

        assertEquals(50, decision.awardedRankingSize)
        assertEquals(
            PublicRankingAwardTierDto.DIAMOND,
            decision.tierFor(1),
        )
        assertEquals(
            PublicRankingAwardTierDto.GOLD,
            decision.tierFor(5),
        )
        assertEquals(
            PublicRankingAwardTierDto.SILVER,
            decision.tierFor(10),
        )
        assertEquals(
            PublicRankingAwardTierDto.BRONZE,
            decision.tierFor(50),
        )
        assertNull(decision.tierFor(51))
    }

    @Test
    fun version_one_tier_boundaries_are_historical_contract() {
        val decision = RankingAwardDecision(
            awardRuleVersion = RANKING_AWARD_RULE_VERSION_V1,
            awardedRankingSize = 50,
        )

        assertEquals(
            PublicRankingAwardTierDto.DIAMOND,
            decision.tierFor(1),
        )
        assertEquals(
            PublicRankingAwardTierDto.GOLD,
            decision.tierFor(2),
        )
        assertEquals(
            PublicRankingAwardTierDto.GOLD,
            decision.tierFor(5),
        )
        assertEquals(
            PublicRankingAwardTierDto.SILVER,
            decision.tierFor(6),
        )
        assertEquals(
            PublicRankingAwardTierDto.SILVER,
            decision.tierFor(10),
        )
        assertEquals(
            PublicRankingAwardTierDto.BRONZE,
            decision.tierFor(11),
        )
        assertEquals(
            PublicRankingAwardTierDto.BRONZE,
            decision.tierFor(50),
        )
        assertNull(decision.tierFor(51))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupported_award_rule_version_is_rejected() {
        RankingAwardDecision(
            awardRuleVersion = 999,
            awardedRankingSize = 1,
        )
    }

    @Test
    fun retained_ranking_bounds_awarded_size() {
        val decision = RankingAwardPolicy.evaluate(
            isClosed = true,
            publicationDecision = publishedDecision(),
            retainedRankingSize = 7,
        )

        assertEquals(7, decision.awardedRankingSize)
        assertEquals(
            PublicRankingAwardTierDto.SILVER,
            decision.tierFor(7),
        )
        assertNull(decision.tierFor(8))
    }

    private fun publishedDecision(): RankingPublicationDecision {
        return RankingPublicationDecision(
            publicationThreshold = 100,
            totalEligiblePlayers = 100,
        )
    }
}
