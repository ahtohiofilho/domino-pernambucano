package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

@Serializable
enum class PublicRankingCycleDto {
    DAILY,
    WEEKLY,
    MONTHLY,
    ANNUAL,
}

@Serializable
data class PublicRankingEntryDto(
    val rank: Int,
    val competitorId: String,
    val displayName: String? = null,
    val victories: Long,
    val games: Long,
    val scoreNumerator: Long,
    val scoreDenominator: Long,
    val teamBalance: Long,
    val individualPoints: Long,
    val touchesGiven: Long,
    val automaticRounds: Long,
)

@Serializable
data class PublicRankingResponseDto(
    val cycle: PublicRankingCycleDto,
    val cycleId: String,
    val rankingRuleVersion: Int,
    val timeZoneId: String,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
    val resultCount: Int,
    val totalEligiblePlayers: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val entries: List<PublicRankingEntryDto>,
    val viewer: PublicRankingEntryDto? = null,
    val retainedRankingSize: Int = totalEligiblePlayers,
    val isClosed: Boolean = false,
    val closedAtEpochMillis: Long? = null,
)

@Serializable
data class PublicRankingCycleSummaryDto(
    val cycle: PublicRankingCycleDto,
    val cycleId: String,
    val rankingRuleVersion: Int,
    val timeZoneId: String,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
    val closedAtEpochMillis: Long,
    val resultCount: Int,
    val totalEligiblePlayers: Int,
    val retainedRankingSize: Int,
)

@Serializable
data class PublicRankingCyclesResponseDto(
    val cycle: PublicRankingCycleDto,
    val totalClosedCycles: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val cycles: List<PublicRankingCycleSummaryDto>,
)
