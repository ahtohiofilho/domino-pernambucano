package com.ahtohiofilho.dominopernambucano.online

import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRankedQueueRemoteClientTest {
    @Test
    fun anonymous_session_is_rejected_before_any_remote_call() =
        runBlocking {
            val api = FakeRankedQueueApi()
            val client = createClient(
                api = api,
                credential = anonymousCredential(),
            )

            val result = client.enqueue("Visitante")

            assertEquals(
                OnlineRankedQueueClientResult.Failure(
                    kind = OnlineRankedQueueFailureKind.ACCOUNT_REQUIRED,
                    retryable = false,
                ),
                result,
            )
            assertEquals(0, api.totalCalls)
        }

    @Test
    fun missing_account_requires_authentication_locally() =
        runBlocking {
            val api = FakeRankedQueueApi()
            val client = createClient(
                api = api,
                credential = null,
            )

            val result = client.resume()

            assertEquals(
                OnlineRankedQueueClientResult.Failure(
                    kind =
                        OnlineRankedQueueFailureKind
                            .AUTHENTICATION_REQUIRED,
                    retryable = false,
                ),
                result,
            )
            assertEquals(0, api.totalCalls)
        }

    @Test
    fun account_session_is_applied_and_waiting_is_mapped() =
        runBlocking {
            val api = FakeRankedQueueApi().apply {
                enqueueResponses += Result.success(
                    PublicRankedQueueHttpResponseDto(
                        status = PublicRankedQueueHttpStatus.WAITING,
                        queuePosition = 4,
                    ),
                )
            }
            val client = createClient(
                api = api,
                credential = accountCredential(),
            )

            val result = client.enqueue("  Jogador  ")

            assertEquals(
                OnlineRankedQueueClientResult.Success(
                    state = OnlineRankedQueueState.Waiting(
                        queuePosition = 4,
                    ),
                ),
                result,
            )
            assertEquals("Jogador", api.lastPlayerName)
            assertEquals("account-token", api.observedBearerAccessToken)
            assertEquals(null, api.observedDevelopmentPlayerId)
        }

    @Test
    fun resume_after_recreation_starts_with_get_and_preserves_match() =
        runBlocking {
            val api = FakeRankedQueueApi().apply {
                statusResponses += Result.success(
                    PublicRankedQueueHttpResponseDto(
                        status = PublicRankedQueueHttpStatus.MATCHED,
                        matchId = "match-recovered",
                    ),
                )
            }
            val client = createClient(
                api = api,
                credential = accountCredential(),
            )

            val result = client.resume()

            assertEquals(
                OnlineRankedQueueClientResult.Success(
                    state = OnlineRankedQueueState.Matched(
                        matchId = "match-recovered",
                    ),
                ),
                result,
            )
            assertEquals(0, api.enqueueCalls)
            assertEquals(1, api.statusCalls)
        }

    @Test
    fun polling_is_bounded_and_stops_when_matched() =
        runBlocking {
            val delays = mutableListOf<Long>()
            val api = FakeRankedQueueApi().apply {
                statusResponses += Result.success(
                    PublicRankedQueueHttpResponseDto(
                        status = PublicRankedQueueHttpStatus.WAITING,
                        queuePosition = 1,
                    ),
                )
                statusResponses += Result.success(
                    PublicRankedQueueHttpResponseDto(
                        status = PublicRankedQueueHttpStatus.MATCHED,
                        matchId = "match-1",
                    ),
                )
            }
            val client = createClient(
                api = api,
                credential = accountCredential(),
                delayProvider = { millis ->
                    delays += millis
                },
            )

            val results = client.poll(
                policy = OnlineRankedQueuePollingPolicy(
                    intervalMillis = 1_000L,
                    maxConsecutiveRetryableFailures = 3,
                ),
            ).toList()

            assertEquals(2, results.size)
            assertTrue(
                (results.first() as
                    OnlineRankedQueueClientResult.Success)
                    .state is OnlineRankedQueueState.Waiting,
            )
            assertEquals(
                OnlineRankedQueueState.Matched(
                    matchId = "match-1",
                ),
                (results.last() as
                    OnlineRankedQueueClientResult.Success).state,
            )
            assertEquals(listOf(1_000L), delays)
        }

    @Test
    fun polling_stops_after_retryable_failure_budget() =
        runBlocking {
            val api = FakeRankedQueueApi().apply {
                repeat(3) {
                    statusResponses += Result.failure(
                        OnlineRankedQueueHttpException(429),
                    )
                }
            }
            val client = createClient(
                api = api,
                credential = accountCredential(),
                delayProvider = {},
            )

            val results = client.poll(
                policy = OnlineRankedQueuePollingPolicy(
                    intervalMillis = 1_000L,
                    maxConsecutiveRetryableFailures = 3,
                ),
            ).toList()

            assertEquals(3, results.size)
            assertTrue(
                results.all { item ->
                    item ==
                        OnlineRankedQueueClientResult.Failure(
                            kind =
                                OnlineRankedQueueFailureKind
                                    .RATE_LIMITED,
                            retryable = true,
                        )
                },
            )
            assertEquals(3, api.statusCalls)
        }

    @Test
    fun http_and_network_failures_are_classified() =
        runBlocking {
            val scenarios = listOf(
                OnlineRankedQueueHttpException(401) to
                        OnlineRankedQueueFailureKind
                            .AUTHENTICATION_REQUIRED,
                OnlineRankedQueueHttpException(403) to
                        OnlineRankedQueueFailureKind.ACCOUNT_REQUIRED,
                OnlineRankedQueueHttpException(429) to
                        OnlineRankedQueueFailureKind.RATE_LIMITED,
                OnlineRankedQueueHttpException(503) to
                        OnlineRankedQueueFailureKind.UNAVAILABLE,
                IOException("offline") to
                        OnlineRankedQueueFailureKind.UNAVAILABLE,
            )

            scenarios.forEach { (error, expectedKind) ->
                val api = FakeRankedQueueApi().apply {
                    statusResponses += Result.failure(error)
                }
                val client = createClient(
                    api = api,
                    credential = accountCredential(),
                )

                val result =
                    client.resume() as
                        OnlineRankedQueueClientResult.Failure

                assertEquals(expectedKind, result.kind)
                assertEquals(
                    expectedKind ==
                            OnlineRankedQueueFailureKind.RATE_LIMITED ||
                            expectedKind ==
                            OnlineRankedQueueFailureKind.UNAVAILABLE,
                    result.retryable,
                )
            }
        }

    @Test
    fun invalid_wire_state_is_blocked_as_protocol_error() =
        runBlocking {
            val api = FakeRankedQueueApi().apply {
                statusResponses += Result.success(
                    PublicRankedQueueHttpResponseDto(
                        status = PublicRankedQueueHttpStatus.MATCHED,
                        matchId = null,
                        localSeatIndex = 9,
                    ),
                )
            }
            val client = createClient(
                api = api,
                credential = accountCredential(),
            )

            val result = client.resume()

            assertEquals(
                OnlineRankedQueueClientResult.Failure(
                    kind = OnlineRankedQueueFailureKind.PROTOCOL_ERROR,
                    retryable = false,
                ),
                result,
            )
        }

    @Test
    fun cancel_preserves_matched_projection() =
        runBlocking {
            val api = FakeRankedQueueApi().apply {
                cancelResponses += Result.success(
                    PublicRankedQueueHttpResponseDto(
                        status = PublicRankedQueueHttpStatus.MATCHED,
                        matchId = "match-conflict",
                    ),
                )
            }
            val client = createClient(
                api = api,
                credential = accountCredential(),
            )

            val result = client.cancel()

            assertEquals(
                OnlineRankedQueueClientResult.Success(
                    state = OnlineRankedQueueState.Matched(
                        matchId = "match-conflict",
                    ),
                ),
                result,
            )
        }

    private fun createClient(
        api: FakeRankedQueueApi,
        credential: OnlineSessionCredential?,
        delayProvider: suspend (Long) -> Unit = {},
    ): OnlineRankedQueueRemoteClient {
        return OnlineRankedQueueRemoteClient(
            remoteApiClient = api,
            sessionCredentialRepository =
                OnlineSessionCredentialRepository(
                    store = InMemoryCredentialStore(credential),
                    nowEpochMillis = {
                        1_000L
                    },
                ),
            delayProvider = delayProvider,
        )
    }

    private fun accountCredential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            playerId = "player-1",
            accountId = "account-1",
            accessToken = "account-token",
            expiresAtEpochMillis = 10_000L,
        )
    }

    private fun anonymousCredential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ANONYMOUS,
            playerId = "visitor-1",
            accessToken = "visitor-token",
            expiresAtEpochMillis = 10_000L,
        )
    }

    private class InMemoryCredentialStore(
        private var credential: OnlineSessionCredential?,
    ) : OnlineSessionCredentialStore {
        override fun read(): OnlineSessionCredential? = credential

        override fun write(
            credential: OnlineSessionCredential,
        ): Boolean {
            this.credential = credential
            return true
        }

        override fun clear(): Boolean {
            credential = null
            return true
        }
    }

    private class FakeRankedQueueApi : RemoteOnlineApiClient {
        val enqueueResponses =
            ArrayDeque<Result<PublicRankedQueueHttpResponseDto>>()
        val statusResponses =
            ArrayDeque<Result<PublicRankedQueueHttpResponseDto>>()
        val cancelResponses =
            ArrayDeque<Result<PublicRankedQueueHttpResponseDto>>()

        var observedBearerAccessToken: String? = null
        var observedDevelopmentPlayerId: String? = "legacy-player"
        var lastPlayerName: String? = null
        var enqueueCalls: Int = 0
        var statusCalls: Int = 0
        var cancelCalls: Int = 0

        val totalCalls: Int
            get() = enqueueCalls + statusCalls + cancelCalls

        override fun setBearerAccessToken(
            accessToken: String?,
        ) {
            observedBearerAccessToken = accessToken
        }

        override fun setDevelopmentPlayerId(
            playerId: String?,
        ) {
            observedDevelopmentPlayerId = playerId
        }

        override suspend fun enqueuePublicRankedQueue(
            request: PublicRankedQueueEnterRequestDto,
        ): PublicRankedQueueHttpResponseDto {
            enqueueCalls += 1
            lastPlayerName = request.playerName
            return enqueueResponses.removeFirst().getOrThrow()
        }

        override suspend fun fetchPublicRankedQueueStatus():
            PublicRankedQueueHttpResponseDto {
            statusCalls += 1
            return statusResponses.removeFirst().getOrThrow()
        }

        override suspend fun cancelPublicRankedQueue():
            PublicRankedQueueHttpResponseDto {
            cancelCalls += 1
            return cancelResponses.removeFirst().getOrThrow()
        }

        override suspend fun createRoom(
            request: CreateOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto = error("Not used.")

        override suspend fun joinRoom(
            request: JoinOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto = error("Not used.")

        override suspend fun submitAction(
            action: OnlinePlayerActionDto,
        ): OnlineActionResultDto = error("Not used.")

        override suspend fun fetchRoomSnapshot(
            roomId: String,
        ): OnlineRoomSnapshotDto = error("Not used.")

        override suspend fun fetchMatchSnapshot(
            matchId: String,
        ): OnlineMatchSnapshotDto = error("Not used.")
    }
}
