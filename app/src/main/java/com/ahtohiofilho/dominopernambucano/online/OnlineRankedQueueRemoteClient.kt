package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

private const val MIN_RANKED_QUEUE_POLL_INTERVAL_MILLIS = 1_000L
private const val MAX_RANKED_QUEUE_POLL_INTERVAL_MILLIS = 10_000L
private const val DEFAULT_RANKED_QUEUE_POLL_INTERVAL_MILLIS = 2_000L
private const val DEFAULT_RANKED_QUEUE_MAX_CONSECUTIVE_FAILURES = 3

class OnlineRankedQueueHttpException(
    val statusCode: Int,
) : IllegalStateException(
    "A fila rankeada respondeu com HTTP $statusCode.",
)

enum class OnlineRankedQueueFailureKind {
    AUTHENTICATION_REQUIRED,
    ACCOUNT_REQUIRED,
    RATE_LIMITED,
    UNAVAILABLE,
    PROTOCOL_ERROR,
    UNKNOWN,
}

sealed interface OnlineRankedQueueState {
    data class Waiting(
        val queuePosition: Int,
    ) : OnlineRankedQueueState

    data class Matched(
        val matchId: String,
        // Compatibilidade local/testes. O contrato HTTP de produção não
        // revela assento antes da ativação da partida.
        val localSeatIndex: Int? = null,
    ) : OnlineRankedQueueState

    data object NotQueued : OnlineRankedQueueState
}

sealed interface OnlineRankedQueueClientResult {
    data class Success(
        val state: OnlineRankedQueueState,
    ) : OnlineRankedQueueClientResult

    data class Failure(
        val kind: OnlineRankedQueueFailureKind,
        val retryable: Boolean,
    ) : OnlineRankedQueueClientResult
}

data class OnlineRankedQueuePollingPolicy(
    val intervalMillis: Long =
        DEFAULT_RANKED_QUEUE_POLL_INTERVAL_MILLIS,
    val maxConsecutiveRetryableFailures: Int =
        DEFAULT_RANKED_QUEUE_MAX_CONSECUTIVE_FAILURES,
) {
    init {
        require(
            intervalMillis in
                    MIN_RANKED_QUEUE_POLL_INTERVAL_MILLIS..
                    MAX_RANKED_QUEUE_POLL_INTERVAL_MILLIS
        ) {
            "O polling rankeado deve ficar entre 1 e 10 segundos."
        }
        require(maxConsecutiveRetryableFailures in 1..10) {
            "O limite de falhas consecutivas deve ficar entre 1 e 10."
        }
    }
}

interface OnlineRankedQueueClient {
    suspend fun enqueue(
        playerName: String,
    ): OnlineRankedQueueClientResult

    suspend fun resume(): OnlineRankedQueueClientResult

    suspend fun cancel(): OnlineRankedQueueClientResult

    fun poll(
        policy: OnlineRankedQueuePollingPolicy =
            OnlineRankedQueuePollingPolicy(),
    ): Flow<OnlineRankedQueueClientResult>
}

/**
 * Camada Android sem UI para entrada, retomada, polling e cancelamento.
 *
 * Cancelar a coleta interrompe o polling sem retirar o jogador da fila.
 * Uma nova instância pode chamar resume() ou poll(); a primeira operação é
 * sempre GET, sem um POST implícito.
 */
