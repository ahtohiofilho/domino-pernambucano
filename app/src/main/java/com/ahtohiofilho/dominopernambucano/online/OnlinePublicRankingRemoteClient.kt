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

interface OnlinePublicRankingClient {
    suspend fun fetch(
        cycle: PublicRankingCycleDto,
        offset: Int = 0,
        limit: Int = 50,
    ): OnlinePublicRankingClientResult
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
        if (
            offset < 0 ||
            limit !in 1..100
        ) {
            return protocolFailure()
        }

        val credential =
            sessionCredentialRepository.getValidCredentialOrNull()
                ?: return OnlinePublicRankingClientResult.Failure(
                    kind =
                        OnlinePublicRankingFailureKind
                            .AUTHENTICATION_REQUIRED,
                    retryable = false,
                )

        remoteApiClient.setDevelopmentPlayerId(
            playerId = credential.playerId,
        )
        remoteApiClient.setBearerAccessToken(
            accessToken = credential.accessToken,
        )

        return try {
            val response = remoteApiClient.fetchPublicRanking(
                cycle = cycle,
                offset = offset,
                limit = limit,
            )

            response.requireValidFor(
                requestedCycle = cycle,
                requestedOffset = offset,
                requestedLimit = limit,
            )

            OnlinePublicRankingClientResult.Success(
                response = response,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: OnlinePublicRankingHttpException) {
            error.toClientFailure()
        } catch (_: HttpRequestTimeoutException) {
            unavailableFailure()
        } catch (_: IOException) {
            unavailableFailure()
        } catch (_: IllegalArgumentException) {
            protocolFailure()
        } catch (_: IllegalStateException) {
            protocolFailure()
        } catch (_: Throwable) {
            OnlinePublicRankingClientResult.Failure(
                kind = OnlinePublicRankingFailureKind.UNKNOWN,
                retryable = false,
            )
        }
    }
}

private fun PublicRankingResponseDto.requireValidFor(
    requestedCycle: PublicRankingCycleDto,
    requestedOffset: Int,
    requestedLimit: Int,
) {
    require(cycle == requestedCycle)
    require(offset == requestedOffset)
    require(limit == requestedLimit)
    require(cycleId.isNotBlank())
    require(rankingRuleVersion > 0)
    require(timeZoneId == "America/Recife")
    require(startsAtEpochMillis >= 0L)
    require(endsAtEpochMillis > startsAtEpochMillis)
    require(resultCount >= 0)
    require(totalEligiblePlayers >= 0)
    require(entries.size <= limit)

    val consumed = Math.addExact(
        offset.toLong(),
        entries.size.toLong(),
    )

    if (hasMore) {
        require(consumed < totalEligiblePlayers.toLong())
    } else {
        require(consumed >= totalEligiblePlayers.toLong())
    }

    val pageRanks = entries.map { entry -> entry.rank }
    require(pageRanks.distinct().size == pageRanks.size)
    require(pageRanks == pageRanks.sorted())

    val pageCompetitors = entries.map { entry ->
        entry.competitorId
    }
    require(
        pageCompetitors.distinct().size == pageCompetitors.size,
    )

    entries.forEach { entry ->
        entry.requireValid()
    }
    viewer?.requireValid()
}

private fun PublicRankingEntryDto.requireValid() {
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
}

private fun OnlinePublicRankingHttpException.toClientFailure():
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

        in 500..599 -> unavailableFailure()

        else -> protocolFailure()
    }
}

private fun unavailableFailure():
    OnlinePublicRankingClientResult.Failure {
    return OnlinePublicRankingClientResult.Failure(
        kind = OnlinePublicRankingFailureKind.UNAVAILABLE,
        retryable = true,
    )
}

private fun protocolFailure():
    OnlinePublicRankingClientResult.Failure {
    return OnlinePublicRankingClientResult.Failure(
        kind = OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
        retryable = false,
    )
}
