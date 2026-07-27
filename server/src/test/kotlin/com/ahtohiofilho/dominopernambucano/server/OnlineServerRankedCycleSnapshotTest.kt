package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineServerRankedCycleSnapshotTest {
    @Test
    fun closed_daily_cycle_is_materialized_once_and_capped() {
        val day = epochMillis(
            year = 2026,
            month = 7,
            day = 25,
            hour = 12,
        )
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )
        var now = day
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val results = (0 until 26).map { matchIndex ->
            result(
                matchIndex = matchIndex,
                completedAtEpochMillis = day + matchIndex,
            )
        }

        store.restorePersistentState(
            OnlineServerStoreState(
                rankedResults = results,
            ),
        )

        now = period.endsAtEpochMillis

        assertTrue(store.advanceAuthoritativeTime())

        val state = store.snapshotPersistentState()
        val snapshot = state.rankedCycleSnapshots.single()

        assertEquals(
            ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
            state.schemaVersion,
        )
        assertEquals(period, snapshot.period)
        assertEquals(results.size, snapshot.resultCount)
        assertEquals(
            DAILY_CLOSED_RANKING_CAPACITY,
            snapshot.standings.size,
        )
        assertEquals(
            (1..DAILY_CLOSED_RANKING_CAPACITY).toList(),
            snapshot.standings.map { standing ->
                standing.rank
            },
        )

        val historical = store.getRankedCycleLadder(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )

        assertEquals(snapshot.toRankedCycleLadder(), historical)
        assertFalse(store.advanceAuthoritativeTime())
        assertEquals(
            1,
            store.snapshotPersistentState()
                .rankedCycleSnapshots
                .size,
        )
    }

    @Test
    fun persistent_store_reuses_closed_snapshot_after_restart() {
        val root = temporaryRoot()
        val stateFile = File(root, "authoritative-state.json")
        val day = epochMillis(
            year = 2026,
            month = 7,
            day = 25,
            hour = 18,
        )
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )
        val initialState = OnlineServerStoreState(
            rankedResults = listOf(
                result(
                    matchIndex = 1,
                    completedAtEpochMillis = day,
                ),
            ),
        )
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

        try {
            root.mkdirs()
            stateFile.writeText(
                json.encodeToString(initialState),
                Charsets.UTF_8,
            )

            val firstStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = {
                    period.endsAtEpochMillis
                },
            )
            try {
                assertTrue(firstStore.advanceAuthoritativeTime())
            } finally {
                firstStore.close()
            }

            val persisted = json.decodeFromString<OnlineServerStoreState>(
                stateFile.readText(Charsets.UTF_8),
            )
            val persistedSnapshot =
                persisted.rankedCycleSnapshots.single()

            val secondStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = {
                    period.endsAtEpochMillis
                },
            )
            try {
                val historical = secondStore.getRankedCycleLadder(
                    kind = RankingCycleKind.DAILY,
                    completedAtEpochMillis = day,
                )

                assertEquals(
                    persistedSnapshot.toRankedCycleLadder(),
                    historical,
                )
                assertFalse(secondStore.advanceAuthoritativeTime())
            } finally {
                secondStore.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun schema_seven_is_upgraded_without_inventing_snapshots() {
        val store = InMemoryOnlineServerStore()

        store.restorePersistentState(
            OnlineServerStoreState(
                schemaVersion = 7,
            ),
        )

        val normalized = store.snapshotPersistentState()

        assertEquals(
            ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
            normalized.schemaVersion,
        )
        assertTrue(normalized.rankedCycleSnapshots.isEmpty())
    }

    private fun result(
        matchIndex: Int,
        completedAtEpochMillis: Long,
    ): RankedMatchResult {
        val matchId = "snapshot-match-$matchIndex"

        return RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = CURRENT_RANKING_RULE_VERSION,
            completedAtEpochMillis = completedAtEpochMillis,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(1, 1),
            completedRounds = 1,
            players = (0 until 4).map { seatIndex ->
                val won = seatIndex % 2 == 0

                RankedMatchPlayerResult(
                    playerId =
                        "snapshot-player-$matchIndex-$seatIndex",
                    accountId =
                        "snapshot-account-$matchIndex-$seatIndex",
                    seatIndex = seatIndex,
                    teamIndex = seatIndex % 2,
                    won = won,
                    victoriesDelta = if (won) 1 else 0,
                    gamesDelta = 1,
                    teamBalanceDelta = if (won) 6 else -3,
                    individualPointsScored =
                        if (won) 4 else 1,
                    touchesGiven = if (won) 2 else 0,
                    automaticRounds = 0,
                )
            },
        )
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
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
            "domino-ranking-snapshot-${System.nanoTime()}",
        )
    }
}
