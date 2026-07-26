package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleStanding
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.security.MessageDigest
import java.util.Locale

internal const val DEFAULT_PUBLIC_RANKING_PAGE_SIZE = 50
internal const val MAXIMUM_PUBLIC_RANKING_PAGE_SIZE = 100

internal fun parsePublicRankingCycle(value: String?): PublicRankingCycleDto? {
    val normalized = value
        ?.trim()
        ?.takeIf { candidate -> candidate.isNotBlank() }
        ?.uppercase(Locale.ROOT)
        ?: return null

    return PublicRankingCycleDto.values().firstOrNull { cycle ->
        cycle.name == normalized
    }
}

internal fun PublicRankingCycleDto.toRankingCycleKind(): RankingCycleKind {
    return when (this) {
        PublicRankingCycleDto.DAILY -> RankingCycleKind.DAILY
        PublicRankingCycleDto.WEEKLY -> RankingCycleKind.WEEKLY
        PublicRankingCycleDto.MONTHLY -> RankingCycleKind.MONTHLY
        PublicRankingCycleDto.ANNUAL -> RankingCycleKind.ANNUAL
    }
}

internal suspend fun ApplicationCall.respondPublicRanking(
    cycle: PublicRankingCycleDto,
    ladder: RankedCycleLadder,
    viewerAccountId: String?,
    publicDisplayNames: Map<String, String> = emptyMap(),
    offset: Int,
    limit: Int,
) {
    if (offset < 0 || limit !in 1..MAXIMUM_PUBLIC_RANKING_PAGE_SIZE) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val entries = ladder.standings
        .drop(offset)
        .take(limit)
        .map { standing ->
            standing.toPublicRankingEntry(
                displayName =
                    publicDisplayNames[standing.accountId],
            )
        }
    val totalEligiblePlayers = ladder.standings.size
    val consumed = offset.toLong() + entries.size.toLong()
    val viewer = viewerAccountId
        ?.trim()
        ?.takeIf { accountId -> accountId.isNotBlank() }
        ?.let { accountId ->
            ladder.standings.firstOrNull { standing ->
                standing.accountId == accountId
            }
        }
        ?.let { standing ->
            standing.toPublicRankingEntry(
                displayName =
                    publicDisplayNames[standing.accountId],
            )
        }

    respond(
        HttpStatusCode.OK,
        PublicRankingResponseDto(
            cycle = cycle,
            cycleId = ladder.period.cycleId,
            rankingRuleVersion = ladder.period.rankingRuleVersion,
            timeZoneId = ladder.period.timeZoneId,
            startsAtEpochMillis = ladder.period.startsAtEpochMillis,
            endsAtEpochMillis = ladder.period.endsAtEpochMillis,
            resultCount = ladder.resultCount,
            totalEligiblePlayers = totalEligiblePlayers,
            offset = offset,
            limit = limit,
            hasMore = consumed < totalEligiblePlayers.toLong(),
            entries = entries,
            viewer = viewer,
        ),
    )
}

private fun RankedCycleStanding.toPublicRankingEntry(
    displayName: String?,
): PublicRankingEntryDto {
    return PublicRankingEntryDto(
        rank = rank,
        competitorId = createPublicCompetitorId(accountId),
        displayName = displayName,
        victories = stats.victories,
        games = stats.games,
        scoreNumerator = Math.addExact(stats.victories, 1L),
        scoreDenominator = Math.addExact(stats.games, 2L),
        teamBalance = stats.teamBalance,
        individualPoints = stats.individualPoints,
        touchesGiven = stats.touchesGiven,
        automaticRounds = stats.automaticRounds,
    )
}

private fun createPublicCompetitorId(accountId: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(
        accountId.toByteArray(Charsets.UTF_8),
    )
    val prefix = digest.take(16).joinToString(separator = "") { byte ->
        "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
    }

    return "competitor-$prefix"
}
