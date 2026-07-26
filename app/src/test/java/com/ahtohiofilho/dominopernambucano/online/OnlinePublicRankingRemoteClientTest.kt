package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePublicRankingRemoteClientTest {
    @Test
    fun anonymous_online_credential_can_fetch_public_ranking() =
        runBlocking {
            var path: String? = null
            var cycle: String? = null
            var offset: String? = null
            var limit: String? = null
            var authorization: String? = null

            val httpClient = HttpClient(
                MockEngine { request ->
                    path = request.url.encodedPath
                    cycle = request.url.parameters["cycle"]
                    offset = request.url.parameters["offset"]
                    limit = request.url.parameters["limit"]
                    authorization =
                        request.headers[HttpHeaders.Authorization]

                    respond(
                        content = validResponseJson(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val client = client(
                httpClient = httpClient,
                credential = OnlineSessionCredential(
                    sessionKind = OnlineSessionKind.ANONYMOUS,
                    playerId = "visitor-player",
                    accessToken = "visitor-token",
                    expiresAtEpochMillis = 10_000L,
                ),
            )

            val result = client.fetch(
                cycle = PublicRankingCycleDto.DAILY,
                offset = 0,
                limit = 2,
            ) as OnlinePublicRankingClientResult.Success

            assertEquals("/ranking", path)
            assertEquals("DAILY", cycle)
            assertEquals("0", offset)
            assertEquals("2", limit)
            assertEquals("Bearer visitor-token", authorization)
            assertEquals(2, result.response.entries.size)
            assertTrue(result.response.hasMore)
            assertNull(result.response.viewer)
            assertFalse(
                result.response.entries.first()
                    .competitorId
                    .contains("account"),
            )

            httpClient.close()
        }

    @Test
    fun missing_valid_credential_does_not_call_backend() =
        runBlocking {
            val requestCount = AtomicInteger(0)
            val httpClient = HttpClient(
                MockEngine {
                    requestCount.incrementAndGet()
                    error("Backend must not be called.")
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val result = client(
                httpClient = httpClient,
                credential = null,
            ).fetch(
                cycle = PublicRankingCycleDto.WEEKLY,
            ) as OnlinePublicRankingClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind
                    .AUTHENTICATION_REQUIRED,
                result.kind,
            )
            assertFalse(result.retryable)
            assertEquals(0, requestCount.get())

            httpClient.close()
        }

    @Test
    fun rate_limit_is_reported_as_retryable() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = """{"error":"rate_limited"}""",
                        status = HttpStatusCode.TooManyRequests,
                        headers = jsonHeaders(),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val result = client(
                httpClient = httpClient,
                credential = OnlineSessionCredential(
                    sessionKind = OnlineSessionKind.ACCOUNT,
                    playerId = "account-player",
                    accountId = "account-a",
                    accessToken = "account-token",
                    expiresAtEpochMillis = 10_000L,
                ),
            ).fetch(
                cycle = PublicRankingCycleDto.MONTHLY,
            ) as OnlinePublicRankingClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind.RATE_LIMITED,
                result.kind,
            )
            assertTrue(result.retryable)

            httpClient.close()
        }

    @Test
    fun mismatched_response_cycle_is_a_protocol_failure() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = validResponseJson(
                            cycle = "ANNUAL",
                        ),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val result = client(
                httpClient = httpClient,
                credential = OnlineSessionCredential(
                    sessionKind = OnlineSessionKind.ANONYMOUS,
                    playerId = "visitor-player",
                    accessToken = "visitor-token",
                    expiresAtEpochMillis = 10_000L,
                ),
            ).fetch(
                cycle = PublicRankingCycleDto.DAILY,
                limit = 2,
            ) as OnlinePublicRankingClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
                result.kind,
            )
            assertFalse(result.retryable)

            httpClient.close()
        }

    private fun client(
        httpClient: HttpClient,
        credential: OnlineSessionCredential?,
    ): OnlinePublicRankingRemoteClient {
        return OnlinePublicRankingRemoteClient(
            remoteApiClient = KtorRemoteOnlineApiClient(
                config = OnlineBackendConfig.remote(
                    baseUrl = "http://localhost:8080",
                ),
                httpClient = httpClient,
            ),
            sessionCredentialRepository =
                OnlineSessionCredentialRepository(
                    store = MemoryCredentialStore(credential),
                    nowEpochMillis = { 1_000L },
                ),
        )
    }

    private fun validResponseJson(
        cycle: String = "DAILY",
    ): String {
        return """
            {
              "cycle": "$cycle",
              "cycleId": "ranking-v1:daily:2026-07-25",
              "rankingRuleVersion": 1,
              "timeZoneId": "America/Recife",
              "startsAtEpochMillis": 100,
              "endsAtEpochMillis": 200,
              "resultCount": 1,
              "totalEligiblePlayers": 4,
              "offset": 0,
              "limit": 2,
              "hasMore": true,
              "entries": [
                {
                  "rank": 1,
                  "competitorId": "competitor-a",
                  "victories": 1,
                  "games": 1,
                  "scoreNumerator": 2,
                  "scoreDenominator": 3,
                  "teamBalance": 6,
                  "individualPoints": 4,
                  "touchesGiven": 2,
                  "automaticRounds": 0
                },
                {
                  "rank": 2,
                  "competitorId": "competitor-b",
                  "victories": 1,
                  "games": 1,
                  "scoreNumerator": 2,
                  "scoreDenominator": 3,
                  "teamBalance": 6,
                  "individualPoints": 1,
                  "touchesGiven": 1,
                  "automaticRounds": 0
                }
              ],
              "viewer": null
            }
        """.trimIndent()
    }

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    private class MemoryCredentialStore(
        initialCredential: OnlineSessionCredential?,
    ) : OnlineSessionCredentialStore {
        private var credential = initialCredential

        override fun read(): OnlineSessionCredential? {
            return credential
        }

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
}
