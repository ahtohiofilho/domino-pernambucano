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

class KtorRemoteOnlineApiClientPlayGamesIdentityTest {
    @Test
    fun link_posts_bearer_to_play_games_route() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var authorization: String? = null

        val httpClient = accountSessionHttpClient { request ->
            method = request.method
            path = request.url.encodedPath
            authorization =
                request.headers[HttpHeaders.Authorization]
            HttpStatusCode.OK
        }
        val client = createClient(httpClient)

        val result = client.linkPlayGamesIdentity(
            request = OnlinePlayGamesIdentityRequestDto(
                serverAuthCode = "server-auth-code",
            ),
            accessToken = " anonymous-access-token ",
        )

        assertEquals(HttpMethod.Post, method)
        assertEquals(
            "/accounts/identities/play-games/link",
            path,
        )
        assertEquals(
            "Bearer anonymous-access-token",
            authorization,
        )
        assertEquals("account-1", result.accountId)
        httpClient.close()
    }

    @Test
    fun recovery_does_not_send_mutable_client_bearer() = runBlocking {
        var path: String? = null
        var authorization: String? = null
        val httpClient = accountSessionHttpClient { request ->
            path = request.url.encodedPath
            authorization =
                request.headers[HttpHeaders.Authorization]
            HttpStatusCode.OK
        }
        val client = createClient(httpClient)
        client.setBearerAccessToken("unrelated-token")

        client.recoverPlayGamesAccount(
            request = OnlinePlayGamesIdentityRequestDto(
                serverAuthCode = "server-auth-code",
            ),
        )

        assertEquals(
            "/accounts/identities/play-games/recover",
            path,
        )
        assertNull(authorization)
        httpClient.close()
    }

    @Test
    fun known_statuses_are_typed_play_games_failures() {
        val cases = listOf(
            HttpStatusCode.Unauthorized to
                OnlinePlayGamesIdentityFailureReason
                    .INVALID_PLAY_GAMES_CREDENTIAL,
            HttpStatusCode.NotFound to
                OnlinePlayGamesIdentityFailureReason.ACCOUNT_NOT_FOUND,
            HttpStatusCode.Conflict to
                OnlinePlayGamesIdentityFailureReason.IDENTITY_CONFLICT,
            HttpStatusCode.ServiceUnavailable to
                OnlinePlayGamesIdentityFailureReason.SERVICE_UNAVAILABLE,
        )

        cases.forEach { (status, reason) ->
            val httpClient = accountSessionHttpClient { status }
            val client = createClient(httpClient)

            val failure = assertThrows(
                OnlinePlayGamesIdentityException::class.java,
            ) {
                runBlocking {
                    client.recoverPlayGamesAccount(
                        OnlinePlayGamesIdentityRequestDto(
                            serverAuthCode = "server-auth-code",
                        ),
                    )
                }
            }

            assertEquals(reason, failure.reason)
            httpClient.close()
        }
    }

    private fun createClient(
        httpClient: HttpClient,
    ) = KtorRemoteOnlineApiClient(
        config = OnlineBackendConfig.remote(
            baseUrl = "http://localhost:8080",
        ),
        httpClient = httpClient,
    )

    private fun accountSessionHttpClient(
        statusProvider:
            (io.ktor.client.request.HttpRequestData) ->
                HttpStatusCode,
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
