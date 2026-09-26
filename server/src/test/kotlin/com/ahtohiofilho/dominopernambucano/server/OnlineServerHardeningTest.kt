package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineServerHardeningTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun anonymous_session_limit_returns_retry_after() = testApplication {
        application {
            module(
                store = InMemoryOnlineServerStore(),
                serverEnvironment = OnlineServerEnvironment.TEST,
                rateLimitPolicy = testRateLimitPolicy(
                    anonymousSessionRequestLimit = 1,
                ),
            )
        }

        val firstResponse = client.post(
            urlString = "/${OnlineRemoteRoutes.CREATE_ANONYMOUS_SESSION}",
        )
        val rejectedResponse = client.post(
            urlString = "/${OnlineRemoteRoutes.CREATE_ANONYMOUS_SESSION}",
        )

        assertEquals(
            HttpStatusCode.Created,
            firstResponse.status,
        )
        assertEquals(
            HttpStatusCode.TooManyRequests,
            rejectedResponse.status,
        )
        assertNotNull(
            rejectedResponse.headers[HttpHeaders.RetryAfter],
        )
    }

    @Test
    fun authenticated_mutation_limit_is_independent_per_player() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    rateLimitPolicy = testRateLimitPolicy(
                        authenticatedMutationRequestLimit = 1,
                    ),
                )
            }

            val firstPlayerResponse = createRoom(
                playerId = "player-1",
            )
            val secondPlayerResponse = createRoom(
                playerId = "player-2",
            )
            val rejectedResponse = createRoom(
                playerId = "player-1",
            )

            assertEquals(
                HttpStatusCode.OK,
                firstPlayerResponse.status,
            )
            assertEquals(
                HttpStatusCode.OK,
                secondPlayerResponse.status,
            )
            assertEquals(
                HttpStatusCode.TooManyRequests,
                rejectedResponse.status,
            )
            assertNotNull(
                rejectedResponse.headers[HttpHeaders.RetryAfter],
            )
        }

    @Test
    fun liveness_remains_ok_when_readiness_is_unavailable() =
        testApplication {
            val store = InMemoryOnlineServerStore()
            val readiness = OnlineServerReadiness(
                store = store,
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    readiness = readiness,
                )
            }

            val initiallyReady = client.get(
                urlString = "/ready",
            )

            readiness.markTickerFailed()

            val healthResponse = client.get(
                urlString = "/health",
            )
            val unavailableResponse = client.get(
                urlString = "/ready",
            )

            assertEquals(
                HttpStatusCode.OK,
                initiallyReady.status,
            )
            assertEquals(
                HttpStatusCode.OK,
                healthResponse.status,
            )
            assertTrue(
                healthResponse.bodyAsText().contains(
                    "\"status\":\"ok\"",
                ),
            )
            assertEquals(
                HttpStatusCode.ServiceUnavailable,
                unavailableResponse.status,
            )
            assertTrue(
                unavailableResponse.bodyAsText().contains(
                    "\"status\":\"not_ready\"",
                ),
            )
        }

    @Test
    fun ticker_failure_changes_readiness_to_service_unavailable() =
        testApplication {
            application {
                module(
                    store = FailingTickerOnlineServerStore(),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    authoritativeTickIntervalMillis = 1L,
                )
            }

            var readinessStatus = client.get(
                urlString = "/ready",
            ).status

            withTimeout(2_000L) {
                while (
                    readinessStatus !=
                    HttpStatusCode.ServiceUnavailable
                ) {
                    delay(10L)
                    readinessStatus = client.get(
                        urlString = "/ready",
                    ).status
                }
            }

            assertEquals(
                HttpStatusCode.ServiceUnavailable,
                readinessStatus,
            )
            assertEquals(
                HttpStatusCode.OK,
                client.get(
                    urlString = "/health",
                ).status,
            )
        }

    private suspend fun ApplicationTestBuilder.createRoom(
        playerId: String,
    ) = client.post(
        urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
    ) {
        header(
            OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
            playerId,
        )
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                CreateOnlineRoomRequestDto(
                    localPlayerId = playerId,
                    playerName = when (playerId) {
                        "player-1" -> "P01"
                        "player-2" -> "P02"
                        else -> "TST"
                    },
                ),
            ),
        )
    }

    private fun testRateLimitPolicy(
        anonymousSessionRequestLimit: Int = 100,
        authenticatedMutationRequestLimit: Int = 100,
    ) = OnlineServerRateLimitPolicy(
        anonymousSessionRequestLimit = anonymousSessionRequestLimit,
        authenticatedMutationRequestLimit =
            authenticatedMutationRequestLimit,
        authenticatedReadRequestLimit = 100,
        clientTraceRequestLimit = 100,
    )

    private class FailingTickerOnlineServerStore(
        delegate: OnlineServerStore = InMemoryOnlineServerStore(),
    ) : OnlineServerStore by delegate {
        override fun advanceAuthoritativeTime(): Boolean {
            throw IllegalStateException("ticker failure")
        }
    }
}
