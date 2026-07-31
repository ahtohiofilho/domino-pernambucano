package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleStanding
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleSummaryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCyclesResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingPublicationStatusDto
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
    totalEligiblePlayers: Int = ladder.standings.size,
    retainedRankingSize: Int = ladder.standings.size,
    closedAtEpochMillis: Long? = null,
    offset: Int,
    limit: Int,
    publicationPolicy: RankingPublicationPolicy =
        DEFAULT_RANKING_PUBLICATION_POLICY,
) {
    if (offset < 0 || limit !in 1..MAXIMUM_PUBLIC_RANKING_PAGE_SIZE) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val publicationDecision = publicationPolicy.evaluate(
        kind = cycle.toRankingCycleKind(),
        totalEligiblePlayers = totalEligiblePlayers,
    )
    val publishedStandings =
        if (publicationDecision.isPublished) {
            ladder.standings
        } else {
            emptyList()
        }
    val entries = publishedStandings
        .drop(offset)
        .take(limit)
        .map { standing ->
            standing.toPublicRankingEntry(
                displayName =
                    publicDisplayNames[standing.accountId],
            )
        }
    require(totalEligiblePlayers >= retainedRankingSize)
    require(retainedRankingSize == ladder.standings.size)

    val consumed = offset.toLong() + entries.size.toLong()
    val viewer = if (publicationDecision.isPublished) {
        viewerAccountId
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
    } else {
        null
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
            publicationThreshold =
                publicationDecision.publicationThreshold,
            publicationStatus =
                publicationDecision.status
                    .toPublicRankingPublicationStatusDto(),
            eligiblePlayersRemaining =
                publicationDecision.eligiblePlayersRemaining,
            awardsEligible = publicationDecision.isPublished,
            retainedRankingSize = retainedRankingSize,
            isClosed = closedAtEpochMillis != null,
            closedAtEpochMillis = closedAtEpochMillis,
            isLegacyTruncated =
                closedAtEpochMillis != null &&
                    totalEligiblePlayers > retainedRankingSize,
            offset = offset,
            limit = limit,
            hasMore =
                publicationDecision.isPublished &&
                    consumed < retainedRankingSize.toLong(),
            entries = entries,
            viewer = viewer,
        ),
    )
}

private fun RankingPublicationStatus
    .toPublicRankingPublicationStatusDto():
        PublicRankingPublicationStatusDto {
    return when (this) {
        RankingPublicationStatus.BELOW_THRESHOLD ->
            PublicRankingPublicationStatusDto.BELOW_THRESHOLD
        RankingPublicationStatus.PUBLISHED ->
            PublicRankingPublicationStatusDto.PUBLISHED
    }
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

internal suspend fun ApplicationCall.respondPublicRankingCycles(
    cycle: PublicRankingCycleDto,
    page: RankedCycleSnapshotPage,
    offset: Int,
    limit: Int,
    publicationPolicy: RankingPublicationPolicy =
        DEFAULT_RANKING_PUBLICATION_POLICY,
) {
    if (offset < 0 || limit !in 1..MAXIMUM_PUBLIC_RANKING_PAGE_SIZE) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val consumed = offset.toLong() + page.snapshots.size.toLong()

    respond(
        HttpStatusCode.OK,
        PublicRankingCyclesResponseDto(
            cycle = cycle,
            totalClosedCycles = page.totalSnapshots,
            offset = offset,
            limit = limit,
            hasMore = consumed < page.totalSnapshots.toLong(),
            cycles = page.snapshots.map { snapshot ->
                val publicationDecision = publicationPolicy.evaluate(
                    kind = snapshot.period.kind,
                    totalEligiblePlayers =
                        snapshot.totalEligiblePlayers,
                )

                PublicRankingCycleSummaryDto(
                    cycle = cycle,
                    cycleId = snapshot.period.cycleId,
                    rankingRuleVersion =
                        snapshot.period.rankingRuleVersion,
                    timeZoneId = snapshot.period.timeZoneId,
                    startsAtEpochMillis =
                        snapshot.period.startsAtEpochMillis,
                    endsAtEpochMillis =
                        snapshot.period.endsAtEpochMillis,
                    closedAtEpochMillis =
                        snapshot.closedAtEpochMillis,
                    resultCount = snapshot.resultCount,
                    totalEligiblePlayers =
                        snapshot.totalEligiblePlayers,
                    retainedRankingSize =
                        snapshot.retainedRankingSize,
                    publicationThreshold =
                        publicationDecision.publicationThreshold,
                    publicationStatus =
                        publicationDecision.status
                            .toPublicRankingPublicationStatusDto(),
                    eligiblePlayersRemaining =
                        publicationDecision.eligiblePlayersRemaining,
                    awardsEligible =
                        publicationDecision.isPublished,
                    isLegacyTruncated =
                        snapshot.isLegacyTruncated,
                )
            },
        ),
    )
}
