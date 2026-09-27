package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.PublicRankingAwardTierDto

internal const val RANKING_AWARD_RULE_VERSION_V1 = 1
internal const val CURRENT_RANKING_AWARD_RULE_VERSION =
    RANKING_AWARD_RULE_VERSION_V1
internal const val MAXIMUM_AWARDED_RANKING_SIZE = 50

internal fun isSupportedRankingAwardRuleVersion(
    version: Int,
): Boolean {
    return version == RANKING_AWARD_RULE_VERSION_V1
}

internal data class RankingAwardDecision(
    val awardRuleVersion: Int,
    val awardedRankingSize: Int,
) {
    init {
        require(isSupportedRankingAwardRuleVersion(awardRuleVersion))
        require(awardedRankingSize >= 0)
        require(awardedRankingSize <= MAXIMUM_AWARDED_RANKING_SIZE)
    }

    fun tierFor(rank: Int): PublicRankingAwardTierDto? {
        require(rank > 0)

        if (rank > awardedRankingSize) {
            return null
        }

        return when (awardRuleVersion) {
            RANKING_AWARD_RULE_VERSION_V1 ->
                tierForVersionOne(rank)

            else -> error(
                "Versao de regra de conquista nao suportada: " +
                    awardRuleVersion,
            )
        }
    }

    private fun tierForVersionOne(
        rank: Int,
    ): PublicRankingAwardTierDto? {
        return when (rank) {
            1 -> PublicRankingAwardTierDto.DIAMOND
            in 2..5 -> PublicRankingAwardTierDto.GOLD
            in 6..10 -> PublicRankingAwardTierDto.SILVER
            in 11..50 -> PublicRankingAwardTierDto.BRONZE
            else -> null
        }
    }
}

internal object RankingAwardPolicy {
    fun evaluate(
        isClosed: Boolean,
        publicationDecision: RankingPublicationDecision,
        retainedRankingSize: Int,
    ): RankingAwardDecision {
        require(retainedRankingSize >= 0)

        val awardedRankingSize =
            if (isClosed && publicationDecision.isPublished) {
                minOf(
                    retainedRankingSize,
                    MAXIMUM_AWARDED_RANKING_SIZE,
                )
            } else {
                0
            }

        return RankingAwardDecision(
            awardRuleVersion = CURRENT_RANKING_AWARD_RULE_VERSION,
            awardedRankingSize = awardedRankingSize,
        )
    }
}
