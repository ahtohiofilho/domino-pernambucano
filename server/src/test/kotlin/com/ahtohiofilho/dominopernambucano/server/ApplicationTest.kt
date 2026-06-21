package com.ahtohiofilho.dominopernambucano.server

import io.ktor.http.contentType
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationTest {
    @Test
    fun health_returns_ok() = testApplication {
        application {
            module()
        }

        val response = client.get(
            urlString = "/health",
        )

        assertEquals(
            HttpStatusCode.OK,
            response.status,
        )

        assertTrue(
            response.bodyAsText().contains(
                "\"status\":\"ok\"",
            ),
        )
    }

    @Test
    fun create_room_and_fetch_room_snapshot_work_through_http() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = { 1_000L },
                    ),
                )
            }

            val createRoomResponse = client.post(
                urlString = "/rooms",
            ) {
                contentType(
                    ContentType.Application.Json,
                )

                setBody(
                    """
                    {
                      "localPlayerId": "player-1",
                      "playerName": "Jogador 1"
                    }
                    """.trimIndent(),
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                createRoomResponse.status,
            )

            val createRoomBody = createRoomResponse.bodyAsText()

            assertTrue(
                createRoomBody.contains(
                    "\"accepted\":true",
                ),
            )

            assertTrue(
                createRoomBody.contains(
                    "\"roomId\":\"server-room-1\"",
                ),
            )

            val roomResponse = client.get(
                urlString = "/rooms/server-room-1",
            )

            assertEquals(
                HttpStatusCode.OK,
                roomResponse.status,
            )

            assertTrue(
                roomResponse.bodyAsText().contains(
                    "\"roomCode\":\"0001\"",
                ),
            )
        }
}