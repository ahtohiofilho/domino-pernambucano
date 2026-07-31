package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind

internal enum class RankingPublicationProfile {
    PRODUCTION,
    HOMOLOGATION,
    TEST,
}

internal enum class RankingPublicationStatus {
    BELOW_THRESHOLD,
    PUBLISHED,
}

internal data class RankingPublicationThresholds(
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

    fun thresholdFor(kind: RankingCycleKind): Int {
        return when (kind) {
            RankingCycleKind.DAILY -> daily
            RankingCycleKind.WEEKLY -> weekly
            RankingCycleKind.MONTHLY -> monthly
            RankingCycleKind.ANNUAL -> annual
        }
    }

    companion object {
        val production = RankingPublicationThresholds(
            daily = 100,
            weekly = 500,
            monthly = 1_000,
            annual = 5_000,
        )

        val homologation = RankingPublicationThresholds(
            daily = 4,
            weekly = 4,
            monthly = 4,
            annual = 4,
        )
    }
}

internal data class RankingPublicationDecision(
    val publicationThreshold: Int,
    val totalEligiblePlayers: Int,
) {
    init {
        require(publicationThreshold > 0)
        require(totalEligiblePlayers >= 0)
    }

    val status: RankingPublicationStatus =
        if (totalEligiblePlayers >= publicationThreshold) {
            RankingPublicationStatus.PUBLISHED
        } else {
            RankingPublicationStatus.BELOW_THRESHOLD
        }

    val eligiblePlayersRemaining: Int =
        (publicationThreshold - totalEligiblePlayers).coerceAtLeast(0)

    val isPublished: Boolean
        get() = status == RankingPublicationStatus.PUBLISHED
}

internal class RankingPublicationPolicy private constructor(
    val profile: RankingPublicationProfile,
    val thresholds: RankingPublicationThresholds,
) {
    fun thresholdFor(kind: RankingCycleKind): Int {
        return thresholds.thresholdFor(kind)
    }

    fun evaluate(
        kind: RankingCycleKind,
        totalEligiblePlayers: Int,
    ): RankingPublicationDecision {
        return RankingPublicationDecision(
            publicationThreshold = thresholdFor(kind),
            totalEligiblePlayers = totalEligiblePlayers,
        )
    }

    companion object {
        val production = RankingPublicationPolicy(
            profile = RankingPublicationProfile.PRODUCTION,
            thresholds = RankingPublicationThresholds.production,
        )

        val homologation = RankingPublicationPolicy(
            profile = RankingPublicationProfile.HOMOLOGATION,
            thresholds = RankingPublicationThresholds.homologation,
        )

        fun test(
            thresholds: RankingPublicationThresholds,
        ): RankingPublicationPolicy {
            return RankingPublicationPolicy(
                profile = RankingPublicationProfile.TEST,
                thresholds = thresholds,
            )
        }
    }
}

internal val OnlineServerEnvironment.rankingPublicationPolicy:
    RankingPublicationPolicy
    get() = when (this) {
        OnlineServerEnvironment.HOMOLOGATION ->
            RankingPublicationPolicy.homologation

        OnlineServerEnvironment.DEVELOPMENT,
        OnlineServerEnvironment.TEST,
        OnlineServerEnvironment.PRODUCTION ->
            RankingPublicationPolicy.production
    }

internal val DEFAULT_RANKING_PUBLICATION_POLICY: RankingPublicationPolicy =
    RankingPublicationPolicy.production
