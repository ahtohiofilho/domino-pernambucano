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

class KtorRemoteOnlineApiClientGoogleIdentityTest {
    @Test
    fun link_posts_explicit_bearer_and_decodes_account_session() =
        runBlocking {
            var recordedMethod: HttpMethod? = null
            var recordedPath: String? = null
            var recordedAuthorization: String? = null

            val httpClient = accountSessionHttpClient { request ->
                recordedMethod = request.method
                recordedPath = request.url.encodedPath
                recordedAuthorization =
                    request.headers[HttpHeaders.Authorization]
                HttpStatusCode.OK
            }
            val client = createClient(httpClient)

            val result = client.linkGoogleIdentity(
                request = OnlineGoogleIdentityRequestDto(
                    idToken = "google-id-token",
                ),
                accessToken = " anonymous-access-token ",
            )

            assertEquals(HttpMethod.Post, recordedMethod)
            assertEquals(
                "/accounts/identities/google/link",
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
    fun recovery_never_sends_the_mutable_client_bearer() = runBlocking {
        var recordedPath: String? = null
        var recordedAuthorization: String? = null
        val httpClient = accountSessionHttpClient { request ->
            recordedPath = request.url.encodedPath
            recordedAuthorization =
                request.headers[HttpHeaders.Authorization]
            HttpStatusCode.OK
        }
        val client = createClient(httpClient)
        client.setBearerAccessToken("unrelated-active-token")

        client.recoverGoogleAccount(
            request = OnlineGoogleIdentityRequestDto(
                idToken = "google-id-token",
            ),
        )

        assertEquals(
            "/accounts/identities/google/recover",
            recordedPath,
        )
        assertNull(recordedAuthorization)
        httpClient.close()
    }

    @Test
    fun known_http_statuses_are_converted_to_typed_failures() {
        val cases = listOf(
            HttpStatusCode.Unauthorized to
                    OnlineGoogleIdentityFailureReason
                        .INVALID_GOOGLE_CREDENTIAL,
            HttpStatusCode.NotFound to
                    OnlineGoogleIdentityFailureReason.ACCOUNT_NOT_FOUND,
            HttpStatusCode.Conflict to
                    OnlineGoogleIdentityFailureReason.IDENTITY_CONFLICT,
            HttpStatusCode.ServiceUnavailable to
                    OnlineGoogleIdentityFailureReason.SERVICE_UNAVAILABLE,
        )

        cases.forEach { (status, expectedReason) ->
            val httpClient = accountSessionHttpClient { status }
            val client = createClient(httpClient)

            val failure = assertThrows(
                OnlineGoogleIdentityException::class.java,
            ) {
                runBlocking {
                    client.recoverGoogleAccount(
                        request = OnlineGoogleIdentityRequestDto(
                            idToken = "google-id-token",
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

    private fun accountSessionHttpClient(
        statusProvider:
            (io.ktor.client.request.HttpRequestData) -> HttpStatusCode,
    ): HttpClient {
        return HttpClient(
            MockEngine { request ->
                respond(
                    content = """
                        {
                          "accountId": "account-1",
                          "playerId": "player-1",
                          "accessToken": "account-token",
                          "expiresAtEpochMillis": 3000
                        }
                    """.trimIndent(),
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
