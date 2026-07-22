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
import org.junit.Test

class KtorRemoteOnlineApiClientAccountTest {
    @Test
    fun promote_account_posts_bearer_and_decodes_account_session() =
        runBlocking {
            var recordedMethod: HttpMethod? = null
            var recordedPath: String? = null
            var recordedAuthorization: String? = null

            val httpClient = HttpClient(
                MockEngine { request ->
                    recordedMethod = request.method
                    recordedPath = request.url.encodedPath
                    recordedAuthorization =
                        request.headers[HttpHeaders.Authorization]

                    respond(
                        content = """
                            {
                              "accountId": "account-1",
                              "playerId": "anonymous-player-1",
                              "accessToken": "account-token",
                              "expiresAtEpochMillis": 2000
                            }
                        """.trimIndent(),
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
            client.setBearerAccessToken("anonymous-token")

            val result = client.promoteAccount()

            assertEquals(HttpMethod.Post, recordedMethod)
            assertEquals("/accounts/promote", recordedPath)
            assertEquals("Bearer anonymous-token", recordedAuthorization)
            assertEquals("account-1", result.accountId)
            assertEquals("anonymous-player-1", result.playerId)
            assertEquals("account-token", result.accessToken)
            assertEquals(2_000L, result.expiresAtEpochMillis)

            httpClient.close()
        }
}
