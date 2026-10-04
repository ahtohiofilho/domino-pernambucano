package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleStanding
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.PublicRankingAchievementDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingAchievementGalleryResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingAwardTierDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleSummaryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCyclesResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingPublicationStatusDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val DEFAULT_PUBLIC_RANKING_PAGE_SIZE = 50
internal const val MAXIMUM_PUBLIC_RANKING_PAGE_SIZE = 100
internal const val CURRENT_RANKING_CACHE_MAX_AGE_SECONDS = 300
internal const val HISTORICAL_RANKING_CACHE_MAX_AGE_SECONDS = 300
internal const val CLOSED_CYCLES_CACHE_MAX_AGE_SECONDS = 30
internal const val RANKING_ACHIEVEMENTS_CACHE_MAX_AGE_SECONDS = 30

private val publicRankingCacheJson = Json {
    encodeDefaults = true
}

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

private fun RankingCycleKind.toPublicRankingCycleDto():
    PublicRankingCycleDto {
    return when (this) {
        RankingCycleKind.DAILY -> PublicRankingCycleDto.DAILY
        RankingCycleKind.WEEKLY -> PublicRankingCycleDto.WEEKLY
        RankingCycleKind.MONTHLY -> PublicRankingCycleDto.MONTHLY
        RankingCycleKind.ANNUAL -> PublicRankingCycleDto.ANNUAL
    }
}

internal suspend fun ApplicationCall.respondPublicRanking(
    cycle: PublicRankingCycleDto,
    ladder: RankedCycleLadder,
    viewerAccountId: String?,
    publicDisplayNames: Map<String, String> = emptyMap(),
    publicTableNames: Map<String, String> = emptyMap(),
    totalEligiblePlayers: Int = ladder.standings.size,
    retainedRankingSize: Int = ladder.standings.size,
    closedAtEpochMillis: Long? = null,
    retentionPolicyVersion: Int =
        LEGACY_RANKING_RETENTION_POLICY_VERSION,
    isLegacyTruncated: Boolean = false,
    frozenPublicationThreshold: Int? = null,
    frozenAwardRuleVersion: Int? = null,
    frozenAwardedRankingSize: Int? = null,
    expectedRankingRevision: String? = null,
    offset: Int,
    limit: Int,
    publicationPolicy: RankingPublicationPolicy =
        DEFAULT_RANKING_PUBLICATION_POLICY,
) {
    if (offset < 0 || limit !in 1..MAXIMUM_PUBLIC_RANKING_PAGE_SIZE) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    require(totalEligiblePlayers >= retainedRankingSize)
    require(retainedRankingSize == ladder.standings.size)

    val rankingRevision = ladder.publicRankingRevision()
    if (
        expectedRankingRevision != null &&
        expectedRankingRevision != rankingRevision
    ) {
        respond(HttpStatusCode.Conflict)
        return
    }

    val isClosed = closedAtEpochMillis != null
    val frozenAwardFacts = listOf(
        frozenPublicationThreshold,
        frozenAwardRuleVersion,
        frozenAwardedRankingSize,
    )
    require(
        frozenAwardFacts.all { value -> value == null } ||
            frozenAwardFacts.all { value -> value != null },
    )
    require(
        !frozenAwardFacts.any { value -> value != null } || isClosed,
    )

    val publicationDecision =
        frozenPublicationThreshold?.let { threshold ->
            RankingPublicationDecision(
                publicationThreshold = threshold,
                totalEligiblePlayers = totalEligiblePlayers,
            )
        } ?: publicationPolicy.evaluate(
            kind = cycle.toRankingCycleKind(),
            totalEligiblePlayers = totalEligiblePlayers,
        )
    val awardDecision =
        if (
            frozenAwardRuleVersion != null &&
            frozenAwardedRankingSize != null
        ) {
            RankingAwardDecision(
                awardRuleVersion = frozenAwardRuleVersion,
                awardedRankingSize = frozenAwardedRankingSize,
            ).also { decision ->
                require(
                    decision.awardedRankingSize <= retainedRankingSize,
                )
                if (!publicationDecision.isPublished) {
                    require(decision.awardedRankingSize == 0)
                }
            }
        } else {
            RankingAwardPolicy.evaluate(
                isClosed = isClosed,
                publicationDecision = publicationDecision,
                retainedRankingSize = retainedRankingSize,
            )
        }
    val entries = ladder.standings
        .drop(offset)
        .take(limit)
        .map { standing ->
            standing.toPublicRankingEntry(
                displayName =
                    publicDisplayNames[standing.accountId],
                tableName =
                    publicTableNames[standing.accountId],
                awardTier =
                    awardDecision.tierFor(standing.rank),
            )
        }

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
                tableName =
                    publicTableNames[standing.accountId],
                awardTier =
                    awardDecision.tierFor(standing.rank),
            )
        }

    val response = PublicRankingResponseDto(
            cycle = cycle,
            cycleId = ladder.period.cycleId,
            rankingRevision = rankingRevision,
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
            awardRuleVersion = awardDecision.awardRuleVersion,
            awardedRankingSize = awardDecision.awardedRankingSize,
            retainedRankingSize = retainedRankingSize,
            isClosed = closedAtEpochMillis != null,
            closedAtEpochMillis = closedAtEpochMillis,
            isLegacyTruncated = isLegacyTruncated,
            retentionPolicyVersion = retentionPolicyVersion,
            isRetentionLimited =
                closedAtEpochMillis != null &&
                    retentionPolicyVersion >
                    LEGACY_RANKING_RETENTION_POLICY_VERSION &&
                    totalEligiblePlayers > retainedRankingSize,
            offset = offset,
            limit = limit,
            hasMore =
                consumed < retainedRankingSize.toLong(),
            entries = entries,
            viewer = viewer,
        )

    respondPrivateCacheableRanking(
        payload = response,
        canonicalJson = publicRankingCacheJson.encodeToString(response),
        maxAgeSeconds =
            if (isClosed) {
                HISTORICAL_RANKING_CACHE_MAX_AGE_SECONDS
            } else {
                CURRENT_RANKING_CACHE_MAX_AGE_SECONDS
            },
    )
}

