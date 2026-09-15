package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V1
import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V2
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleStanding
import com.ahtohiofilho.dominopernambucano.competitive.RankedLadderStats
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.competitive.resolveRankingCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingV2RetentionBoundaryTest {
    @Test
    fun v2_daily_top_100_keeps_the_entire_rank_100_tie_group() {
        val standings = buildList {
            (1..99).forEach { rank ->
                add(standing(rank = rank, ordinal = rank))
            }
            add(standing(rank = 100, ordinal = 100))
            add(standing(rank = 100, ordinal = 101))
            add(standing(rank = 100, ordinal = 102))
            add(standing(rank = 103, ordinal = 103))
            add(standing(rank = 104, ordinal = 104))
        }
        val ladder = ladder(
            ruleVersion = RANKING_RULE_VERSION_V2,
            standings = standings,
        )

        val snapshot = ladder.toClosedSnapshot(
            closedAtEpochMillis = ladder.period.endsAtEpochMillis,
        )

        assertEquals(104, snapshot.totalEligiblePlayers)
        assertEquals(102, snapshot.retainedRankingSize)
        assertEquals(102, snapshot.standings.size)
        assertEquals(
            listOf(100, 100, 100),
            snapshot.standings.takeLast(3).map { it.rank },
        )
        assertTrue(snapshot.isRetentionLimited)
    }

    @Test
    fun v2_boundary_also_expands_when_a_tie_started_before_row_100() {
        val standings = buildList {
            (1..98).forEach { rank ->
                add(standing(rank = rank, ordinal = rank))
            }
            add(standing(rank = 99, ordinal = 99))
            add(standing(rank = 99, ordinal = 100))
            add(standing(rank = 99, ordinal = 101))
            add(standing(rank = 102, ordinal = 102))
        }
        val ladder = ladder(
            ruleVersion = RANKING_RULE_VERSION_V2,
            standings = standings,
        )

        val snapshot = ladder.toClosedSnapshot(
            closedAtEpochMillis = ladder.period.endsAtEpochMillis,
        )

        assertEquals(102, snapshot.totalEligiblePlayers)
        assertEquals(101, snapshot.retainedRankingSize)
        assertEquals(
            listOf(99, 99, 99),
            snapshot.standings.takeLast(3).map { it.rank },
        )
    }

    @Test
    fun v1_daily_top_100_remains_a_hard_100_row_limit() {
        val standings = (1..104).map { rank ->
            standing(rank = rank, ordinal = rank)
        }
        val ladder = ladder(
            ruleVersion = RANKING_RULE_VERSION_V1,
            standings = standings,
        )

        val snapshot = ladder.toClosedSnapshot(
            closedAtEpochMillis = ladder.period.endsAtEpochMillis,
        )

        assertEquals(104, snapshot.totalEligiblePlayers)
        assertEquals(100, snapshot.retainedRankingSize)
        assertEquals(100, snapshot.standings.size)
        assertEquals(100, snapshot.standings.last().rank)
        assertTrue(snapshot.isRetentionLimited)
    }

    @Test
    fun v2_without_a_boundary_tie_still_retains_exactly_100_rows() {
        val standings = (1..104).map { rank ->
            standing(rank = rank, ordinal = rank)
        }
        val ladder = ladder(
            ruleVersion = RANKING_RULE_VERSION_V2,
            standings = standings,
        )

        val snapshot = ladder.toClosedSnapshot(
            closedAtEpochMillis = ladder.period.endsAtEpochMillis,
        )

        assertEquals(100, snapshot.retainedRankingSize)
        assertEquals(100, snapshot.standings.last().rank)
    }

    private fun ladder(
        ruleVersion: Int,
        standings: List<RankedCycleStanding>,
    ): RankedCycleLadder {
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = 1_800_000_000_000L,
            rankingRuleVersion = ruleVersion,
        )

        return RankedCycleLadder(
            period = period,
            resultCount = 1,
            standings = standings,
        )
    }

    private fun standing(
        rank: Int,
        ordinal: Int,
    ): RankedCycleStanding {
        return RankedCycleStanding(
            rank = rank,
            accountId = "account-${ordinal.toString().padStart(4, '0')}",
            stats = RankedLadderStats(
                victories = 1,
                games = 1,
                teamBalance = 1_000_000L - rank.toLong(),
                individualPoints = 1,
                assists = 0,
                touchesGiven = 0,
                automaticRounds = 0,
                automaticPlays = 0,
            ),
        )
    }
}