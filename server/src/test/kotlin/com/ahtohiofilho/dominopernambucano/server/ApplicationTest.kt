package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.createOnlineSnapshotRequestAction
import io.ktor.client.request.get
import io.ktor.client.request.header
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
            module(
                store = InMemoryOnlineServerStore(
                    nowEpochMillis = { 1_000L },
                ),
                serverEnvironment = OnlineServerEnvironment.TEST,
            )
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
                    serverEnvironment = OnlineServerEnvironment.TEST,
                )
            }

            val createRoomResponse = client.post(
                urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
                contentType(
                    ContentType.Application.Json,
                )

                setBody(
                    json.encodeToString(
                        CreateOnlineRoomRequestDto(
                            localPlayerId = "player-1",
                            playerName = "P01",
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
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
            }

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

            val missingIdentityResponse = client.get(
                urlString = "/${OnlineRemoteRoutes.roomSnapshot(createdRoom.roomId)}",
            )

            assertEquals(
                HttpStatusCode.Unauthorized,
                missingIdentityResponse.status,
            )

            val foreignIdentityResponse = client.get(
                urlString = "/${OnlineRemoteRoutes.roomSnapshot(createdRoom.roomId)}",
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-2",
                )
            }

            assertEquals(
                HttpStatusCode.Forbidden,
                foreignIdentityResponse.status,
            )
        }

    @Test
    fun request_identity_must_match_player_id_declared_by_create_room() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = { 1_000L },
                    ),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                )
            }

            val response = client.post(
                urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
                contentType(
                    ContentType.Application.Json,
                )
                setBody(
                    json.encodeToString(
                        CreateOnlineRoomRequestDto(
                            localPlayerId = "player-2",
                            playerName = "P02",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.Forbidden,
                response.status,
            )
        }

    @Test
    fun fourth_player_waits_for_host_start_and_http_snapshot_is_projected_for_requesting_player() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = { 1_000L },
                    ),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                )
            }

            val hostResult = createRoomThroughHttp(
                playerId = "player-1",
                playerName = "P01",
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
                playerName = "P02",
            )

            val thirdPlayerResult = joinRoomThroughHttp(
                roomCode = waitingRoom.roomCode,
                playerId = "player-3",
                playerName = "P03",
            )

            val fourthPlayerResult = joinRoomThroughHttp(
                roomCode = waitingRoom.roomCode,
                playerId = "player-4",
                playerName = "P04",
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

            val fullWaitingRoom = requireNotNull(
                fourthPlayerResult.roomSnapshot,
            )

            assertEquals(
                OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
                fullWaitingRoom.status,
            )
            assertEquals(null, fullWaitingRoom.matchId)

            val startResult = startPrivateRoomThroughHttp(
                roomId = fullWaitingRoom.roomId,
                playerId = "player-1",
            )
            assertTrue(startResult.accepted)

            val startedRoom = requireNotNull(
                startResult.roomSnapshot,
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
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
            }

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
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
            }

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
                    "P01",
                    "P02",
                    "P03",
                    "P04",
                ),
                matchSnapshot.gameState.players.map { player ->
                    player.name
                },
            )

            assertTrue(
                matchSnapshot.gameState.players[0].hand.all { piece ->
                    piece.left >= 0 && piece.right >= 0
                },
            )
            matchSnapshot.gameState.players
                .drop(1)
                .forEach { player ->
                    assertTrue(
                        player.hand.all { piece ->
                            piece.left == -1 && piece.right == -1
                        },
                    )
                }
            assertTrue(
                matchSnapshot.gameState.sleepingPieces.all { piece ->
                    piece.left == -1 && piece.right == -1
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
                playerId = "player-1",
            )

            val repeatedActionResult = submitActionThroughHttp(
                actionJson = json.encodeToString(
                    snapshotRequestAction,
                ),
                playerId = "player-1",
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
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
            }

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

    @Test
    fun private_room_http_requires_three_character_player_selected_table_codes() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = { 1_000L },
                    ),
                    serverEnvironment =
                        OnlineServerEnvironment.TEST,
                )
            }

            val invalidCreate = client.post(
                urlString =
                    "/${OnlineRemoteRoutes.CREATE_ROOM}",
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "host-player",
                )
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        CreateOnlineRoomRequestDto(
                            localPlayerId = "host-player",
                            playerName = "Antonio",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.BadRequest,
                invalidCreate.status,
            )

            val validCreate = client.post(
                urlString =
                    "/${OnlineRemoteRoutes.CREATE_ROOM}",
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "host-player",
                )
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        CreateOnlineRoomRequestDto(
                            localPlayerId = "host-player",
                            playerName = "hst",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                validCreate.status,
            )

            val created =
                json.decodeFromString<
                    OnlineRoomOperationResultDto
                    >(
                    validCreate.bodyAsText(),
                )

            val room = requireNotNull(
                created.roomSnapshot,
            )

            assertEquals(
                "HST",
                room.players.single().name,
            )

            val invalidJoin = client.post(
                urlString =
                    "/${OnlineRemoteRoutes.JOIN_ROOM}",
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "guest-player",
                )
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        JoinOnlineRoomRequestDto(
                            roomCode = room.roomCode,
                            localPlayerId =
                                "guest-player",
                            playerName = "Convidado",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.BadRequest,
                invalidJoin.status,
            )

            val validJoin = client.post(
                urlString =
                    "/${OnlineRemoteRoutes.JOIN_ROOM}",
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "guest-player",
                )
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        JoinOnlineRoomRequestDto(
                            roomCode = room.roomCode,
                            localPlayerId =
                                "guest-player",
                            playerName = "g02",
                        ),
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                validJoin.status,
            )

            val joined =
                json.decodeFromString<
                    OnlineRoomOperationResultDto
                    >(
                    validJoin.bodyAsText(),
                )

            assertEquals(
                "G02",
                requireNotNull(joined.roomSnapshot)
                    .players
                    .single {
                        it.playerId == "guest-player"
                    }
                    .name,
            )
        }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createRoomThroughHttp(
        playerId: String,
        playerName: String,
    ): OnlineRoomOperationResultDto {
        val response = client.post(
            urlString = "/${OnlineRemoteRoutes.CREATE_ROOM}",
        ) {
            header(
                OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                playerId,
            )
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
            header(
                OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                playerId,
            )
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

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.startPrivateRoomThroughHttp(
        roomId: String,
        playerId: String,
    ): OnlineRoomOperationResultDto {
        val response = client.post(
            urlString = "/${OnlineRemoteRoutes.PRIVATE_ROOM_START}",
        ) {
            header(
                OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                playerId,
            )
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    PrivateRoomStartRequestDto(
                        roomId = roomId,
                        localPlayerId = playerId,
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.submitActionThroughHttp(
        actionJson: String,
        playerId: String,
    ): OnlineActionResultDto {
        val response = client.post(
            urlString = "/${OnlineRemoteRoutes.SUBMIT_ACTION}",
        ) {
            header(
                OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                playerId,
            )
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
