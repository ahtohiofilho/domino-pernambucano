package com.ahtohiofilho.dominopernambucano.competitive

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedCycleLadderTest {
    @Test
    fun aggregates_matching_results_and_reuses_canonical_order() {
        val period = dailyPeriod(
            year = 2026,
            month = 7,
            day = 25,
        )
        val results = listOf(
            result(
                matchId = "match-1",
                completedAtEpochMillis = epochMillis(
                    year = 2026,
                    month = 7,
                    day = 25,
                    hour = 10,
                ),
                players = listOf(
                    player(0, "account-a", true, 6, 4, 2, 0),
                    player(1, "account-b", false, -3, 3, 1, 0),
                    player(2, "account-c", true, 6, 1, 0, 0),
                    player(3, "account-d", false, -3, 0, 0, 1),
                ),
            ),
            result(
                matchId = "match-2",
                completedAtEpochMillis = epochMillis(
                    year = 2026,
                    month = 7,
                    day = 25,
                    hour = 20,
                ),
                players = listOf(
                    player(0, "account-a", false, -4, 2, 0, 1),
                    player(1, "account-b", true, 6, 5, 3, 0),
                    player(2, "account-c", false, -4, 0, 0, 0),
                    player(3, "account-d", true, 6, 1, 0, 0),
                ),
            ),
        )

        val ladder = buildRankedCycleLadder(
            period = period,
            results = results,
        )

        assertEquals(2, ladder.resultCount)
        assertEquals(
            listOf(
                "account-b",
                "account-d",
                "account-a",
                "account-c",
            ),
            ladder.standings.map { standing ->
                standing.accountId
            },
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            ladder.standings.map { standing ->
                standing.rank
            },
        )

        val accountB = ladder.standings.first()
        assertEquals(1L, accountB.stats.victories)
        assertEquals(2L, accountB.stats.games)
        assertEquals(3L, accountB.stats.teamBalance)
        assertEquals(8L, accountB.stats.individualPoints)
        assertEquals(4L, accountB.stats.touchesGiven)
        assertEquals(0L, accountB.stats.automaticRounds)
    }

    @Test
    fun excludes_other_periods_and_other_rule_versions() {
        val period = dailyPeriod(
            year = 2026,
            month = 7,
            day = 25,
        )
        val included = result(
            matchId = "included",
            completedAtEpochMillis = epochMillis(
                year = 2026,
                month = 7,
                day = 25,
                hour = 12,
            ),
        )
        val nextDay = result(
            matchId = "next-day",
            completedAtEpochMillis = period.endsAtEpochMillis,
        )
        val otherVersion = result(
            matchId = "other-version",
            completedAtEpochMillis = included.completedAtEpochMillis,
            rankingRuleVersion = 2,
        )

        val ladder = buildRankedCycleLadder(
            period = period,
            results = listOf(
                included,
                nextDay,
                otherVersion,
            ),
        )

        assertEquals(1, ladder.resultCount)
        assertEquals(
            included.players.map { player ->
                requireNotNull(player.accountId)
            }.sorted(),
            ladder.standings.map { standing ->
                standing.accountId
            }.sorted(),
        )
        assertTrue(
            ladder.standings.all { standing ->
                standing.stats.games == 1L
            },
        )
    }

    @Test
    fun excludes_selected_accounts_without_discarding_mixed_result() {
        val period = dailyPeriod(
            year = 2026,
            month = 7,
            day = 25,
        )
        val mixed = result(
            matchId = "mixed",
            completedAtEpochMillis = epochMillis(
                year = 2026,
                month = 7,
                day = 25,
                hour = 12,
            ),
            players = listOf(
                player(0, "account-a", true, 6, 4, 2, 0),
                player(1, "account-s", false, -3, 3, 1, 0),
                player(2, "account-c", true, 6, 1, 0, 0),
                player(3, "account-t", false, -3, 0, 0, 0),
            ),
        )
        val syntheticOnly = result(
            matchId = "synthetic-only",
            completedAtEpochMillis = mixed.completedAtEpochMillis + 1L,
            players = listOf(
                player(0, "account-s", true, 6, 4, 2, 0),
                player(1, "account-t", false, -3, 3, 1, 0),
                player(2, "account-u", true, 6, 1, 0, 0),
                player(3, "account-v", false, -3, 0, 0, 0),
            ),
        )

        val ladder = buildRankedCycleLadder(
            period = period,
            results = listOf(
                mixed,
                syntheticOnly,
            ),
            excludedAccountIds = setOf(
                "account-s",
                "account-t",
                "account-u",
                "account-v",
            ),
        )

        assertEquals(1, ladder.resultCount)
        assertEquals(
            setOf(
                "account-a",
                "account-c",
            ),
            ladder.standings
                .map { standing -> standing.accountId }
                .toSet(),
        )
        assertTrue(
            ladder.standings.all { standing ->
                standing.stats.games == 1L
            },
        )
    }

    @Test
    fun rejects_duplicate_result_ids_before_accumulating() {
        val period = dailyPeriod(
            year = 2026,
            month = 7,
            day = 25,
        )
        val duplicate = result(
            matchId = "duplicate",
            completedAtEpochMillis = epochMillis(
                year = 2026,
                month = 7,
                day = 25,
            ),
        )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            buildRankedCycleLadder(
                period = period,
                results = listOf(
                    duplicate,
                    duplicate,
                ),
            )
        }
    }

    @Test
    fun rejects_matching_ranked_result_without_canonical_account() {
        val period = dailyPeriod(
            year = 2026,
            month = 7,
            day = 25,
        )
        val invalid = result(
            matchId = "missing-account",
            completedAtEpochMillis = epochMillis(
                year = 2026,
                month = 7,
                day = 25,
            ),
            players = listOf(
                player(0, null, true, 6, 4, 2, 0),
                player(1, "account-b", false, -3, 3, 1, 0),
                player(2, "account-c", true, 6, 1, 0, 0),
                player(3, "account-d", false, -3, 0, 0, 0),
            ),
        )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            buildRankedCycleLadder(
                period = period,
                results = listOf(invalid),
            )
        }
    }

    private fun dailyPeriod(
        year: Int,
        month: Int,
        day: Int,
    ): RankedCyclePeriod {
        return resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = epochMillis(
                year = year,
                month = month,
                day = day,
                hour = 12,
            ),
        )
    }

    private fun result(
        matchId: String,
        completedAtEpochMillis: Long,
        rankingRuleVersion: Int = CURRENT_RANKING_RULE_VERSION,
        players: List<RankedMatchPlayerResult> = listOf(
            player(0, "account-a", true, 6, 4, 2, 0),
            player(1, "account-b", false, -3, 3, 1, 0),
            player(2, "account-c", true, 6, 1, 0, 0),
            player(3, "account-d", false, -3, 0, 0, 0),
        ),
    ): RankedMatchResult {
        return RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = rankingRuleVersion,
            completedAtEpochMillis = completedAtEpochMillis,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(1, 1),
            completedRounds = 1,
            players = players,
        )
    }

    private fun player(
        seatIndex: Int,
        accountId: String?,
        won: Boolean,
        teamBalanceDelta: Int,
        individualPointsScored: Int,
        touchesGiven: Int,
        automaticRounds: Int,
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
            automaticRounds = automaticRounds,
        )
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
    ): Long {
        return GregorianCalendar(
            TimeZone.getTimeZone(
                CANONICAL_RANKING_TIME_ZONE_ID,
            ),
            Locale.ROOT,
        ).apply {
            isLenient = false
            clear()
            set(
                Calendar.YEAR,
                year,
            )
            set(
                Calendar.MONTH,
                month - 1,
            )
            set(
                Calendar.DAY_OF_MONTH,
                day,
            )
            set(
                Calendar.HOUR_OF_DAY,
                hour,
            )
        }.timeInMillis
    }
}
