package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.CancellationException

sealed interface OnlineRankingAchievementClientResult {
    data class Success(
        val response: PublicRankingAchievementGalleryResponseDto,
    ) : OnlineRankingAchievementClientResult

    data class Failure(
        val kind: OnlinePublicRankingFailureKind,
        val retryable: Boolean,
    ) : OnlineRankingAchievementClientResult
}

interface OnlineRankingAchievementClient {
    suspend fun fetch(
        offset: Int = 0,
        limit: Int = 50,
    ): OnlineRankingAchievementClientResult
}

class OnlineRankingAchievementRemoteClient(
    private val remoteApiClient: RemoteOnlineApiClient,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
) : OnlineRankingAchievementClient {
    override suspend fun fetch(
        offset: Int,
        limit: Int,
    ): OnlineRankingAchievementClientResult {
        if (offset < 0 || limit !in 1..100) {
            return protocolFailure()
        }

        val credential =
            sessionCredentialRepository.getValidCredentialOrNull()
                ?: return OnlineRankingAchievementClientResult.Failure(
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
            val response =
                remoteApiClient.fetchPublicRankingAchievements(
                    offset = offset,
                    limit = limit,
                )

            response.requireValidFor(
                requestedOffset = offset,
                requestedLimit = limit,
            )

            OnlineRankingAchievementClientResult.Success(
                response = response,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: OnlinePublicRankingHttpException) {
            when (error.statusCode) {
                401 -> OnlineRankingAchievementClientResult.Failure(
                    kind =
                        OnlinePublicRankingFailureKind
                            .AUTHENTICATION_REQUIRED,
                    retryable = false,
                )

                429 -> OnlineRankingAchievementClientResult.Failure(
                    kind = OnlinePublicRankingFailureKind.RATE_LIMITED,
                    retryable = true,
                )

                in 500..599 -> unavailableFailure()

                else -> protocolFailure()
            }
        } catch (_: HttpRequestTimeoutException) {
            unavailableFailure()
        } catch (_: IOException) {
            unavailableFailure()
        } catch (_: IllegalArgumentException) {
            protocolFailure()
        } catch (_: IllegalStateException) {
            protocolFailure()
        } catch (_: Throwable) {
            OnlineRankingAchievementClientResult.Failure(
                kind = OnlinePublicRankingFailureKind.UNKNOWN,
                retryable = false,
            )
        }
    }
}

private fun PublicRankingAchievementGalleryResponseDto.requireValidFor(
    requestedOffset: Int,
    requestedLimit: Int,
) {
    require(offset == requestedOffset)
    require(limit == requestedLimit)
    require(totalAchievements >= 0)
    require(totalChampionships in 0..totalAchievements)
    require(totalAwardedPlayers in 0..totalAchievements)
    require(available == (totalAchievements > 0))
    require(achievements.size <= limit)

    if (!available) {
        require(totalAchievements == 0)
        require(totalChampionships == 0)
        require(totalAwardedPlayers == 0)
        require(achievements.isEmpty())
    }

    val consumed = Math.addExact(
        offset.toLong(),
        achievements.size.toLong(),
    )

    if (hasMore) {
        require(consumed < totalAchievements.toLong())
    } else {
        require(consumed >= totalAchievements.toLong())
    }

    require(
        achievements
            .map { achievement ->
                achievement.competitorId to achievement.cycleId
            }
            .distinct()
            .size == achievements.size,
    )

    achievements.forEach { achievement ->
        achievement.requireValidOfficialAchievement()
    }
}

private fun PublicRankingAchievementDto
        .requireValidOfficialAchievement() {
    require(competitorId.startsWith("competitor-"))
    displayName?.let { name ->
        require(name.isNotBlank())
    }
    require(cycleId.isNotBlank())
    require(startsAtEpochMillis >= 0L)
    require(endsAtEpochMillis > startsAtEpochMillis)
    require(closedAtEpochMillis >= endsAtEpochMillis)
    require(rank > 0)
    require(awardRuleVersion == 1)
    require(diamondCount >= 0)
    require(goldCount >= 0)
    require(silverCount >= 0)
    require(bronzeCount >= 0)
    require(
        diamondCount + goldCount + silverCount + bronzeCount > 0,
    )

    when (awardTier) {
        PublicRankingAwardTierDto.DIAMOND -> {
            require(rank == 1)
            require(diamondCount > 0)
        }

        PublicRankingAwardTierDto.GOLD -> {
            require(rank in 2..5)
            require(goldCount > 0)
        }

        PublicRankingAwardTierDto.SILVER -> {
            require(rank in 6..10)
            require(silverCount > 0)
        }

        PublicRankingAwardTierDto.BRONZE -> {
            require(rank in 11..50)
            require(bronzeCount > 0)
        }
    }
}

private fun unavailableFailure():
    OnlineRankingAchievementClientResult.Failure {
    return OnlineRankingAchievementClientResult.Failure(
        kind = OnlinePublicRankingFailureKind.UNAVAILABLE,
        retryable = true,
    )
}

private fun protocolFailure():
    OnlineRankingAchievementClientResult.Failure {
    return OnlineRankingAchievementClientResult.Failure(
        kind = OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
        retryable = false,
    )
}