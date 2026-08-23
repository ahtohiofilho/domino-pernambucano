package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KtorRemoteOnlineApiClientRankedQueueTest {
    @Test
    fun ranked_queue_uses_shared_route_bearer_and_safe_dtos() =
        runBlocking {
            val recorded = mutableListOf<RecordedRequest>()

            val httpClient = HttpClient(
                MockEngine { request ->
                    recorded += RecordedRequest(
                        method = request.method,
                        path = request.url.encodedPath,
                        authorization =
                            request.headers[HttpHeaders.Authorization],
                    )

                    val response = when (request.method) {
                        HttpMethod.Post -> """
                            {
                              "status": "WAITING",
                              "queuePosition": 2
                            }
                        """.trimIndent()

                        HttpMethod.Get -> """
                            {
                              "status": "MATCHED",
                              "matchId": "match-1"
                            }
                        """.trimIndent()

                        else -> """
                            {
                              "status": "NOT_QUEUED"
                            }
                        """.trimIndent()
                    }

                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val client = KtorRemoteOnlineApiClient(
                config = OnlineBackendConfig.remote(
                    baseUrl = "http://localhost:8080",
                ),
                httpClient = httpClient,
            )
            client.setBearerAccessToken("account-token")

            val entered = client.enqueuePublicRankedQueue(
                request = PublicRankedQueueEnterRequestDto(
                    playerName = "Jogador",
                ),
            )
            val resumed = client.fetchPublicRankedQueueStatus()
            val cancelled = client.cancelPublicRankedQueue()

            assertEquals(PublicRankedQueueHttpStatus.WAITING, entered.status)
            assertEquals(2, entered.queuePosition)
            assertEquals(PublicRankedQueueHttpStatus.MATCHED, resumed.status)
            assertEquals("match-1", resumed.matchId)
            assertNull(resumed.localSeatIndex)
            assertEquals(PublicRankedQueueHttpStatus.NOT_QUEUED, cancelled.status)

            assertEquals(
                listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Delete),
                recorded.map { item -> item.method },
            )
            assertEquals(
                listOf("/ranked-queue", "/ranked-queue", "/ranked-queue"),
                recorded.map { item -> item.path },
            )
            assertEquals(
                listOf(
                    "Bearer account-token",
                    "Bearer account-token",
                    "Bearer account-token",
                ),
                recorded.map { item -> item.authorization },
            )

            httpClient.close()
        }

    @Test
    fun cancellation_decodes_safe_matched_body_from_http_409() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = """
                            {
                              "status": "MATCHED",
                              "matchId": "match-409"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.Conflict,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val client = KtorRemoteOnlineApiClient(
                config = OnlineBackendConfig.remote(
                    baseUrl = "http://localhost:8080",
                ),
                httpClient = httpClient,
            )
            client.setBearerAccessToken("account-token")

            val result = client.cancelPublicRankedQueue()

            assertEquals(PublicRankedQueueHttpStatus.MATCHED, result.status)
            assertEquals("match-409", result.matchId)
            assertNull(result.localSeatIndex)
            assertNull(result.queuePosition)

            httpClient.close()
        }

    @Test
    fun ranked_queue_preserves_http_status_for_client_policy() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = """{"error":"rate_limited"}""",
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val client = KtorRemoteOnlineApiClient(
                config = OnlineBackendConfig.remote(
                    baseUrl = "http://localhost:8080",
                ),
                httpClient = httpClient,
            )
            client.setBearerAccessToken("account-token")

            val error = runCatching {
                client.fetchPublicRankedQueueStatus()
            }.exceptionOrNull() as OnlineRankedQueueHttpException

            assertEquals(429, error.statusCode)

            httpClient.close()
        }

    private data class RecordedRequest(
        val method: HttpMethod,
        val path: String,
        val authorization: String?,
    )
}
