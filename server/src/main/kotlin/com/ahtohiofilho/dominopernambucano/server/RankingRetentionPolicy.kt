package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind

internal const val LEGACY_RANKING_RETENTION_POLICY_VERSION = 0
internal const val CURRENT_RANKING_RETENTION_POLICY_VERSION = 1

internal data class RankingRetentionLimits(
    val daily: Int,
    val weekly: Int,
    val monthly: Int,
    val annual: Int,
) {
    init {
        require(daily > 0)
        require(weekly > 0)
        require(monthly > 0)
        require(annual > 0)
    }

    fun limitFor(kind: RankingCycleKind): Int {
        return when (kind) {
            RankingCycleKind.DAILY -> daily
            RankingCycleKind.WEEKLY -> weekly
            RankingCycleKind.MONTHLY -> monthly
            RankingCycleKind.ANNUAL -> annual
        }
    }

    companion object {
        val current = RankingRetentionLimits(
            daily = 100,
            weekly = 500,
            monthly = 1_000,
            annual = 5_000,
        )
    }
}

internal class RankingRetentionPolicy(
    val version: Int,
    val limits: RankingRetentionLimits,
) {
    init {
        require(version > LEGACY_RANKING_RETENTION_POLICY_VERSION)
    }

    fun limitFor(kind: RankingCycleKind): Int = limits.limitFor(kind)

    companion object {
        val current = RankingRetentionPolicy(
            version = CURRENT_RANKING_RETENTION_POLICY_VERSION,
            limits = RankingRetentionLimits.current,
        )
    }
}

internal val DEFAULT_RANKING_RETENTION_POLICY: RankingRetentionPolicy =
    RankingRetentionPolicy.current
