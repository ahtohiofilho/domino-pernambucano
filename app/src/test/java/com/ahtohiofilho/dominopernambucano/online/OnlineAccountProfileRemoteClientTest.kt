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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAccountProfileRemoteClientTest {
    @Test
    fun account_fetch_and_update_use_profile_route_and_bearer() =
        runBlocking {
            val methods = mutableListOf<HttpMethod>()
            val paths = mutableListOf<String>()
            val authorization = mutableListOf<String?>()
            val requestCount = AtomicInteger(0)
            val httpClient = HttpClient(
                MockEngine { request ->
                    methods += request.method
                    paths += request.url.encodedPath
                    authorization +=
                        request.headers[HttpHeaders.Authorization]

                    val count = requestCount.incrementAndGet()
                    val body = if (count == 1) {
                        profileJson(
                            updatedAtEpochMillis = 2_000L,
                        )
                    } else {
                        profileJson(
                            updatedAtEpochMillis = 3_000L,
                        )
                    }

                    respond(
                        content = body,
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
                credential = accountCredential(),
            )

            val fetched = client.fetch()
                as OnlineAccountProfileClientResult.Success
            val updated = client.update(
                publicDisplayName = "  Antônio   Filho  ",
                tableName = "afi",
            ) as OnlineAccountProfileClientResult.Success

            assertEquals(
                listOf(HttpMethod.Get, HttpMethod.Put),
                methods,
            )
            assertEquals(
                listOf(
                    "/accounts/profile",
                    "/accounts/profile",
                ),
                paths,
            )
            assertEquals(
                listOf(
                    "Bearer account-token",
                    "Bearer account-token",
                ),
                authorization,
            )
            assertEquals(
                "Antônio Filho",
                fetched.profile.publicDisplayName,
            )
            assertEquals("AFI", updated.profile.tableName)
            assertEquals(2, requestCount.get())

            httpClient.close()
        }

    @Test
    fun fetch_accepts_legacy_table_name_for_migration() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = """
                            {
                              "publicDisplayName": "Antônio Filho",
                              "tableName": "ANTÔNIO",
                              "updatedAtEpochMillis": 2000
                            }
                        """.trimIndent(),
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
                credential = accountCredential(),
            ).fetch() as OnlineAccountProfileClientResult.Success

            assertEquals(
                "ANTÔNIO",
                result.profile.tableName,
            )

            httpClient.close()
        }

    @Test
    fun missing_credential_does_not_call_backend() =
        runBlocking {
            val requestCount = AtomicInteger(0)
            val httpClient = noCallHttpClient(requestCount)

            val result = client(
                httpClient = httpClient,
                credential = null,
            ).fetch() as OnlineAccountProfileClientResult.Failure

            assertEquals(
                OnlineAccountProfileFailureKind
                    .AUTHENTICATION_REQUIRED,
                result.kind,
            )
            assertFalse(result.retryable)
            assertEquals(0, requestCount.get())

            httpClient.close()
        }

    @Test
    fun anonymous_credential_does_not_call_backend() =
        runBlocking {
            val requestCount = AtomicInteger(0)
            val httpClient = noCallHttpClient(requestCount)

            val result = client(
                httpClient = httpClient,
                credential = OnlineSessionCredential(
                    sessionKind = OnlineSessionKind.ANONYMOUS,
                    playerId = "visitor-player",
                    accessToken = "visitor-token",
                    expiresAtEpochMillis = 10_000L,
                ),
            ).fetch() as OnlineAccountProfileClientResult.Failure

            assertEquals(
                OnlineAccountProfileFailureKind.ACCOUNT_REQUIRED,
                result.kind,
            )
            assertEquals(0, requestCount.get())

            httpClient.close()
        }

    @Test
    fun invalid_update_is_rejected_before_backend_call() =
        runBlocking {
            val requestCount = AtomicInteger(0)
            val httpClient = noCallHttpClient(requestCount)

            val result = client(
                httpClient = httpClient,
                credential = accountCredential(),
            ).update(
                publicDisplayName = "Antônio",
                tableName = "A F!",
            ) as OnlineAccountProfileClientResult.Failure

            assertEquals(
                OnlineAccountProfileFailureKind.INVALID_PROFILE,
                result.kind,
            )
            assertEquals(0, requestCount.get())

            httpClient.close()
        }

    @Test
    fun missing_profile_is_a_non_retryable_state() =
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
            ).fetch() as OnlineAccountProfileClientResult.Failure

            assertEquals(
                OnlineAccountProfileFailureKind.NOT_ESTABLISHED,
                result.kind,
            )
            assertFalse(result.retryable)

            httpClient.close()
        }

    @Test
    fun server_validation_rejection_is_not_retryable() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = """{"error":"invalid_profile"}""",
                        status = HttpStatusCode.BadRequest,
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
            ).update(
                publicDisplayName = "Antônio Filho",
                tableName = "AFI",
            ) as OnlineAccountProfileClientResult.Failure

            assertEquals(
                OnlineAccountProfileFailureKind.INVALID_PROFILE,
                result.kind,
            )
            assertFalse(result.retryable)

            httpClient.close()
        }

    @Test
    fun rate_limit_is_retryable() =
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
            ).fetch() as OnlineAccountProfileClientResult.Failure

            assertEquals(
                OnlineAccountProfileFailureKind.RATE_LIMITED,
                result.kind,
            )
            assertTrue(result.retryable)

            httpClient.close()
        }

    private fun client(
        httpClient: HttpClient,
        credential: OnlineSessionCredential?,
    ): OnlineAccountProfileRemoteClient {
        return OnlineAccountProfileRemoteClient(
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

    private fun accountCredential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            playerId = "account-player",
            accountId = "account-1",
            accessToken = "account-token",
            expiresAtEpochMillis = 10_000L,
        )
    }

    private fun noCallHttpClient(
        requestCount: AtomicInteger,
    ): HttpClient {
        return HttpClient(
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
    }

    private fun profileJson(
        updatedAtEpochMillis: Long,
    ): String {
        return """
            {
              "publicDisplayName": "Antônio Filho",
              "tableName": "AFI",
              "updatedAtEpochMillis": $updatedAtEpochMillis
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
