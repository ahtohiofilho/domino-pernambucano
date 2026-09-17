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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorPollingRequestCacheHardeningTest {

    @Test
    fun fetch_match_updates_sends_no_cache_request_directive() =
        runBlocking {
            var observedCacheControl: String? = null
            var observedAuthorization: String? = null
            var observedUrl: String? = null

            val engine = MockEngine { request ->
                observedCacheControl =
                    request.headers[HttpHeaders.CacheControl]
                observedAuthorization =
                    request.headers[HttpHeaders.Authorization]
                observedUrl = request.url.toString()

                respond(
                    content = "[]",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString(),
                    ),
                )
            }

            val httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            try {
                val api = KtorRemoteOnlineApiClient(
                    config = OnlineBackendConfig.remote(
                        baseUrl = "https://example.test",
                    ),
                    httpClient = httpClient,
                )

                api.setBearerAccessToken("regression-token")

                val snapshots = api.fetchMatchSnapshotsAfter(
                    matchId = "match-1",
                    afterRevision = 7L,
                )

                assertTrue(snapshots.isEmpty())
                assertEquals(
                    "no-cache",
                    observedCacheControl,
                )
                assertEquals(
                    "Bearer regression-token",
                    observedAuthorization,
                )
                assertTrue(
                    observedUrl?.contains(
                        "/matches/match-1/updates?afterRevision=7",
                    ) == true,
                )
            }
            finally {
                httpClient.close()
            }
        }
}