internal fun RankedCycleLadder.publicRankingRevision(): String {
    val digest = MessageDigest.getInstance("SHA-256")

    digest.updateCanonical(period.cycleId)
    digest.updateCanonical(period.rankingRuleVersion)
    digest.updateCanonical(resultCount)
    digest.updateCanonical(standings.size)
    standings.forEach { standing ->
        digest.updateCanonical(standing.rank)
        digest.updateCanonical(standing.accountId)
        digest.updateCanonical(standing.stats.victories)
        digest.updateCanonical(standing.stats.games)
        digest.updateCanonical(standing.stats.teamBalance)
        digest.updateCanonical(standing.stats.individualPoints)
        if (
            period.rankingRuleVersion ==
                com.ahtohiofilho.dominopernambucano.competitive
                    .RANKING_RULE_VERSION_V2 ||
            period.rankingRuleVersion ==
                com.ahtohiofilho.dominopernambucano.competitive
                    .RANKING_RULE_VERSION_V3
        ) {
            digest.updateCanonical(standing.stats.assists)
            digest.updateCanonical(standing.stats.automaticPlays)
        }
        digest.updateCanonical(standing.stats.touchesGiven)
        digest.updateCanonical(standing.stats.automaticRounds)
        if (
            period.rankingRuleVersion ==
                com.ahtohiofilho.dominopernambucano.competitive
                    .RANKING_RULE_VERSION_V3
        ) {
            digest.updateCanonical(standing.stats.timeoutRounds)
        }
    }

    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
    }
}

private fun MessageDigest.updateCanonical(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    updateCanonical(bytes.size)
    update(bytes)
}

private fun MessageDigest.updateCanonical(value: Int) {
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
}

