package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineAnonymousSessionRouteTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun issued_token_resolves_only_until_its_expiration() {
        var nowEpochMillis = 1_000L
        val service = HmacOnlineSessionTokenService(
            signingSecret = ByteArray(32) { index ->
                index.toByte()
            },
            nowEpochMillis = {
                nowEpochMillis
            },
            sessionTtlMillis = 5_000L,
            playerIdFactory = {
                "anonymous-test-player"
            },
        )

        val session = service.issueAnonymousSession()

        assertEquals(
            "anonymous-test-player",
            service.resolveAccessToken(
                accessToken = session.accessToken,
            )?.playerId,
        )

        nowEpochMillis = session.expiresAtEpochMillis

        assertNull(
            service.resolveAccessToken(
                accessToken = session.accessToken,
            ),
        )
    }

    @Test
    fun anonymous_session_authorizes_matching_player_without_development_header() =
        testApplication {
            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { index ->
                    (index + 1).toByte()
                },
                nowEpochMillis = {
                    1_000L
                },
                playerIdFactory = {
                    "anonymous-test-player"
                },
            )

            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = {
                            1_000L
                        },
                    ),
                    sessionTokenService = tokenService,
                )
            }

            val sessionResponse = client.post(
                urlString = "/${OnlineRemoteRoutes.CREATE_ANONYMOUS_SESSION}",
            )

            assertEquals(
                HttpStatusCode.Created,
                sessionResponse.status,
            )

            val session = json.decodeFromString<OnlineAnonymousSessionDto>(
                sessionResponse.bodyAsText(),
            )

            assertNotNull(
                tokenService.resolveAccessToken(
                    accessToken = session.accessToken,
                ),
            )

            val missingIdentityResponse = client.post(
                urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
            ) {
                contentType(
                    ContentType.Application.Json,
                )
                setBody(
                    json.encodeToString(
                        CreateOnlineRoomRequestDto(
                            localPlayerId = session.playerId,
                            playerName = "Sem credencial",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.Unauthorized,
                missingIdentityResponse.status,
            )

            val matchingIdentityResponse = client.post(
                urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
            ) {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${session.accessToken}",
                )
                contentType(
                    ContentType.Application.Json,
                )
                setBody(
                    json.encodeToString(
                        CreateOnlineRoomRequestDto(
                            localPlayerId = session.playerId,
                            playerName = "Sessão anônima",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                matchingIdentityResponse.status,
            )

            val mismatchedIdentityResponse = client.post(
                urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
            ) {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${session.accessToken}",
                )
                contentType(
                    ContentType.Application.Json,
                )
                setBody(
                    json.encodeToString(
                        CreateOnlineRoomRequestDto(
                            localPlayerId = "anonymous-forged-player",
                            playerName = "Tentativa forjada",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.Forbidden,
                mismatchedIdentityResponse.status,
            )
        }
}
