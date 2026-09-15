package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankedCyclePeriod
import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V2
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleStanding
import com.ahtohiofilho.dominopernambucano.competitive.RankedLadderStats
import com.ahtohiofilho.dominopernambucano.online.areValidPublicRankingPageRanks
import kotlinx.serialization.Serializable


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
    val assists: Long = 0L,
    val automaticPlays: Long = 0L,
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
        require(assists >= 0L)
        require(automaticPlays >= 0L)
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
                assists = assists,
                touchesGiven = touchesGiven,
                automaticRounds = automaticRounds,
                automaticPlays = automaticPlays,
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
                assists = standing.stats.assists,
                automaticPlays = standing.stats.automaticPlays,
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
    val retentionPolicyVersion: Int =
        LEGACY_RANKING_RETENTION_POLICY_VERSION,
) {
    init {
        require(resultCount >= 0)
        require(closedAtEpochMillis >= period.endsAtEpochMillis)
        require(totalEligiblePlayers >= 0)
        require(retainedRankingSize == standings.size)
        require(totalEligiblePlayers >= retainedRankingSize)
        require(retentionPolicyVersion >= 0)
        require(
            areValidPublicRankingPageRanks(
                rankingRuleVersion = period.rankingRuleVersion,
                offset = 0,
                ranks = standings.map { standing ->
                    standing.rank
                },
            ),
        )
        require(
            standings.map { standing -> standing.accountId }
                .distinct()
                .size == standings.size,
        )
    }

    val hasCompleteStandings: Boolean
        get() = totalEligiblePlayers == retainedRankingSize

    val isLegacyTruncated: Boolean
        get() =
            retentionPolicyVersion ==
                LEGACY_RANKING_RETENTION_POLICY_VERSION &&
                !hasCompleteStandings

    val isRetentionLimited: Boolean
        get() =
            retentionPolicyVersion >
                LEGACY_RANKING_RETENTION_POLICY_VERSION &&
                !hasCompleteStandings

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
    retentionPolicy: RankingRetentionPolicy =
        DEFAULT_RANKING_RETENTION_POLICY,
): RankedCycleSnapshot {
    require(closedAtEpochMillis >= period.endsAtEpochMillis)

    val retentionLimit = retentionPolicy.limitFor(period.kind)
    val retainedStandings = (
        if (period.rankingRuleVersion == RANKING_RULE_VERSION_V2) {
            /*
             * In V2, a retention limit is a public-rank boundary rather
             * than a hard row count. This prevents an exact tie from
             * being split merely because it crosses the nominal Top N.
             *
             * Example: ranks 99, 100, 100, 100, 103 with a Top 100
             * retention policy keep all three players ranked 100th.
             */
            standings.takeWhile { standing ->
                standing.rank <= retentionLimit
            }
        } else {
            standings.take(retentionLimit)
        }
    ).map { standing ->
        RankedCycleStandingSnapshot.from(standing)
    }

    return RankedCycleSnapshot(
        period = period,
        resultCount = resultCount,
        closedAtEpochMillis = closedAtEpochMillis,
        standings = retainedStandings,
        totalEligiblePlayers = standings.size,
        retainedRankingSize = retainedStandings.size,
        retentionPolicyVersion = retentionPolicy.version,
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