class OnlineRankedQueueRemoteClient(
    private val remoteApiClient: RemoteOnlineApiClient,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
    private val delayProvider: suspend (Long) -> Unit = { millis ->
        delay(millis)
    },
) : OnlineRankedQueueClient {
    override suspend fun enqueue(
        playerName: String,
    ): OnlineRankedQueueClientResult {
        val normalizedName = playerName.trim()
        if (normalizedName.isBlank()) {
            return protocolFailure()
        }

        return executeAccountRequest {
            remoteApiClient.enqueuePublicRankedQueue(
                request = PublicRankedQueueEnterRequestDto(
                    playerName = normalizedName,
                ),
            )
        }
    }

    override suspend fun resume(): OnlineRankedQueueClientResult {
        return executeAccountRequest {
            remoteApiClient.fetchPublicRankedQueueStatus()
        }
    }

    override suspend fun cancel(): OnlineRankedQueueClientResult {
        return executeAccountRequest {
            remoteApiClient.cancelPublicRankedQueue()
        }
    }

    override fun poll(
        policy: OnlineRankedQueuePollingPolicy,
    ): Flow<OnlineRankedQueueClientResult> {
        return flow {
            var consecutiveRetryableFailures = 0

            while (currentCoroutineContext().isActive) {
                val result = resume()
                emit(result)

                when (result) {
                    is OnlineRankedQueueClientResult.Success -> {
                        consecutiveRetryableFailures = 0
                        if (
                            result.state !is
                            OnlineRankedQueueState.Waiting
                        ) {
                            return@flow
                        }
                    }

                    is OnlineRankedQueueClientResult.Failure -> {
                        if (!result.retryable) {
                            return@flow
                        }

                        consecutiveRetryableFailures += 1
                        if (
                            consecutiveRetryableFailures >=
                            policy.maxConsecutiveRetryableFailures
                        ) {
                            return@flow
                        }
                    }
                }

                delayProvider(policy.intervalMillis)
            }
        }
    }

    private suspend fun executeAccountRequest(
        request:
            suspend () -> PublicRankedQueueHttpResponseDto,
    ): OnlineRankedQueueClientResult {
        val credential =
            sessionCredentialRepository.getValidCredentialOrNull()
                ?: return OnlineRankedQueueClientResult.Failure(
                    kind =
                        OnlineRankedQueueFailureKind
                            .AUTHENTICATION_REQUIRED,
                    retryable = false,
                )

        if (
            credential.sessionKind != OnlineSessionKind.ACCOUNT ||
            credential.accountId.isNullOrBlank()
        ) {
            return OnlineRankedQueueClientResult.Failure(
                kind = OnlineRankedQueueFailureKind.ACCOUNT_REQUIRED,
                retryable = false,
            )
        }

        remoteApiClient.setDevelopmentPlayerId(
            playerId = null,
        )
        remoteApiClient.setBearerAccessToken(
            accessToken = credential.accessToken,
        )

        return try {
            OnlineRankedQueueClientResult.Success(
                state = request().toClientState(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: OnlineRankedQueueHttpException) {
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
            OnlineRankedQueueClientResult.Failure(
                kind = OnlineRankedQueueFailureKind.UNKNOWN,
                retryable = false,
            )
        }
    }
}

private fun PublicRankedQueueHttpResponseDto.toClientState():
    OnlineRankedQueueState {
    return when (status) {
        PublicRankedQueueHttpStatus.WAITING -> {
            val requiredPosition = requireNotNull(queuePosition)
            require(requiredPosition > 0)
            require(matchId == null)
            require(localSeatIndex == null)

            OnlineRankedQueueState.Waiting(
                queuePosition = requiredPosition,
            )
        }

        PublicRankedQueueHttpStatus.MATCHED -> {
            val requiredMatchId = requireNotNull(matchId)
            require(requiredMatchId.isNotBlank())
            require(localSeatIndex == null)
            require(queuePosition == null)

            OnlineRankedQueueState.Matched(
                matchId = requiredMatchId,
            )
        }

        PublicRankedQueueHttpStatus.NOT_QUEUED -> {
            require(queuePosition == null)
            require(matchId == null)
            require(localSeatIndex == null)

            OnlineRankedQueueState.NotQueued
        }
    }
}

private fun OnlineRankedQueueHttpException.toClientFailure():
    OnlineRankedQueueClientResult.Failure {
    return when (statusCode) {
        401 -> OnlineRankedQueueClientResult.Failure(
            kind =
                OnlineRankedQueueFailureKind
                    .AUTHENTICATION_REQUIRED,
            retryable = false,
        )

        403 -> OnlineRankedQueueClientResult.Failure(
            kind = OnlineRankedQueueFailureKind.ACCOUNT_REQUIRED,
            retryable = false,
        )

        429 -> OnlineRankedQueueClientResult.Failure(
            kind = OnlineRankedQueueFailureKind.RATE_LIMITED,
            retryable = true,
        )

        in 500..599 -> unavailableFailure()

        else -> protocolFailure()
    }
}

private fun unavailableFailure():
    OnlineRankedQueueClientResult.Failure {
    return OnlineRankedQueueClientResult.Failure(
        kind = OnlineRankedQueueFailureKind.UNAVAILABLE,
        retryable = true,
    )
}

private fun protocolFailure():
    OnlineRankedQueueClientResult.Failure {
    return OnlineRankedQueueClientResult.Failure(
        kind = OnlineRankedQueueFailureKind.PROTOCOL_ERROR,
        retryable = false,
    )
}
