package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V1
import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V2
import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V3
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankingV2RemoteContractTest {
    @Test
    fun v1_keeps_strict_sequential_public_ranks() {
        assertTrue(
            areValidPublicRankingPageRanks(
                rankingRuleVersion = RANKING_RULE_VERSION_V1,
                offset = 0,
                ranks = listOf(1, 2, 3),
            ),
        )
        assertFalse(
            areValidPublicRankingPageRanks(
                rankingRuleVersion = RANKING_RULE_VERSION_V1,
                offset = 0,
                ranks = listOf(1, 1, 3),
            ),
        )
    }

    @Test
    fun v2_accepts_competition_ranking_with_shared_positions() {
        assertTrue(
            areValidPublicRankingPageRanks(
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
                offset = 0,
                ranks = listOf(1, 1, 3, 4, 4, 6),
            ),
        )
    }

    @Test
    fun v2_page_can_start_inside_a_tie_and_leave_it_at_global_position() {
        assertTrue(
            areValidPublicRankingPageRanks(
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
                offset = 50,
                ranks = listOf(50, 52, 53),
            ),
        )
    }

    @Test
    fun v2_rejects_non_competition_rank_sequences() {
        assertFalse(
            areValidPublicRankingPageRanks(
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
                offset = 0,
                ranks = listOf(1, 1, 2),
            ),
        )
        assertFalse(
            areValidPublicRankingPageRanks(
                rankingRuleVersion = RANKING_RULE_VERSION_V2,
                offset = 0,
                ranks = listOf(1, 3),
            ),
        )
    }

    @Test
    fun legacy_public_entry_payload_defaults_new_metrics_to_zero() {
        val entry = Json.decodeFromString<PublicRankingEntryDto>(
            """
            {
              "rank": 1,
              "competitorId": "competitor-legacy",
              "victories": 2,
              "games": 3,
              "scoreNumerator": 3,
              "scoreDenominator": 5,
              "teamBalance": 4,
              "individualPoints": 7,
              "touchesGiven": 2,
              "automaticRounds": 1
            }
            """.trimIndent(),
        )

        assertEquals(0L, entry.assists)
        assertEquals(0L, entry.automaticPlays)
        assertEquals(0L, entry.timeoutRounds)
    }

    @Test
    fun v1_v2_and_v3_are_supported_by_this_contract() {
        assertTrue(isSupportedPublicRankingRuleVersion(RANKING_RULE_VERSION_V1))
        assertTrue(isSupportedPublicRankingRuleVersion(RANKING_RULE_VERSION_V2))
        assertTrue(isSupportedPublicRankingRuleVersion(RANKING_RULE_VERSION_V3))
        assertFalse(isSupportedPublicRankingRuleVersion(4))
        assertFalse(isSupportedPublicRankingRuleVersion(0))
    }
}