private fun MessageDigest.updateCanonical(value: Long) {
    update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())
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
    tableName: String?,
    awardTier: PublicRankingAwardTierDto?,
): PublicRankingEntryDto {
    return PublicRankingEntryDto(
        rank = rank,
        competitorId = createPublicCompetitorId(accountId),
        displayName = displayName,
        tableName = tableName,
        victories = stats.victories,
        games = stats.games,
        scoreNumerator = Math.addExact(stats.victories, 1L),
        scoreDenominator = Math.addExact(stats.games, 2L),
        teamBalance = stats.teamBalance,
        individualPoints = stats.individualPoints,
        touchesGiven = stats.touchesGiven,
        automaticRounds = stats.automaticRounds,
        assists = stats.assists,
        automaticPlays = stats.automaticPlays,
        timeoutRounds = stats.timeoutRounds,
        awardTier = awardTier,
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

    val response = PublicRankingCyclesResponseDto(
            cycle = cycle,
            totalClosedCycles = page.totalSnapshots,
            offset = offset,
            limit = limit,
            hasMore = consumed < page.totalSnapshots.toLong(),
            cycles = page.snapshots.map { snapshot ->
                val publicationDecision =
                    snapshot.publicationThreshold?.let { threshold ->
                        RankingPublicationDecision(
                            publicationThreshold = threshold,
                            totalEligiblePlayers =
                                snapshot.totalEligiblePlayers,
                        )
                    } ?: publicationPolicy.evaluate(
                        kind = snapshot.period.kind,
                        totalEligiblePlayers =
                            snapshot.totalEligiblePlayers,
                    )
                val awardDecision =
                    if (
                        snapshot.awardRuleVersion != null &&
                        snapshot.awardedRankingSize != null
                    ) {
                        RankingAwardDecision(
                            awardRuleVersion =
                                snapshot.awardRuleVersion,
                            awardedRankingSize =
                                snapshot.awardedRankingSize,
                        )
                    } else {
                        RankingAwardPolicy.evaluate(
                            isClosed = true,
                            publicationDecision = publicationDecision,
                            retainedRankingSize =
                                snapshot.retainedRankingSize,
                        )
                    }

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
                    awardRuleVersion =
                        awardDecision.awardRuleVersion,
                    awardedRankingSize =
                        awardDecision.awardedRankingSize,
                    isLegacyTruncated =
                        snapshot.isLegacyTruncated,
                    retentionPolicyVersion =
                        snapshot.retentionPolicyVersion,
                    isRetentionLimited =
                        snapshot.isRetentionLimited,
                )
            },
        )

    respondPrivateCacheableRanking(
        payload = response,
        canonicalJson = publicRankingCacheJson.encodeToString(response),
        maxAgeSeconds = CLOSED_CYCLES_CACHE_MAX_AGE_SECONDS,
    )
}

private data class RankingAchievementRecord(
    val snapshot: RankedCycleSnapshot,
    val standing: RankedCycleStandingSnapshot,
    val tier: PublicRankingAwardTierDto,
    val awardRuleVersion: Int,
)

private data class RankingAchievementCounts(
    val diamond: Int,
    val gold: Int,
    val silver: Int,
    val bronze: Int,
)

internal fun officialRankingAchievementAccountIds(
    snapshots: List<RankedCycleSnapshot>,
): Set<String> {
    return buildOfficialRankingAchievementRecords(snapshots)
        .mapTo(linkedSetOf()) { record ->
            record.standing.accountId
        }
}

