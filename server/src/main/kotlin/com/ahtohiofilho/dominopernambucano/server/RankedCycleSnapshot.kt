package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankedCyclePeriod
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleStanding
import com.ahtohiofilho.dominopernambucano.competitive.RankedLadderStats
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import kotlinx.serialization.Serializable

internal const val DAILY_CLOSED_RANKING_CAPACITY = 100
internal const val WEEKLY_CLOSED_RANKING_CAPACITY = 500
internal const val MONTHLY_CLOSED_RANKING_CAPACITY = 1_000
internal const val ANNUAL_CLOSED_RANKING_CAPACITY = 5_000

@Serializable
data class RankedCycleStandingSnapshot(
    val rank: Int,
    val accountId: String,
    val victories: Long,
    val games: Long,
    val teamBalance: Long,
    val individualPoints: Long,
    val touchesGiven: Long,
    val automaticRounds: Long,
) {
    init {
        require(rank > 0)
        require(accountId.isNotBlank())
        require(victories >= 0L)
        require(games > 0L)
        require(victories <= games)
        require(individualPoints >= 0L)
        require(touchesGiven >= 0L)
        require(automaticRounds >= 0L)
    }

    internal fun toRankedCycleStanding(): RankedCycleStanding {
        return RankedCycleStanding(
            rank = rank,
            accountId = accountId,
            stats = RankedLadderStats(
                victories = victories,
                games = games,
                teamBalance = teamBalance,
                individualPoints = individualPoints,
                touchesGiven = touchesGiven,
                automaticRounds = automaticRounds,
            ),
        )
    }

    companion object {
        internal fun from(
            standing: RankedCycleStanding,
        ): RankedCycleStandingSnapshot {
            return RankedCycleStandingSnapshot(
                rank = standing.rank,
                accountId = standing.accountId,
                victories = standing.stats.victories,
                games = standing.stats.games,
                teamBalance = standing.stats.teamBalance,
                individualPoints = standing.stats.individualPoints,
                touchesGiven = standing.stats.touchesGiven,
                automaticRounds = standing.stats.automaticRounds,
            )
        }
    }
}

@Serializable
data class RankedCycleSnapshot(
    val period: RankedCyclePeriod,
    val resultCount: Int,
    val closedAtEpochMillis: Long,
    val standings: List<RankedCycleStandingSnapshot>,
    val totalEligiblePlayers: Int = standings.size,
    val retainedRankingSize: Int = standings.size,
) {
    init {
        require(resultCount >= 0)
        require(closedAtEpochMillis >= period.endsAtEpochMillis)
        require(totalEligiblePlayers >= 0)
        require(retainedRankingSize == standings.size)
        require(totalEligiblePlayers >= retainedRankingSize)
        require(
            standings.map { standing -> standing.rank } ==
                (1..standings.size).toList(),
        )
        require(
            standings.map { standing -> standing.accountId }
                .distinct()
                .size == standings.size,
        )
        require(standings.size <= period.kind.closedRankingCapacity())
    }

    internal fun toRankedCycleLadder(): RankedCycleLadder {
        return RankedCycleLadder(
            period = period,
            resultCount = resultCount,
            standings = standings.map { standing ->
                standing.toRankedCycleStanding()
            },
        )
    }
}

internal fun RankedCycleLadder.toClosedSnapshot(
    closedAtEpochMillis: Long,
): RankedCycleSnapshot {
    require(closedAtEpochMillis >= period.endsAtEpochMillis)

    val retainedStandings = standings
        .take(period.kind.closedRankingCapacity())
        .map { standing ->
            RankedCycleStandingSnapshot.from(standing)
        }

    return RankedCycleSnapshot(
        period = period,
        resultCount = resultCount,
        closedAtEpochMillis = closedAtEpochMillis,
        standings = retainedStandings,
        totalEligiblePlayers = standings.size,
        retainedRankingSize = retainedStandings.size,
    )
}


data class RankedCycleSnapshotPage(
    val totalSnapshots: Int,
    val snapshots: List<RankedCycleSnapshot>,
) {
    init {
        require(totalSnapshots >= snapshots.size)
    }
}

internal fun RankingCycleKind.closedRankingCapacity(): Int {
    return when (this) {
        RankingCycleKind.DAILY -> DAILY_CLOSED_RANKING_CAPACITY
        RankingCycleKind.WEEKLY -> WEEKLY_CLOSED_RANKING_CAPACITY
        RankingCycleKind.MONTHLY -> MONTHLY_CLOSED_RANKING_CAPACITY
        RankingCycleKind.ANNUAL -> ANNUAL_CLOSED_RANKING_CAPACITY
    }
}
