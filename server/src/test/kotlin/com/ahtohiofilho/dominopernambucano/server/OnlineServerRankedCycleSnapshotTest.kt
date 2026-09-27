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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineServerRankedCycleSnapshotTest {
    @Test
    fun schema_thirteen_to_fourteen_preserves_ranked_history() {
        val day = epochMillis(
            year = 2026,
            month = 9,
            day = 23,
            hour = 12,
        )
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )
        var now = day
        val source = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        source.restorePersistentState(
            OnlineServerStoreState(
                rankedResults = listOf(
                    result(
                        matchIndex = 13,
                        completedAtEpochMillis = day,
                    ),
                ),
            ),
        )

        now = period.endsAtEpochMillis
        assertTrue(source.advanceAuthoritativeTime())

        val beforeMigration = source.snapshotPersistentState()

        assertEquals(1, beforeMigration.rankedResults.size)
        assertEquals(1, beforeMigration.rankedCycleSnapshots.size)

        val schemaThirteenState = beforeMigration.copy(
            schemaVersion = 13,
        )
        val restored = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )

        restored.restorePersistentState(schemaThirteenState)

        val normalized = restored.snapshotPersistentState()

        assertEquals(
            ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
            normalized.schemaVersion,
        )
        assertEquals(
            beforeMigration.rankedResults,
            normalized.rankedResults,
        )
        assertEquals(
            beforeMigration.rankedCycleSnapshots,
            normalized.rankedCycleSnapshots,
        )
        assertEquals(
            beforeMigration.accounts,
            normalized.accounts,
        )
        assertEquals(
            beforeMigration.publicRankedFormationHistory,
            normalized.publicRankedFormationHistory,
        )
    }

    @Test
    fun closed_daily_v2_cycle_preserves_ties_across_top_n_boundary() {
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
            results.size * 4,
            snapshot.totalEligiblePlayers,
        )
        assertEquals(
            results.size * 4,
            snapshot.retainedRankingSize,
        )
        assertEquals(
            snapshot.retainedRankingSize,
            snapshot.standings.size,
        )
        assertEquals(
            listOf(1, 53),
            snapshot.standings
                .map { standing -> standing.rank }
                .distinct(),
        )
        assertEquals(
            results.size * 2,
            snapshot.standings.count { standing ->
                standing.rank == 1
            },
        )
        assertEquals(
            results.size * 2,
            snapshot.standings.count { standing ->
                standing.rank == 53
            },
        )
        assertTrue(snapshot.hasCompleteStandings)
        assertFalse(snapshot.isLegacyTruncated)
        assertFalse(snapshot.isRetentionLimited)
        assertEquals(
            CURRENT_RANKING_RETENTION_POLICY_VERSION,
            snapshot.retentionPolicyVersion,
        )
        assertEquals(100, snapshot.publicationThreshold)
        assertEquals(
            CURRENT_RANKING_AWARD_RULE_VERSION,
            snapshot.awardRuleVersion,
        )
        assertEquals(50, snapshot.awardedRankingSize)

        val historical = store.getRankedCycleLadder(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )

        assertEquals(snapshot.toRankedCycleLadder(), historical)
        assertEquals(
            snapshot,
            store.getClosedRankedCycleSnapshot(
                cycleId = period.cycleId,
            ),
        )
        assertEquals(
            RankedCycleSnapshotPage(
                totalSnapshots = 1,
                snapshots = listOf(snapshot),
            ),
            store.listClosedRankedCycleSnapshots(
                kind = RankingCycleKind.DAILY,
                offset = 0,
                limit = 10,
            ),
        )
        assertFalse(store.advanceAuthoritativeTime())
        assertEquals(
            1,
            store.snapshotPersistentState()
                .rankedCycleSnapshots
                .size,
        )
    }

    @Test
    fun legacy_truncated_snapshot_remains_readable_and_is_marked() {
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
        val source = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val results = (0 until 26).map { matchIndex ->
            result(
                matchIndex = matchIndex,
                completedAtEpochMillis = day + matchIndex,
            )
        }

        source.restorePersistentState(
            OnlineServerStoreState(
                rankedResults = results,
            ),
        )
        now = period.endsAtEpochMillis
        assertTrue(source.advanceAuthoritativeTime())

        val completeSnapshot = source.snapshotPersistentState()
            .rankedCycleSnapshots
            .single()
        val legacyRetainedSize = 100
        val legacySnapshot = completeSnapshot.copy(
            standings = completeSnapshot.standings
                .take(legacyRetainedSize),
            retainedRankingSize = legacyRetainedSize,
            retentionPolicyVersion =
                LEGACY_RANKING_RETENTION_POLICY_VERSION,
        )
        val restored = InMemoryOnlineServerStore()

        restored.restorePersistentState(
            OnlineServerStoreState(
                rankedCycleSnapshots = listOf(legacySnapshot),
            ),
        )

        val normalized = restored.snapshotPersistentState()
            .rankedCycleSnapshots
            .single()

        assertEquals(
            results.size * 4,
            normalized.totalEligiblePlayers,
        )
        assertEquals(
            legacyRetainedSize,
            normalized.retainedRankingSize,
        )
        assertEquals(
            legacyRetainedSize,
            normalized.standings.size,
        )
        assertFalse(normalized.hasCompleteStandings)
        assertTrue(normalized.isLegacyTruncated)
        assertEquals(
            legacyRetainedSize,
            requireNotNull(
                restored.getRankedCycleLadder(
                    kind = RankingCycleKind.DAILY,
                    completedAtEpochMillis = day,
                ),
            ).standings.size,
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
    fun schema_eight_is_upgraded_without_losing_snapshot_metadata() {
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
        val source = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )

        source.restorePersistentState(
            OnlineServerStoreState(
                rankedResults = listOf(
                    result(
                        matchIndex = 1,
                        completedAtEpochMillis = day,
                    ),
                ),
            ),
        )
        now = period.endsAtEpochMillis
        assertTrue(source.advanceAuthoritativeTime())

        val json = Json {
            encodeDefaults = true
        }
        val encodedState = json.encodeToJsonElement(
            source.snapshotPersistentState(),
        ).jsonObject
        val legacyRoot = encodedState.toMutableMap().apply {
            this["schemaVersion"] = JsonPrimitive(8)
            this["rankedCycleSnapshots"] = JsonArray(
                getValue("rankedCycleSnapshots")
                    .jsonArray
                    .map { snapshotElement ->
                        JsonObject(
                            snapshotElement.jsonObject
                                .toMutableMap()
                                .apply {
                                    remove("totalEligiblePlayers")
                                    remove("retainedRankingSize")
                                },
                        )
                    },
            )
        }
        val legacyState =
            json.decodeFromJsonElement<OnlineServerStoreState>(
                JsonObject(legacyRoot),
            )
        val restored = InMemoryOnlineServerStore()

        restored.restorePersistentState(legacyState)

        val normalized = restored.snapshotPersistentState()
        val snapshot = normalized.rankedCycleSnapshots.single()

        assertEquals(
            ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
            normalized.schemaVersion,
        )
        assertEquals(4, snapshot.totalEligiblePlayers)
        assertEquals(4, snapshot.retainedRankingSize)
        assertEquals(4, snapshot.standings.size)
        assertEquals(
            LEGACY_RANKING_RETENTION_POLICY_VERSION,
            snapshot.retentionPolicyVersion,
        )
    }

    @Test
    fun schema_nine_snapshot_is_preserved_without_retroactive_cut() {
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
        val standings = (1..104).map { rank ->
            RankedCycleStandingSnapshot(
                rank = rank,
                accountId = "legacy-account-$rank",
                victories = 1,
                games = 1,
                teamBalance = 1,
                individualPoints = 1,
                touchesGiven = 0,
                automaticRounds = 0,
            )
        }
        val store = InMemoryOnlineServerStore()

        store.restorePersistentState(
            OnlineServerStoreState(
                schemaVersion = 9,
                rankedCycleSnapshots = listOf(
                    RankedCycleSnapshot(
                        period = period,
                        resultCount = 26,
                        closedAtEpochMillis = period.endsAtEpochMillis,
                        standings = standings,
                    ),
                ),
            ),
        )

        val snapshot = store.snapshotPersistentState()
            .rankedCycleSnapshots.single()

        assertEquals(104, snapshot.retainedRankingSize)
        assertEquals(104, snapshot.standings.size)
        assertEquals(
            LEGACY_RANKING_RETENTION_POLICY_VERSION,
            snapshot.retentionPolicyVersion,
        )
        assertTrue(snapshot.hasCompleteStandings)
        assertFalse(snapshot.isLegacyTruncated)
        assertFalse(snapshot.isRetentionLimited)
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
