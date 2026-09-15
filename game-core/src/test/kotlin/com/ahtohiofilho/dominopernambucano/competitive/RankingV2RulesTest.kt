package com.ahtohiofilho.dominopernambucano.competitive

import org.junit.Assert.assertEquals
import org.junit.Test

class RankingV2RulesTest {
    @Test
    fun v2_uses_the_frozen_six_criterion_order() {
        val common = RankedLadderStats(
            victories = 6,
            games = 10,
            teamBalance = 10,
            individualPoints = 20,
            assists = 5,
            touchesGiven = 4,
            automaticPlays = 2,
        )
        val entries = rankEligibleEntries(
            entries = listOf(
                entry("technical-b", common),
                entry(
                    "automatic",
                    common.copy(automaticPlays = 1),
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
            rankingRuleVersion = RANKING_RULE_VERSION_V2,
        )

        assertEquals(
            listOf(
                "victory-balance",
                "point-balance",
                "points",
                "assists",
                "touches",
                "automatic",
                "technical-a",
                "technical-b",
            ),
            entries.map { entry -> entry.technicalId },
        )
    }

    @Test
    fun v2_assigns_shared_public_ranks_after_all_six_criteria_tie() {
        val completedAtEpochMillis = 1_800_000_000_000L
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = completedAtEpochMillis,
            rankingRuleVersion = RANKING_RULE_VERSION_V2,
        )
        val result = rankedResult(
            matchId = "v2-shared-rank",
            completedAtEpochMillis = completedAtEpochMillis,
            rankingRuleVersion = RANKING_RULE_VERSION_V2,
        )

        val ladder = buildRankedCycleLadder(
            period = period,
            results = listOf(result),
        )

        assertEquals(
            listOf(
                "account-a",
                "account-c",
                "account-b",
                "account-d",
            ),
            ladder.standings.map { standing ->
                standing.accountId
            },
        )
        assertEquals(
            listOf(1, 1, 3, 3),
            ladder.standings.map { standing ->
                standing.rank
            },
        )
    }

    @Test
    fun v1_keeps_unique_public_ranks_for_legacy_history() {
        val completedAtEpochMillis = 1_800_000_000_000L
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = completedAtEpochMillis,
            rankingRuleVersion = RANKING_RULE_VERSION_V1,
        )
        val result = rankedResult(
            matchId = "v1-legacy-rank",
            completedAtEpochMillis = completedAtEpochMillis,
            rankingRuleVersion = RANKING_RULE_VERSION_V1,
        )

        val ladder = buildRankedCycleLadder(
            period = period,
            results = listOf(result),
        )

        assertEquals(
            listOf(1, 2, 3, 4),
            ladder.standings.map { standing ->
                standing.rank
            },
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

    private fun rankedResult(
        matchId: String,
        completedAtEpochMillis: Long,
        rankingRuleVersion: Int,
    ): RankedMatchResult {
        return RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = rankingRuleVersion,
            completedAtEpochMillis = completedAtEpochMillis,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(0, 0),
            completedRounds = 1,
            players = listOf(
                player(
                    seatIndex = 0,
                    accountId = "account-a",
                    won = true,
                    teamBalanceDelta = 3,
                    individualPointsScored = 2,
                    touchesGiven = 1,
                ),
                player(
                    seatIndex = 1,
                    accountId = "account-b",
                    won = false,
                    teamBalanceDelta = -3,
                    individualPointsScored = 0,
                    touchesGiven = 0,
                ),
                player(
                    seatIndex = 2,
                    accountId = "account-c",
                    won = true,
                    teamBalanceDelta = 3,
                    individualPointsScored = 2,
                    touchesGiven = 1,
                ),
                player(
                    seatIndex = 3,
                    accountId = "account-d",
                    won = false,
                    teamBalanceDelta = -3,
                    individualPointsScored = 0,
                    touchesGiven = 0,
                ),
            ),
        )
    }

    private fun player(
        seatIndex: Int,
        accountId: String,
        won: Boolean,
        teamBalanceDelta: Int,
        individualPointsScored: Int,
        touchesGiven: Int,
    ): RankedMatchPlayerResult {
        return RankedMatchPlayerResult(
            playerId = "player-$seatIndex",
            accountId = accountId,
            seatIndex = seatIndex,
            teamIndex = seatIndex % 2,
            won = won,
            victoriesDelta = if (won) 1 else 0,
            gamesDelta = 1,
            teamBalanceDelta = teamBalanceDelta,
            individualPointsScored = individualPointsScored,
            touchesGiven = touchesGiven,
            automaticRounds = 0,
        )
    }
}
