package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.CancellationException

class OnlinePublicRankingHttpException(
    val statusCode: Int,
) : IllegalStateException(
    "A classificação pública respondeu com HTTP $statusCode.",
)

enum class OnlinePublicRankingFailureKind {
    AUTHENTICATION_REQUIRED,
    RATE_LIMITED,
    UNAVAILABLE,
    PROTOCOL_ERROR,
    UNKNOWN,
}

sealed interface OnlinePublicRankingClientResult {
    data class Success(
        val response: PublicRankingResponseDto,
    ) : OnlinePublicRankingClientResult

    data class Failure(
        val kind: OnlinePublicRankingFailureKind,
        val retryable: Boolean,
    ) : OnlinePublicRankingClientResult
}

sealed interface OnlinePublicRankingCyclesClientResult {
    data class Success(
        val response: PublicRankingCyclesResponseDto,
    ) : OnlinePublicRankingCyclesClientResult

    data class Failure(
        val kind: OnlinePublicRankingFailureKind,
        val retryable: Boolean,
    ) : OnlinePublicRankingCyclesClientResult
}

interface OnlinePublicRankingClient {
    suspend fun fetch(
        cycle: PublicRankingCycleDto,
        offset: Int = 0,
        limit: Int = 50,
    ): OnlinePublicRankingClientResult

    suspend fun fetchHistorical(
        cycle: PublicRankingCycleDto,
        cycleId: String,
        offset: Int = 0,
        limit: Int = 50,
    ): OnlinePublicRankingClientResult {
        throw UnsupportedOperationException(
            "Historical ranking is not configured for this client.",
        )
    }

    suspend fun fetchClosedCycles(
        cycle: PublicRankingCycleDto,
        offset: Int = 0,
        limit: Int = 50,
    ): OnlinePublicRankingCyclesClientResult {
        throw UnsupportedOperationException(
            "Closed ranking cycles are not configured for this client.",
        )
    }
}

