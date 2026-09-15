package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V1
import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V2
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleStanding
import com.ahtohiofilho.dominopernambucano.competitive.RankedLadderStats
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.competitive.resolveRankingCycle
import com.ahtohiofilho.dominopernambucano.online.PublicRankingAwardTierDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankingV2PublicContractTest {
    @Test
    fun v2_closed_snapshot_accepts_and_preserves_shared_public_ranks() {
        val period = period(RANKING_RULE_VERSION_V2)
        val snapshot = RankedCycleSnapshot(
            period = period,
            resultCount = 2,
            closedAtEpochMillis = period.endsAtEpochMillis,
            standings = listOf(
                snapshotStanding(
                    rank = 1,
                    accountId = "account-a",
                    assists = 4,
                    automaticPlays = 1,
                ),
                snapshotStanding(
                    rank = 1,
                    accountId = "account-b",
                    assists = 4,
                    automaticPlays = 1,
                ),
                snapshotStanding(
                    rank = 3,
                    accountId = "account-c",
                    assists = 2,
                    automaticPlays = 0,
                ),
            ),
        )

        val ladder = snapshot.toRankedCycleLadder()

        assertEquals(listOf(1, 1, 3), ladder.standings.map { it.rank })
        assertEquals(4L, ladder.standings[0].stats.assists)
        assertEquals(1L, ladder.standings[0].stats.automaticPlays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun v1_closed_snapshot_still_rejects_shared_public_ranks() {
        val period = period(RANKING_RULE_VERSION_V1)

        RankedCycleSnapshot(
            period = period,
            resultCount = 1,
            closedAtEpochMillis = period.endsAtEpochMillis,
            standings = listOf(
                snapshotStanding(1, "account-a"),
                snapshotStanding(1, "account-b"),
            ),
        )
    }

    @Test
    fun legacy_snapshot_standing_defaults_v2_metrics_to_zero() {
        val standing = Json.decodeFromString<RankedCycleStandingSnapshot>(
            """
            {
              "rank": 1,
              "accountId": "legacy-account",
              "victories": 1,
              "games": 1,
              "teamBalance": 3,
              "individualPoints": 2,
              "touchesGiven": 1,
              "automaticRounds": 0
            }
            """.trimIndent(),
        )

        assertEquals(0L, standing.assists)
        assertEquals(0L, standing.automaticPlays)
    }

    @Test
    fun v2_revision_changes_with_assists_and_automatic_plays() {
        val base = ladder(
            ruleVersion = RANKING_RULE_VERSION_V2,
            assists = 1,
            automaticPlays = 1,
        )
        val changedAssist = ladder(
            ruleVersion = RANKING_RULE_VERSION_V2,
            assists = 2,
            automaticPlays = 1,
        )
        val changedAutomatic = ladder(
            ruleVersion = RANKING_RULE_VERSION_V2,
            assists = 1,
            automaticPlays = 2,
        )

        assertNotEquals(
            base.publicRankingRevision(),
            changedAssist.publicRankingRevision(),
        )
        assertNotEquals(
            base.publicRankingRevision(),
            changedAutomatic.publicRankingRevision(),
        )
    }

    @Test
    fun v1_revision_remains_independent_of_v2_only_metrics() {
        val base = ladder(
            ruleVersion = RANKING_RULE_VERSION_V1,
            assists = 0,
            automaticPlays = 0,
        )
        val changed = ladder(
            ruleVersion = RANKING_RULE_VERSION_V1,
            assists = 99,
            automaticPlays = 99,
        )

        assertEquals(
            base.publicRankingRevision(),
            changed.publicRankingRevision(),
        )
    }

    @Test
    fun award_cutoff_is_public_rank_based_so_rank_50_ties_share_bronze() {
        val decision = RankingAwardDecision(
            awardRuleVersion = CURRENT_RANKING_AWARD_RULE_VERSION,
            awardedRankingSize = 50,
        )

        assertEquals(
            PublicRankingAwardTierDto.BRONZE,
            decision.tierFor(50),
        )
        assertEquals(
            PublicRankingAwardTierDto.BRONZE,
            decision.tierFor(50),
        )
        assertNull(decision.tierFor(51))
    }

    private fun period(ruleVersion: Int) =
        resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = 1_800_000_000_000L,
            rankingRuleVersion = ruleVersion,
        )

    private fun snapshotStanding(
        rank: Int,
        accountId: String,
        assists: Long = 0L,
        automaticPlays: Long = 0L,
    ) = RankedCycleStandingSnapshot(
        rank = rank,
        accountId = accountId,
        victories = 2,
        games = 3,
        teamBalance = 4,
        individualPoints = 7,
        touchesGiven = 2,
        automaticRounds = 1,
        assists = assists,
        automaticPlays = automaticPlays,
    )

    private fun ladder(
        ruleVersion: Int,
        assists: Long,
        automaticPlays: Long,
    ): RankedCycleLadder {
        return RankedCycleLadder(
            period = period(ruleVersion),
            resultCount = 1,
            standings = listOf(
                RankedCycleStanding(
                    rank = 1,
                    accountId = "account-a",
                    stats = RankedLadderStats(
                        victories = 2,
                        games = 3,
                        teamBalance = 4,
                        individualPoints = 7,
                        assists = assists,
                        touchesGiven = 2,
                        automaticRounds = 1,
                        automaticPlays = automaticPlays,
                    ),
                ),
            ),
        )
    }
}