package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchPlayerResult
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.competitive.createRankedMatchResultId
import com.ahtohiofilho.dominopernambucano.competitive.resolveRankingCycle
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticRankingIsolationTest {
    @Test
    fun live_ladder_excludes_entire_result_when_any_account_is_synthetic() {
        val day = epochMillis(
            year = 2026,
            month = 8,
            day = 15,
            hour = 12,
        )
        val store = InMemoryOnlineServerStore()

        store.restorePersistentState(
            stateWithHumanAndMixedSyntheticResults(day),
        )

        val ladder = store.getRankedCycleLadder(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )

        assertEquals(1, ladder.resultCount)
        assertEquals(
            setOf(
                "account-a",
                "account-b",
                "account-c",
                "account-d",
            ),
            ladder.standings
                .map { standing -> standing.accountId }
                .toSet(),
        )
        assertFalse(
            ladder.standings.any { standing ->
                standing.accountId == "account-s"
            },
        )
        assertTrue(
            ladder.standings.all { standing ->
                standing.stats.games == 1L
            },
        )
    }

    @Test
    fun closed_snapshot_uses_the_same_official_result_filter() {
        val day = epochMillis(
            year = 2026,
            month = 8,
            day = 15,
            hour = 12,
        )
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = {
                period.endsAtEpochMillis + 1L
            },
        )

        store.restorePersistentState(
            stateWithHumanAndMixedSyntheticResults(day),
        )

        assertTrue(store.advanceAuthoritativeTime())

        val snapshot = store.getClosedRankedCycleSnapshot(
            period.cycleId,
        )

        assertNotNull(snapshot)
        requireNotNull(snapshot)

        assertEquals(1, snapshot.resultCount)
        assertEquals(
            setOf(
                "account-a",
                "account-b",
                "account-c",
                "account-d",
            ),
            snapshot.standings
                .map { standing -> standing.accountId }
                .toSet(),
        )
        assertFalse(
            snapshot.standings.any { standing ->
                standing.accountId == "account-s"
            },
        )
        assertTrue(
            snapshot.standings.all { standing ->
                standing.games == 1L
            },
        )
    }

    @Test
    fun legacy_result_without_account_registry_remains_rankable() {
        val day = epochMillis(
            year = 2026,
            month = 8,
            day = 15,
            hour = 12,
        )
        val store = InMemoryOnlineServerStore()

        store.restorePersistentState(
            OnlineServerStoreState(
                rankedResults = listOf(
                    result(
                        matchId = "legacy",
                        completedAtEpochMillis = day,
                        accountIds = listOf(
                            "legacy-a",
                            "legacy-b",
                            "legacy-c",
                            "legacy-d",
                        ),
                    ),
                ),
            ),
        )

        val ladder = store.getRankedCycleLadder(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )

        assertEquals(1, ladder.resultCount)
        assertEquals(4, ladder.standings.size)
    }

    private fun stateWithHumanAndMixedSyntheticResults(
        day: Long,
    ): OnlineServerStoreState {
        return OnlineServerStoreState(
            accounts = listOf(
                account("account-a", OnlineParticipantTypeDto.HUMAN),
                account("account-b", OnlineParticipantTypeDto.HUMAN),
                account("account-c", OnlineParticipantTypeDto.HUMAN),
                account("account-d", OnlineParticipantTypeDto.HUMAN),
                account("account-s", OnlineParticipantTypeDto.SYNTHETIC),
            ),
            rankedResults = listOf(
                result(
                    matchId = "human-only",
                    completedAtEpochMillis = day,
                    accountIds = listOf(
                        "account-a",
                        "account-b",
                        "account-c",
                        "account-d",
                    ),
                ),
                result(
                    matchId = "mixed-synthetic",
                    completedAtEpochMillis = day + 1_000L,
                    accountIds = listOf(
                        "account-a",
                        "account-s",
                        "account-b",
                        "account-c",
                    ),
                ),
            ),
        )
    }

    private fun account(
        accountId: String,
        participantType: OnlineParticipantTypeDto,
    ): OnlineServerAccount {
        return OnlineServerAccount(
            accountId = accountId,
            playerId = playerId(accountId),
            createdAtEpochMillis = 1L,
            participantType = participantType,
        )
    }

    private fun result(
        matchId: String,
        completedAtEpochMillis: Long,
        accountIds: List<String>,
    ): RankedMatchResult {
        require(accountIds.size == 4)

        return RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = CURRENT_RANKING_RULE_VERSION,
            completedAtEpochMillis = completedAtEpochMillis,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(1, 1),
            completedRounds = 1,
            players = accountIds.mapIndexed { seatIndex, accountId ->
                player(
                    seatIndex = seatIndex,
                    accountId = accountId,
                    won = seatIndex % 2 == 0,
                )
            },
        )
    }

    private fun player(
        seatIndex: Int,
        accountId: String,
        won: Boolean,
    ): RankedMatchPlayerResult {
        return RankedMatchPlayerResult(
            playerId = playerId(accountId),
            accountId = accountId,
            seatIndex = seatIndex,
            teamIndex = seatIndex % 2,
            won = won,
            victoriesDelta = if (won) 1 else 0,
            gamesDelta = 1,
            teamBalanceDelta = if (won) 6 else -3,
            individualPointsScored = if (won) 4 else 1,
            touchesGiven = if (won) 1 else 0,
            automaticRounds = 0,
        )
    }

    private fun playerId(
        accountId: String,
    ): String = "player-$accountId"

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
}