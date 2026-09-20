package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V1
import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V2
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchPlayerResult
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.competitive.createRankedMatchResultId
import com.ahtohiofilho.dominopernambucano.competitive.resolveRankingCycle
import java.io.File
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineServerRankedCycleLadderQueryTest {
    @Test
    fun in_memory_store_derives_cycle_ladder_from_persisted_results() {
        val day = epochMillis(
            year = 2026,
            month = 7,
            day = 25,
            hour = 12,
        )
        val included = result(
            matchId = "included",
            completedAtEpochMillis = day,
        )
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )
        val excluded = result(
            matchId = "excluded",
            completedAtEpochMillis = period.endsAtEpochMillis,
        )
        val store = InMemoryOnlineServerStore()

        store.restorePersistentState(
            OnlineServerStoreState(
                rankedResults = listOf(
                    included,
                    excluded,
                ),
            ),
        )

        val authoritativeStore: OnlineServerStore = store
        val ladder = authoritativeStore.getRankedCycleLadder(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )

        assertEquals(period, ladder.period)
        assertEquals(1, ladder.resultCount)
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
        assertTrue(
            ladder.standings.all { standing ->
                standing.stats.games == 1L
            },
        )
    }

    @Test
    fun current_v2_ladder_starts_fresh_while_v1_results_remain_preserved() {
        val day = epochMillis(
            year = 2026,
            month = 9,
            day = 20,
            hour = 12,
        )
        val legacyV1 = result(
            matchId = "legacy-v1",
            completedAtEpochMillis = day,
            rankingRuleVersion = RANKING_RULE_VERSION_V1,
        )
        val freshV2 = result(
            matchId = "fresh-v2",
            completedAtEpochMillis = day,
            rankingRuleVersion = RANKING_RULE_VERSION_V2,
        )
        val store = InMemoryOnlineServerStore()

        store.restorePersistentState(
            OnlineServerStoreState(
                rankedResults = listOf(
                    legacyV1,
                    freshV2,
                ),
            ),
        )

        val authoritativeStore: OnlineServerStore = store
        val currentLadder = authoritativeStore.getRankedCycleLadder(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )
        val legacyLadder = authoritativeStore.getRankedCycleLadder(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
            rankingRuleVersion = RANKING_RULE_VERSION_V1,
        )

        assertEquals(
            RANKING_RULE_VERSION_V2,
            CURRENT_RANKING_RULE_VERSION,
        )
        assertEquals(
            RANKING_RULE_VERSION_V2,
            currentLadder.period.rankingRuleVersion,
        )
        assertEquals(1, currentLadder.resultCount)

        assertEquals(
            RANKING_RULE_VERSION_V1,
            legacyLadder.period.rankingRuleVersion,
        )
        assertEquals(1, legacyLadder.resultCount)

        assertEquals(
            setOf(
                createRankedMatchResultId("legacy-v1"),
                createRankedMatchResultId("fresh-v2"),
            ),
            store.snapshotPersistentState()
                .rankedResults
                .map { rankedResult -> rankedResult.resultId }
                .toSet(),
        )
    }
    @Test
    fun persistent_query_is_read_only_and_matches_in_memory_semantics() {
        val root = temporaryRoot()
        val stateFile = File(root, "authoritative-state.json")
        val day = epochMillis(
            year = 2026,
            month = 7,
            day = 25,
            hour = 18,
        )
        val state = OnlineServerStoreState(
            rankedResults = listOf(
                result(
                    matchId = "persisted",
                    completedAtEpochMillis = day,
                ),
                result(
                    matchId = "other-rule",
                    completedAtEpochMillis = day,
                    rankingRuleVersion =
                        CURRENT_RANKING_RULE_VERSION + 1,
                ),
            ),
        )
        val json = Json {
            encodeDefaults = true
        }

        try {
            root.mkdirs()
            stateFile.writeText(
                json.encodeToString(state),
                Charsets.UTF_8,
            )
            val stateBytesBeforeQuery = stateFile.readBytes()
            val store: OnlineServerStore =
                PersistentOnlineServerStore.open(
                    stateFile = stateFile,
                )

            try {
                val ladder = store.getRankedCycleLadder(
                    kind = RankingCycleKind.DAILY,
                    completedAtEpochMillis = day,
                )

                assertEquals(1, ladder.resultCount)
                assertEquals(
                    "account-a",
                    ladder.standings.first().accountId,
                )
                assertTrue(
                    stateFile.readBytes().contentEquals(
                        stateBytesBeforeQuery,
                    ),
                )
            } finally {
                store.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun result(
        matchId: String,
        completedAtEpochMillis: Long,
        rankingRuleVersion: Int = CURRENT_RANKING_RULE_VERSION,
    ): RankedMatchResult {
        return RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = rankingRuleVersion,
            completedAtEpochMillis = completedAtEpochMillis,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(1, 1),
            completedRounds = 1,
            players = listOf(
                player(0, "account-a", true, 6, 4, 2, 0),
                player(1, "account-b", false, -3, 3, 1, 0),
                player(2, "account-c", true, 6, 1, 0, 0),
                player(3, "account-d", false, -3, 0, 1, 1),
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
        hour: Int,
    ): Long {
        return GregorianCalendar(
            TimeZone.getTimeZone("America/Recife"),
            Locale.ROOT,
        ).apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis
    }

    private fun temporaryRoot(): File {
        return File(
            System.getProperty("java.io.tmpdir"),
            "domino-ranking-query-${System.nanoTime()}",
        )
    }
}
