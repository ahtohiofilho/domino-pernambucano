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
            var cycleId: String? = null
            var authorization: String? = null

            val httpClient = HttpClient(
                MockEngine { request ->
                    path = request.url.encodedPath
                    cycle = request.url.parameters["cycle"]
                    offset = request.url.parameters["offset"]
                    limit = request.url.parameters["limit"]
                    cycleId = request.url.parameters["cycleId"]
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
                credential = anonymousCredential(),
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
            assertNull(cycleId)
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
    fun historical_ranking_sends_cycle_id_and_uses_retained_size() =
        runBlocking {
            var requestedCycleId: String? = null

            val httpClient = HttpClient(
                MockEngine { request ->
                    requestedCycleId =
                        request.url.parameters["cycleId"]

                    respond(
                        content = validResponseJson(
                            cycleId = CLOSED_CYCLE_ID,
                            totalEligiblePlayers = 104,
                            retainedRankingSize = 100,
                            isClosed = true,
                            closedAtEpochMillis = 200,
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
                credential = anonymousCredential(),
            ).fetchHistorical(
                cycle = PublicRankingCycleDto.DAILY,
                cycleId = CLOSED_CYCLE_ID,
                offset = 0,
                limit = 2,
            ) as OnlinePublicRankingClientResult.Success

            assertEquals(CLOSED_CYCLE_ID, requestedCycleId)
            assertTrue(result.response.isClosed)
            assertEquals(104, result.response.totalEligiblePlayers)
            assertEquals(100, result.response.retainedRankingSize)
            assertTrue(result.response.hasMore)

            httpClient.close()
        }

    @Test
    fun closed_cycle_inventory_is_authenticated_and_validated() =
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
                        content = validCyclesResponseJson(),
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
                credential = anonymousCredential(),
            ).fetchClosedCycles(
                cycle = PublicRankingCycleDto.DAILY,
                offset = 0,
                limit = 10,
            ) as OnlinePublicRankingCyclesClientResult.Success

            assertEquals("/ranking/cycles", path)
            assertEquals("DAILY", cycle)
            assertEquals("0", offset)
            assertEquals("10", limit)
            assertEquals("Bearer visitor-token", authorization)
            assertEquals(1, result.response.totalClosedCycles)
            assertEquals(CLOSED_CYCLE_ID, result.response.cycles.single().cycleId)

            httpClient.close()
        }

    @Test
    fun blank_historical_cycle_id_does_not_call_backend() =
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
                credential = anonymousCredential(),
            ).fetchHistorical(
                cycle = PublicRankingCycleDto.DAILY,
                cycleId = "   ",
            ) as OnlinePublicRankingClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
                result.kind,
            )
            assertFalse(result.retryable)
            assertEquals(0, requestCount.get())

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

            val rankingResult = client(
                httpClient = httpClient,
                credential = null,
            ).fetch(
                cycle = PublicRankingCycleDto.WEEKLY,
            ) as OnlinePublicRankingClientResult.Failure

            val cyclesResult = client(
                httpClient = httpClient,
                credential = null,
            ).fetchClosedCycles(
                cycle = PublicRankingCycleDto.WEEKLY,
            ) as OnlinePublicRankingCyclesClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind
                    .AUTHENTICATION_REQUIRED,
                rankingResult.kind,
            )
            assertFalse(rankingResult.retryable)
            assertEquals(
                OnlinePublicRankingFailureKind
                    .AUTHENTICATION_REQUIRED,
                cyclesResult.kind,
            )
            assertFalse(cyclesResult.retryable)
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
                credential = accountCredential(),
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
    fun historical_not_found_is_non_retryable_protocol_failure() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = """{"error":"not_found"}""",
                        status = HttpStatusCode.NotFound,
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
                credential = accountCredential(),
            ).fetchHistorical(
                cycle = PublicRankingCycleDto.DAILY,
                cycleId = CLOSED_CYCLE_ID,
            ) as OnlinePublicRankingClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
                result.kind,
            )
            assertFalse(result.retryable)

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
                credential = anonymousCredential(),
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

    @Test
    fun malformed_cycle_inventory_is_a_protocol_failure() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = validCyclesResponseJson(
                            retainedRankingSize = 101,
                            totalEligiblePlayers = 100,
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
                credential = anonymousCredential(),
            ).fetchClosedCycles(
                cycle = PublicRankingCycleDto.DAILY,
                limit = 10,
            ) as OnlinePublicRankingCyclesClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
                result.kind,
            )
            assertFalse(result.retryable)

            httpClient.close()
        }

    @Test
    fun provisional_award_in_open_cycle_is_a_protocol_failure() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = validResponseJson().replace(
                            "\"awardedRankingSize\": 0",
                            "\"awardedRankingSize\": 1",
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
                credential = anonymousCredential(),
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

    private fun anonymousCredential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ANONYMOUS,
            playerId = "visitor-player",
            accessToken = "visitor-token",
            expiresAtEpochMillis = 10_000L,
        )
    }

    private fun accountCredential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            playerId = "account-player",
            accountId = "account-a",
            accessToken = "account-token",
            expiresAtEpochMillis = 10_000L,
        )
    }

    private fun validResponseJson(
        cycle: String = "DAILY",
        cycleId: String = CLOSED_CYCLE_ID,
        totalEligiblePlayers: Int = 4,
        retainedRankingSize: Int = totalEligiblePlayers,
        isClosed: Boolean = false,
        closedAtEpochMillis: Long? = null,
    ): String {
        val closedAtJson = closedAtEpochMillis?.toString() ?: "null"
        val retentionPolicyVersion = if (isClosed) 1 else 0
        val isRetentionLimited =
            isClosed && totalEligiblePlayers > retainedRankingSize
        val awardedRankingSize =
            if (isClosed) {
                minOf(50, retainedRankingSize)
            } else {
                0
            }
        val firstAwardTier =
            if (awardedRankingSize >= 1) {
                "\"DIAMOND\""
            } else {
                "null"
            }
        val secondAwardTier =
            if (awardedRankingSize >= 2) {
                "\"GOLD\""
            } else {
                "null"
            }

        return """
            {
              "cycle": "$cycle",
              "cycleId": "$cycleId",
              "rankingRuleVersion": 1,
              "timeZoneId": "America/Recife",
              "startsAtEpochMillis": 100,
              "endsAtEpochMillis": 200,
              "resultCount": 1,
              "totalEligiblePlayers": $totalEligiblePlayers,
              "publicationThreshold": 1,
              "publicationStatus": "PUBLISHED",
              "eligiblePlayersRemaining": 0,
              "awardsEligible": true,
              "awardRuleVersion": 1,
              "awardedRankingSize": $awardedRankingSize,
              "retainedRankingSize": $retainedRankingSize,
              "isClosed": $isClosed,
              "closedAtEpochMillis": $closedAtJson,
              "retentionPolicyVersion": $retentionPolicyVersion,
              "isRetentionLimited": $isRetentionLimited,
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
                  "automaticRounds": 0,
                  "awardTier": $firstAwardTier
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
                  "automaticRounds": 0,
                  "awardTier": $secondAwardTier
                }
              ],
              "viewer": null
            }
        """.trimIndent()
    }

    private fun validCyclesResponseJson(
        retainedRankingSize: Int = 100,
        totalEligiblePlayers: Int = 104,
    ): String {
        return """
            {
              "cycle": "DAILY",
              "totalClosedCycles": 1,
              "offset": 0,
              "limit": 10,
              "hasMore": false,
              "cycles": [
                {
                  "cycle": "DAILY",
                  "cycleId": "$CLOSED_CYCLE_ID",
                  "rankingRuleVersion": 1,
                  "timeZoneId": "America/Recife",
                  "startsAtEpochMillis": 100,
                  "endsAtEpochMillis": 200,
                  "closedAtEpochMillis": 200,
                  "resultCount": 26,
                  "totalEligiblePlayers": $totalEligiblePlayers,
                  "publicationThreshold": 1,
                  "publicationStatus": "PUBLISHED",
                  "eligiblePlayersRemaining": 0,
                  "awardsEligible": true,
                  "awardRuleVersion": 1,
                  "awardedRankingSize": ${minOf(50, retainedRankingSize)},
                  "retainedRankingSize": $retainedRankingSize,
                  "retentionPolicyVersion": 1,
                  "isRetentionLimited": ${totalEligiblePlayers > retainedRankingSize}
                }
              ]
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

    companion object {
        private const val CLOSED_CYCLE_ID =
            "ranking-v1:daily:2026-07-25"
    }
}
