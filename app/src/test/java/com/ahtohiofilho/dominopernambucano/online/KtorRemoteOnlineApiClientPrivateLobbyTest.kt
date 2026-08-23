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
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorRemoteOnlineApiClientPrivateLobbyTest {
    @Test
    fun private_lobby_operations_use_protected_post_routes() =
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

                    val started =
                        request.url.encodedPath.endsWith("/start")
                    val response = if (started) {
                        """
                        {
                          "accepted": true,
                          "roomSnapshot": {
                            "roomId": "room-1",
                            "roomCode": "ABCD",
                            "hostPlayerId": "player-1",
                            "status": "IN_MATCH",
                            "players": [],
                            "matchMode": "PRIVATE_UNRANKED",
                            "matchId": "match-1"
                          },
                          "localSeatIndex": 2
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                          "accepted": true,
                          "roomSnapshot": {
                            "roomId": "room-1",
                            "roomCode": "ABCD",
                            "hostPlayerId": "player-1",
                            "status": "WAITING_FOR_PLAYERS",
                            "players": [],
                            "matchMode": "PRIVATE_UNRANKED"
                          },
                          "localSeatIndex": 2
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

            val moved = client.movePrivateRoomSeat(
                PrivateRoomSeatChangeRequestDto(
                    roomId = "room-1",
                    localPlayerId = "player-1",
                    targetSeatIndex = 2,
                ),
            )
            val started = client.startPrivateRoom(
                PrivateRoomStartRequestDto(
                    roomId = "room-1",
                    localPlayerId = "player-1",
                ),
            )

            assertTrue(moved.accepted)
            assertEquals(2, moved.localSeatIndex)
            assertTrue(started.accepted)
            assertEquals("match-1", started.roomSnapshot?.matchId)

            assertEquals(
                listOf(HttpMethod.Post, HttpMethod.Post),
                recorded.map { item -> item.method },
            )
            assertEquals(
                listOf(
                    "/${OnlineRemoteRoutes.PRIVATE_ROOM_SEAT}",
                    "/${OnlineRemoteRoutes.PRIVATE_ROOM_START}",
                ),
                recorded.map { item -> item.path },
            )
            assertEquals(
                listOf(
                    "Bearer account-token",
                    "Bearer account-token",
                ),
                recorded.map { item -> item.authorization },
            )

            httpClient.close()
        }

    private data class RecordedRequest(
        val method: HttpMethod,
        val path: String,
        val authorization: String?,
    )
}