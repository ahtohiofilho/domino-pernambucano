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
import org.junit.Assert.assertThrows
import org.junit.Test

class KtorRemoteOnlineApiClientEmailIdentityTest {
    @Test
    fun request_code_posts_without_bearer_and_decodes_generic_acceptance() =
        runBlocking {
            var recordedMethod: HttpMethod? = null
            var recordedPath: String? = null
            var recordedAuthorization: String? = null

            val httpClient = emailHttpClient { request ->
                recordedMethod = request.method
                recordedPath = request.url.encodedPath
                recordedAuthorization =
                    request.headers[HttpHeaders.Authorization]
                HttpStatusCode.Accepted
            }
            val client = createClient(httpClient)
            client.setBearerAccessToken("unrelated-active-token")

            val result = client.requestEmailCode(
                request = OnlineEmailCodeRequestDto(
                    email = "player@example.com",
                ),
            )

            assertEquals(HttpMethod.Post, recordedMethod)
            assertEquals(
                "/${OnlineEmailIdentityRoutes.REQUEST_CODE}",
                recordedPath,
            )
            assertNull(recordedAuthorization)
            assertEquals(
                OnlineEmailCodeRequestResponseDto(),
                result,
            )
            httpClient.close()
        }

    @Test
    fun link_posts_explicit_bearer_and_decodes_account_session() =
        runBlocking {
            var recordedPath: String? = null
            var recordedAuthorization: String? = null

            val httpClient = emailHttpClient { request ->
                recordedPath = request.url.encodedPath
                recordedAuthorization =
                    request.headers[HttpHeaders.Authorization]
                HttpStatusCode.OK
            }
            val client = createClient(httpClient)

            val result = client.linkEmailIdentity(
                request = OnlineEmailIdentityRequestDto(
                    email = "player@example.com",
                    code = "123456",
                ),
                accessToken = " anonymous-access-token ",
            )

            assertEquals(
                "/${OnlineEmailIdentityRoutes.LINK_IDENTITY}",
                recordedPath,
            )
            assertEquals(
                "Bearer anonymous-access-token",
                recordedAuthorization,
            )
            assertEquals("account-1", result.accountId)
            assertEquals("player-1", result.playerId)
            httpClient.close()
        }

    @Test
    fun recovery_never_sends_mutable_client_bearer() = runBlocking {
        var recordedPath: String? = null
        var recordedAuthorization: String? = null

        val httpClient = emailHttpClient { request ->
            recordedPath = request.url.encodedPath
            recordedAuthorization =
                request.headers[HttpHeaders.Authorization]
            HttpStatusCode.OK
        }
        val client = createClient(httpClient)
        client.setBearerAccessToken("unrelated-active-token")

        client.recoverEmailAccount(
            request = OnlineEmailIdentityRequestDto(
                email = "player@example.com",
                code = "123456",
            ),
        )

        assertEquals(
            "/${OnlineEmailIdentityRoutes.RECOVER_ACCOUNT}",
            recordedPath,
        )
        assertNull(recordedAuthorization)
        httpClient.close()
    }

    @Test
    fun known_http_statuses_are_converted_to_typed_failures() {
        val cases = listOf(
            HttpStatusCode.Unauthorized to
                OnlineEmailIdentityFailureReason.INVALID_CODE,
            HttpStatusCode.NotFound to
                OnlineEmailIdentityFailureReason.ACCOUNT_NOT_FOUND,
            HttpStatusCode.Conflict to
                OnlineEmailIdentityFailureReason.IDENTITY_CONFLICT,
            HttpStatusCode.TooManyRequests to
                OnlineEmailIdentityFailureReason.RATE_LIMITED,
            HttpStatusCode.ServiceUnavailable to
                OnlineEmailIdentityFailureReason.SERVICE_UNAVAILABLE,
        )

        cases.forEach { (status, expectedReason) ->
            val httpClient = emailHttpClient { status }
            val client = createClient(httpClient)

            val failure = assertThrows(
                OnlineEmailIdentityException::class.java,
            ) {
                runBlocking {
                    client.recoverEmailAccount(
                        request = OnlineEmailIdentityRequestDto(
                            email = "player@example.com",
                            code = "123456",
                        ),
                    )
                }
            }

            assertEquals(expectedReason, failure.reason)
            httpClient.close()
        }
    }

    private fun createClient(
        httpClient: HttpClient,
    ): KtorRemoteOnlineApiClient {
        return KtorRemoteOnlineApiClient(
            config = OnlineBackendConfig.remote(
                baseUrl = "http://localhost:8080",
            ),
            httpClient = httpClient,
        )
    }

    private fun emailHttpClient(
        statusProvider:
            (io.ktor.client.request.HttpRequestData) -> HttpStatusCode,
    ): HttpClient {
        return HttpClient(
            MockEngine { request ->
                val isCodeRequest =
                    request.url.encodedPath ==
                        "/${OnlineEmailIdentityRoutes.REQUEST_CODE}"
                respond(
                    content = if (isCodeRequest) {
                        """{"accepted":true}"""
                    } else {
                        """
                            {
                              "accountId": "account-1",
                              "playerId": "player-1",
                              "accessToken": "account-token",
                              "expiresAtEpochMillis": 3000
                            }
                        """.trimIndent()
                    },
                    status = statusProvider(request),
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
    }
}