class OnlinePublicRankingRemoteClient(
    private val remoteApiClient: RemoteOnlineApiClient,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
) : OnlinePublicRankingClient {
    override suspend fun fetch(
        cycle: PublicRankingCycleDto,
        offset: Int,
        limit: Int,
    ): OnlinePublicRankingClientResult {
        return fetchRanking(
            cycle = cycle,
            cycleId = null,
            offset = offset,
            limit = limit,
        )
    }

    override suspend fun fetchHistorical(
        cycle: PublicRankingCycleDto,
        cycleId: String,
        offset: Int,
        limit: Int,
    ): OnlinePublicRankingClientResult {
        return fetchRanking(
            cycle = cycle,
            cycleId = cycleId,
            offset = offset,
            limit = limit,
        )
    }

    private suspend fun fetchRanking(
        cycle: PublicRankingCycleDto,
        cycleId: String?,
        offset: Int,
        limit: Int,
    ): OnlinePublicRankingClientResult {
        val normalizedCycleId = cycleId?.trim()

        if (
            offset < 0 ||
            limit !in 1..100 ||
            (cycleId != null && normalizedCycleId.isNullOrBlank())
        ) {
            return protocolRankingFailure()
        }

        val credential = validCredentialOrNull()
            ?: return OnlinePublicRankingClientResult.Failure(
                kind =
                    OnlinePublicRankingFailureKind
                        .AUTHENTICATION_REQUIRED,
                retryable = false,
            )

        applyCredential(credential)

        return try {
            val response = if (normalizedCycleId == null) {
                remoteApiClient.fetchPublicRanking(
                    cycle = cycle,
                    offset = offset,
                    limit = limit,
                )
            } else {
                remoteApiClient.fetchHistoricalPublicRanking(
                    cycle = cycle,
                    cycleId = normalizedCycleId,
                    offset = offset,
                    limit = limit,
                )
            }

            response.requireValidFor(
                requestedCycle = cycle,
                requestedOffset = offset,
                requestedLimit = limit,
                requestedCycleId = normalizedCycleId,
            )

            OnlinePublicRankingClientResult.Success(
                response = response,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: OnlinePublicRankingHttpException) {
            error.toRankingClientFailure()
        } catch (_: HttpRequestTimeoutException) {
            unavailableRankingFailure()
        } catch (_: IOException) {
            unavailableRankingFailure()
        } catch (_: IllegalArgumentException) {
            protocolRankingFailure()
        } catch (_: IllegalStateException) {
            protocolRankingFailure()
        } catch (_: Throwable) {
            OnlinePublicRankingClientResult.Failure(
                kind = OnlinePublicRankingFailureKind.UNKNOWN,
                retryable = false,
            )
        }
    }

    override suspend fun fetchClosedCycles(
        cycle: PublicRankingCycleDto,
        offset: Int,
        limit: Int,
    ): OnlinePublicRankingCyclesClientResult {
        if (offset < 0 || limit !in 1..100) {
            return protocolCyclesFailure()
        }

        val credential = validCredentialOrNull()
            ?: return OnlinePublicRankingCyclesClientResult.Failure(
                kind =
                    OnlinePublicRankingFailureKind
                        .AUTHENTICATION_REQUIRED,
                retryable = false,
            )

        applyCredential(credential)

        return try {
            val response = remoteApiClient.fetchPublicRankingCycles(
                cycle = cycle,
                offset = offset,
                limit = limit,
            )

            response.requireValidFor(
                requestedCycle = cycle,
                requestedOffset = offset,
                requestedLimit = limit,
            )

            OnlinePublicRankingCyclesClientResult.Success(
                response = response,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: OnlinePublicRankingHttpException) {
            error.toCyclesClientFailure()
        } catch (_: HttpRequestTimeoutException) {
            unavailableCyclesFailure()
        } catch (_: IOException) {
            unavailableCyclesFailure()
        } catch (_: IllegalArgumentException) {
            protocolCyclesFailure()
        } catch (_: IllegalStateException) {
            protocolCyclesFailure()
        } catch (_: Throwable) {
            OnlinePublicRankingCyclesClientResult.Failure(
                kind = OnlinePublicRankingFailureKind.UNKNOWN,
                retryable = false,
            )
        }
    }

    private fun validCredentialOrNull(): OnlineSessionCredential? {
        return sessionCredentialRepository.getValidCredentialOrNull()
    }

    private fun applyCredential(
        credential: OnlineSessionCredential,
    ) {
        remoteApiClient.setDevelopmentPlayerId(
            playerId = credential.playerId,
        )
        remoteApiClient.setBearerAccessToken(
            accessToken = credential.accessToken,
        )
    }
}

private const val SUPPORTED_RANKING_AWARD_RULE_VERSION = 1
private const val MAXIMUM_AWARDED_RANKING_SIZE = 50

private fun PublicRankingResponseDto.requireValidFor(
    requestedCycle: PublicRankingCycleDto,
    requestedOffset: Int,
    requestedLimit: Int,
    requestedCycleId: String?,
) {
    require(cycle == requestedCycle)
    require(offset == requestedOffset)
    require(limit == requestedLimit)
    require(cycleId.isNotBlank())
    require(requestedCycleId == null || cycleId == requestedCycleId)
    require(rankingRuleVersion > 0)
    require(timeZoneId == "America/Recife")
    require(startsAtEpochMillis >= 0L)
    require(endsAtEpochMillis > startsAtEpochMillis)
    require(resultCount >= 0)
    require(totalEligiblePlayers >= 0)
    require(retainedRankingSize in 0..totalEligiblePlayers)
    require(retentionPolicyVersion >= 0)
    require(!(isLegacyTruncated && isRetentionLimited))
    require(publicationThreshold > 0)

    val expectedRemaining = (
        publicationThreshold.toLong() -
            totalEligiblePlayers.toLong()
    ).coerceAtLeast(0L)

    require(expectedRemaining <= Int.MAX_VALUE.toLong())
    require(
        eligiblePlayersRemaining ==
            expectedRemaining.toInt(),
    )

    when (publicationStatus) {
        PublicRankingPublicationStatusDto.BELOW_THRESHOLD -> {
            require(totalEligiblePlayers < publicationThreshold)
            require(eligiblePlayersRemaining > 0)
            require(!awardsEligible)
            require(entries.isEmpty())
            require(viewer == null)
            require(!hasMore)
        }

        PublicRankingPublicationStatusDto.PUBLISHED -> {
            require(totalEligiblePlayers >= publicationThreshold)
            require(eligiblePlayersRemaining == 0)
            require(awardsEligible)
        }
    }

    require(
        awardRuleVersion ==
            SUPPORTED_RANKING_AWARD_RULE_VERSION,
    )
    val expectedAwardedRankingSize =
        if (
            requestedCycleId != null &&
            publicationStatus ==
                PublicRankingPublicationStatusDto.PUBLISHED
        ) {
            minOf(
                retainedRankingSize,
                MAXIMUM_AWARDED_RANKING_SIZE,
            )
        } else {
            0
        }
    require(awardedRankingSize == expectedAwardedRankingSize)

    require(entries.size <= limit)

    if (requestedCycleId == null) {
        require(!isClosed)
        require(closedAtEpochMillis == null)
        require(retentionPolicyVersion == 0)
        require(!isLegacyTruncated)
        require(!isRetentionLimited)
    } else {
        require(isClosed)
        val closedAt = requireNotNull(closedAtEpochMillis)
        require(closedAt >= endsAtEpochMillis)
        require(
            isLegacyTruncated ==
                (
                    retentionPolicyVersion == 0 &&
                        totalEligiblePlayers > retainedRankingSize
                ),
        )
        require(
            isRetentionLimited ==
                (
                    retentionPolicyVersion > 0 &&
                        totalEligiblePlayers > retainedRankingSize
                ),
        )
    }

    val consumed = Math.addExact(
        offset.toLong(),
        entries.size.toLong(),
    )

    if (hasMore) {
        require(consumed < retainedRankingSize.toLong())
    } else {
        require(consumed >= retainedRankingSize.toLong())
    }

    val expectedRanks = (
        (offset + 1) until (offset + entries.size + 1)
    ).toList()
    val pageRanks = entries.map { entry -> entry.rank }
    require(pageRanks == expectedRanks)

    val pageCompetitors = entries.map { entry ->
        entry.competitorId
    }
    require(
        pageCompetitors.distinct().size == pageCompetitors.size,
    )

    entries.forEach { entry ->
        entry.requireValid(
            awardedRankingSize = awardedRankingSize,
        )
    }
    viewer?.let { entry ->
        entry.requireValid(
            awardedRankingSize = awardedRankingSize,
        )
        require(entry.rank <= retainedRankingSize)
    }
}

private fun PublicRankingCyclesResponseDto.requireValidFor(
    requestedCycle: PublicRankingCycleDto,
    requestedOffset: Int,
    requestedLimit: Int,
) {
    require(cycle == requestedCycle)
    require(offset == requestedOffset)
    require(limit == requestedLimit)
    require(totalClosedCycles >= 0)
    require(cycles.size <= limit)

    val consumed = Math.addExact(
        offset.toLong(),
        cycles.size.toLong(),
    )

    if (hasMore) {
        require(consumed < totalClosedCycles.toLong())
    } else {
        require(consumed >= totalClosedCycles.toLong())
    }

    val cycleIds = cycles.map { summary -> summary.cycleId }
    require(cycleIds.distinct().size == cycleIds.size)

    cycles.forEach { summary ->
        summary.requireValidFor(requestedCycle)
    }

    require(
        cycles.zipWithNext().all { (first, second) ->
            first.endsAtEpochMillis > second.endsAtEpochMillis ||
                (
                    first.endsAtEpochMillis ==
                        second.endsAtEpochMillis &&
                        first.cycleId >= second.cycleId
                )
        },
    )
}

private fun PublicRankingCycleSummaryDto.requireValidFor(
    requestedCycle: PublicRankingCycleDto,
) {
    require(cycle == requestedCycle)
    require(cycleId.isNotBlank())
    require(rankingRuleVersion > 0)
    require(timeZoneId == "America/Recife")
    require(startsAtEpochMillis >= 0L)
    require(endsAtEpochMillis > startsAtEpochMillis)
    require(closedAtEpochMillis >= endsAtEpochMillis)
    require(resultCount >= 0)
    require(totalEligiblePlayers >= 0)
    require(retainedRankingSize in 0..totalEligiblePlayers)
    require(retentionPolicyVersion >= 0)
    require(!(isLegacyTruncated && isRetentionLimited))
    require(
        isLegacyTruncated ==
            (
                retentionPolicyVersion == 0 &&
                    totalEligiblePlayers > retainedRankingSize
            ),
    )
    require(
        isRetentionLimited ==
            (
                retentionPolicyVersion > 0 &&
                    totalEligiblePlayers > retainedRankingSize
            ),
    )
    require(publicationThreshold > 0)

    val expectedRemaining = (
        publicationThreshold.toLong() -
            totalEligiblePlayers.toLong()
    ).coerceAtLeast(0L)

    require(expectedRemaining <= Int.MAX_VALUE.toLong())
    require(
        eligiblePlayersRemaining ==
            expectedRemaining.toInt(),
    )

    when (publicationStatus) {
        PublicRankingPublicationStatusDto.BELOW_THRESHOLD -> {
            require(totalEligiblePlayers < publicationThreshold)
            require(eligiblePlayersRemaining > 0)
            require(!awardsEligible)
        }

        PublicRankingPublicationStatusDto.PUBLISHED -> {
            require(totalEligiblePlayers >= publicationThreshold)
            require(eligiblePlayersRemaining == 0)
            require(awardsEligible)
        }
    }

    require(
        awardRuleVersion ==
            SUPPORTED_RANKING_AWARD_RULE_VERSION,
    )
    val expectedAwardedRankingSize =
        if (
            publicationStatus ==
                PublicRankingPublicationStatusDto.PUBLISHED
        ) {
            minOf(
                retainedRankingSize,
                MAXIMUM_AWARDED_RANKING_SIZE,
            )
        } else {
            0
        }
    require(awardedRankingSize == expectedAwardedRankingSize)
}

private fun PublicRankingEntryDto.requireValid(
    awardedRankingSize: Int,
) {
    require(rank > 0)
    require(competitorId.isNotBlank())
    require(victories >= 0L)
    require(games > 0L)
    require(victories <= games)
    require(
        scoreNumerator == Math.addExact(
            victories,
            1L,
        ),
    )
    require(
        scoreDenominator == Math.addExact(
            games,
            2L,
        ),
    )
    require(individualPoints >= 0L)
    require(touchesGiven >= 0L)
    require(automaticRounds >= 0L)
    require(
        awardTier ==
            expectedAwardTier(
                rank = rank,
                awardedRankingSize = awardedRankingSize,
            ),
    )
}

private fun expectedAwardTier(
    rank: Int,
    awardedRankingSize: Int,
): PublicRankingAwardTierDto? {
    require(rank > 0)
    require(awardedRankingSize >= 0)
    require(
        awardedRankingSize <=
            MAXIMUM_AWARDED_RANKING_SIZE,
    )

    if (rank > awardedRankingSize) {
        return null
    }

    return when (rank) {
        1 -> PublicRankingAwardTierDto.DIAMOND
        in 2..5 -> PublicRankingAwardTierDto.GOLD
        in 6..10 -> PublicRankingAwardTierDto.SILVER
        in 11..50 -> PublicRankingAwardTierDto.BRONZE
        else -> null
    }
}

private fun OnlinePublicRankingHttpException.toRankingClientFailure():
    OnlinePublicRankingClientResult.Failure {
    return when (statusCode) {
        401 -> OnlinePublicRankingClientResult.Failure(
            kind =
                OnlinePublicRankingFailureKind
                    .AUTHENTICATION_REQUIRED,
            retryable = false,
        )

        429 -> OnlinePublicRankingClientResult.Failure(
            kind = OnlinePublicRankingFailureKind.RATE_LIMITED,
            retryable = true,
        )

        in 500..599 -> unavailableRankingFailure()

        else -> protocolRankingFailure()
    }
}

private fun OnlinePublicRankingHttpException.toCyclesClientFailure():
    OnlinePublicRankingCyclesClientResult.Failure {
    return when (statusCode) {
        401 -> OnlinePublicRankingCyclesClientResult.Failure(
            kind =
                OnlinePublicRankingFailureKind
                    .AUTHENTICATION_REQUIRED,
            retryable = false,
        )

        429 -> OnlinePublicRankingCyclesClientResult.Failure(
            kind = OnlinePublicRankingFailureKind.RATE_LIMITED,
            retryable = true,
        )

        in 500..599 -> unavailableCyclesFailure()

        else -> protocolCyclesFailure()
    }
}

private fun unavailableRankingFailure():
    OnlinePublicRankingClientResult.Failure {
    return OnlinePublicRankingClientResult.Failure(
        kind = OnlinePublicRankingFailureKind.UNAVAILABLE,
        retryable = true,
    )
}

private fun unavailableCyclesFailure():
    OnlinePublicRankingCyclesClientResult.Failure {
    return OnlinePublicRankingCyclesClientResult.Failure(
        kind = OnlinePublicRankingFailureKind.UNAVAILABLE,
        retryable = true,
    )
}

private fun protocolRankingFailure():
    OnlinePublicRankingClientResult.Failure {
    return OnlinePublicRankingClientResult.Failure(
        kind = OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
        retryable = false,
    )
}

private fun protocolCyclesFailure():
    OnlinePublicRankingCyclesClientResult.Failure {
    return OnlinePublicRankingCyclesClientResult.Failure(
        kind = OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
        retryable = false,
    )
}
