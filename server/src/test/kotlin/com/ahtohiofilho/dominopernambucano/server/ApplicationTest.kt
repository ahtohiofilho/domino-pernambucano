package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.createOnlineSnapshotRequestAction
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
                urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
            ) {
                contentType(
                    ContentType.Application.Json,
                )

                setBody(
                    json.encodeToString(
                        CreateOnlineRoomRequestDto(
                            localPlayerId = "player-1",
                            playerName = "Jogador 1",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                createRoomResponse.status,
            )

            val createRoomResult = json.decodeFromString<
                    OnlineRoomOperationResultDto
                    >(
                createRoomResponse.bodyAsText(),
            )

            assertTrue(createRoomResult.accepted)

            val createdRoom = requireNotNull(
                createRoomResult.roomSnapshot,
            )

            assertEquals(
                "server-room-1",
                createdRoom.roomId,
            )

            assertEquals(
                "0001",
                createdRoom.roomCode,
            )

            val roomResponse = client.get(
                urlString = "/${OnlineRemoteRoutes.roomSnapshot(createdRoom.roomId)}",
            )

            assertEquals(
                HttpStatusCode.OK,
                roomResponse.status,
            )

            val fetchedRoom = json.decodeFromString<
                    OnlineRoomSnapshotDto
                    >(
                roomResponse.bodyAsText(),
            )

            assertEquals(
                createdRoom,
                fetchedRoom,
            )
        }

    @Test
    fun fourth_player_starts_match_and_http_snapshot_is_consistent_for_all_clients() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = { 1_000L },
                    ),
                )
            }

            val hostResult = createRoomThroughHttp(
                playerId = "player-1",
                playerName = "Jogador 1",
            )

            assertTrue(hostResult.accepted)

            val waitingRoom = requireNotNull(
                hostResult.roomSnapshot,
            )

            assertEquals(
                OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
                waitingRoom.status,
            )

            assertEquals(
                0,
                hostResult.localSeatIndex,
            )

            val secondPlayerResult = joinRoomThroughHttp(
                roomCode = waitingRoom.roomCode,
                playerId = "player-2",
                playerName = "Jogador 2",
            )

            val thirdPlayerResult = joinRoomThroughHttp(
                roomCode = waitingRoom.roomCode,
                playerId = "player-3",
                playerName = "Jogador 3",
            )

            val fourthPlayerResult = joinRoomThroughHttp(
                roomCode = waitingRoom.roomCode,
                playerId = "player-4",
                playerName = "Jogador 4",
            )

            assertTrue(secondPlayerResult.accepted)
            assertTrue(thirdPlayerResult.accepted)
            assertTrue(fourthPlayerResult.accepted)

            assertEquals(
                1,
                secondPlayerResult.localSeatIndex,
            )

            assertEquals(
                2,
                thirdPlayerResult.localSeatIndex,
            )

            assertEquals(
                3,
                fourthPlayerResult.localSeatIndex,
            )

            val startedRoom = requireNotNull(
                fourthPlayerResult.roomSnapshot,
            )

            assertEquals(
                OnlineRoomStatusDto.IN_MATCH,
                startedRoom.status,
            )

            assertEquals(
                4,
                startedRoom.players.size,
            )

            assertEquals(
                listOf(0, 1, 2, 3),
                startedRoom.players
                    .mapNotNull { player ->
                        player.seatIndex
                    }
                    .sorted(),
            )

            val matchId = requireNotNull(
                startedRoom.matchId,
            )

            val roomResponse = client.get(
                urlString = "/${OnlineRemoteRoutes.roomSnapshot(startedRoom.roomId)}",
            )

            assertEquals(
                HttpStatusCode.OK,
                roomResponse.status,
            )

            val roomSnapshot = json.decodeFromString<
                    OnlineRoomSnapshotDto
                    >(
                roomResponse.bodyAsText(),
            )

            assertEquals(
                startedRoom,
                roomSnapshot,
            )

            val matchResponse = client.get(
                urlString = "/${OnlineRemoteRoutes.matchSnapshot(matchId)}",
            )

            assertEquals(
                HttpStatusCode.OK,
                matchResponse.status,
            )

            val matchSnapshot = json.decodeFromString<
                    OnlineMatchSnapshotDto
                    >(
                matchResponse.bodyAsText(),
            )

            assertEquals(
                startedRoom.roomId,
                matchSnapshot.roomId,
            )

            assertEquals(
                matchId,
                matchSnapshot.matchId,
            )

            assertEquals(
                1L,
                matchSnapshot.revision,
            )

            assertEquals(
                listOf(
                    "Jogador 1",
                    "Jogador 2",
                    "Jogador 3",
                    "Jogador 4",
                ),
                matchSnapshot.gameState.players.map { player ->
                    player.name
                },
            )

            val snapshotRequestAction = createOnlineSnapshotRequestAction(
                roomId = startedRoom.roomId,
                matchId = matchId,
                playerId = "player-1",
                revision = matchSnapshot.revision,
                actionId = "http-idempotent-snapshot-request",
            )

            val firstActionResult = submitActionThroughHttp(
                actionJson = json.encodeToString(
                    snapshotRequestAction,
                ),
            )

            val repeatedActionResult = submitActionThroughHttp(
                actionJson = json.encodeToString(
                    snapshotRequestAction,
                ),
            )

            assertTrue(firstActionResult.accepted)

            assertEquals(
                firstActionResult,
                repeatedActionResult,
            )

            assertEquals(
                snapshotRequestAction.actionId,
                firstActionResult.actionId,
            )

            val latestMatchResponse = client.get(
                urlString = "/${OnlineRemoteRoutes.matchSnapshot(matchId)}",
            )

            assertEquals(
                HttpStatusCode.OK,
                latestMatchResponse.status,
            )

            val latestMatchSnapshot = json.decodeFromString<
                    OnlineMatchSnapshotDto
                    >(
                latestMatchResponse.bodyAsText(),
            )

            assertNotNull(
                latestMatchSnapshot.serverEpochMillis,
            )

            assertTrue(
                latestMatchSnapshot.revision >= matchSnapshot.revision,
            )
        }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createRoomThroughHttp(
        playerId: String,
        playerName: String,
    ): OnlineRoomOperationResultDto {
        val response = client.post(
            urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
        ) {
            contentType(
                ContentType.Application.Json,
            )

            setBody(
                json.encodeToString(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = playerId,
                        playerName = playerName,
                    ),
                ),
            )
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status,
        )

        return json.decodeFromString(
            response.bodyAsText(),
        )
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.joinRoomThroughHttp(
        roomCode: String,
        playerId: String,
        playerName: String,
    ): OnlineRoomOperationResultDto {
        val response = client.post(
            urlString = "/${OnlineRemoteRoutes.JOIN_ROOM}",
        ) {
            contentType(
                ContentType.Application.Json,
            )

            setBody(
                json.encodeToString(
                    JoinOnlineRoomRequestDto(
                        roomCode = roomCode,
                        localPlayerId = playerId,
                        playerName = playerName,
                    ),
                ),
            )
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status,
        )

        return json.decodeFromString(
            response.bodyAsText(),
        )
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.submitActionThroughHttp(
        actionJson: String,
    ): OnlineActionResultDto {
        val response = client.post(
            urlString = "/${OnlineRemoteRoutes.SUBMIT_ACTION}",
        ) {
            contentType(
                ContentType.Application.Json,
            )

            setBody(
                actionJson,
            )
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status,
        )

        return json.decodeFromString(
            response.bodyAsText(),
        )
    }
}