internal suspend fun ApplicationCall.respondPublicRankingAchievements(
    snapshots: List<RankedCycleSnapshot>,
    publicDisplayNames: Map<String, String>,
    offset: Int,
    limit: Int,
) {
    if (offset < 0 || limit !in 1..MAXIMUM_PUBLIC_RANKING_PAGE_SIZE) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val records = buildOfficialRankingAchievementRecords(snapshots)
        .sortedWith(
            compareByDescending<RankingAchievementRecord> { record ->
                record.snapshot.closedAtEpochMillis
            }.thenByDescending { record ->
                record.snapshot.period.endsAtEpochMillis
            }.thenBy { record ->
                record.snapshot.period.kind.ordinal
            }.thenBy { record ->
                record.standing.rank
            }.thenBy { record ->
                record.standing.accountId
            },
        )

    val countsByAccount = records
        .groupBy { record -> record.standing.accountId }
        .mapValues { (_, accountRecords) ->
            RankingAchievementCounts(
                diamond = accountRecords.count { record ->
                    record.tier == PublicRankingAwardTierDto.DIAMOND
                },
                gold = accountRecords.count { record ->
                    record.tier == PublicRankingAwardTierDto.GOLD
                },
                silver = accountRecords.count { record ->
                    record.tier == PublicRankingAwardTierDto.SILVER
                },
                bronze = accountRecords.count { record ->
                    record.tier == PublicRankingAwardTierDto.BRONZE
                },
            )
        }

    val pageRecords = records
        .drop(offset)
        .take(limit)

    val response = PublicRankingAchievementGalleryResponseDto(
        available = records.isNotEmpty(),
        totalAchievements = records.size,
        totalChampionships = records.count { record ->
            record.tier == PublicRankingAwardTierDto.DIAMOND
        },
        totalAwardedPlayers = countsByAccount.size,
        offset = offset,
        limit = limit,
        hasMore =
            offset.toLong() + pageRecords.size.toLong() <
                records.size.toLong(),
        achievements = pageRecords.map { record ->
            val counts = requireNotNull(
                countsByAccount[record.standing.accountId],
            )

            PublicRankingAchievementDto(
                competitorId =
                    createPublicCompetitorId(record.standing.accountId),
                displayName =
                    publicDisplayNames[record.standing.accountId],
                cycle =
                    record.snapshot.period.kind
                        .toPublicRankingCycleDto(),
                cycleId = record.snapshot.period.cycleId,
                startsAtEpochMillis =
                    record.snapshot.period.startsAtEpochMillis,
                endsAtEpochMillis =
                    record.snapshot.period.endsAtEpochMillis,
                closedAtEpochMillis =
                    record.snapshot.closedAtEpochMillis,
                rank = record.standing.rank,
                awardTier = record.tier,
                awardRuleVersion = record.awardRuleVersion,
                diamondCount = counts.diamond,
                goldCount = counts.gold,
                silverCount = counts.silver,
                bronzeCount = counts.bronze,
            )
        },
    )

    respondPrivateCacheableRanking(
        payload = response,
        canonicalJson = publicRankingCacheJson.encodeToString(response),
        maxAgeSeconds = RANKING_ACHIEVEMENTS_CACHE_MAX_AGE_SECONDS,
    )
}

private fun buildOfficialRankingAchievementRecords(
    snapshots: List<RankedCycleSnapshot>,
): List<RankingAchievementRecord> {
    return snapshots.flatMap { snapshot ->
        val publicationThreshold =
            snapshot.publicationThreshold ?: return@flatMap emptyList()
        val awardRuleVersion =
            snapshot.awardRuleVersion ?: return@flatMap emptyList()
        val awardedRankingSize =
            snapshot.awardedRankingSize ?: return@flatMap emptyList()

        if (
            snapshot.totalEligiblePlayers < publicationThreshold ||
            awardedRankingSize <= 0
        ) {
            return@flatMap emptyList()
        }

        val decision = RankingAwardDecision(
            awardRuleVersion = awardRuleVersion,
            awardedRankingSize = awardedRankingSize,
        )

        snapshot.standings.mapNotNull { standing ->
            decision.tierFor(standing.rank)?.let { tier ->
                RankingAchievementRecord(
                    snapshot = snapshot,
                    standing = standing,
                    tier = tier,
                    awardRuleVersion = awardRuleVersion,
                )
            }
        }
    }
}

private suspend fun ApplicationCall.respondPrivateCacheableRanking(
    payload: Any,
    canonicalJson: String,
    maxAgeSeconds: Int,
) {
    require(maxAgeSeconds >= 0)

    val entityTag = canonicalJson.toPrivateRankingEntityTag()
    response.header(
        name = "Cache-Control",
        value = "private, max-age=$maxAgeSeconds, must-revalidate",
    )
    response.header(
        name = "ETag",
        value = entityTag,
    )
    response.header(
        name = "Vary",
        value = "Authorization, ${OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID}",
    )

    if (request.header("If-None-Match").matchesEntityTag(entityTag)) {
        respond(HttpStatusCode.NotModified)
        return
    }

    respond(HttpStatusCode.OK, payload)
}

private fun String.toPrivateRankingEntityTag(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(
        toByteArray(Charsets.UTF_8),
    )
    val fingerprint = digest.joinToString(separator = "") { byte ->
        "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
    }

    return "\"$fingerprint\""
}

private fun String?.matchesEntityTag(expected: String): Boolean {
    return this
        ?.split(',')
        ?.any { candidate ->
            val normalized = candidate.trim().removePrefix("W/")
            normalized == "*" || normalized == expected
        }
        ?: false
}
