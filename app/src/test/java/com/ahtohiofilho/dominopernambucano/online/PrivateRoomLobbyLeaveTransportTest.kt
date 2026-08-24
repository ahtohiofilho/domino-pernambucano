package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateRoomLobbyLeaveTransportTest {
    @Test
    fun ktor_client_posts_private_leave_to_dedicated_route() = runBlocking {
        var capturedPath: String? = null
        var capturedMethod: HttpMethod? = null

        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedMethod = request.method
            respond(
                content = """{"accepted":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    },
                )
            }
        }
        val client = KtorRemoteOnlineApiClient(
            config = OnlineBackendConfig.remote(
                baseUrl = "http://localhost:8080",
            ),
            httpClient = httpClient,
        )

        val result = client.leavePrivateRoom(
            PrivateRoomLeaveRequestDto(
                roomId = "room-1",
                localPlayerId = "player-1",
            ),
        )

        assertTrue(result.accepted)
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals(
            "/${OnlineRemoteRoutes.PRIVATE_ROOM_LEAVE}",
            capturedPath,
        )
    }

    @Test
    fun repository_leave_waiting_private_lobby_uses_private_leave_not_match_action() =
        runBlocking {
            val room = OnlineRoomSnapshotDto(
                roomId = "room-1",
                roomCode = "ABC123",
                hostPlayerId = "player-1",
                status = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = "player-1",
                        name = "Player 1",
                        seatIndex = 0,
                        connected = true,
                    ),
                ),
                matchMode = DominoMatchMode.PRIVATE_UNRANKED,
            )
            val apiClient = LeaveRecordingApiClient(
                createRoomResult = OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = room,
                    localSeatIndex = 0,
                ),
            )
            val repository = RemoteOnlineRoomRepository(
                config = OnlineBackendConfig.remote(
                    baseUrl = "http://localhost:8080",
                ),
                apiClient = apiClient,
                pollingPolicy = OnlineRemotePollingPolicy.Disabled,
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Player 1",
                ),
            )
            repository.leaveRoom()

            assertEquals(
                listOf(
                    PrivateRoomLeaveRequestDto(
                        roomId = "room-1",
                        localPlayerId = "player-1",
                    ),
                ),
                apiClient.privateLeaveRequests,
            )
            assertTrue(apiClient.submitActionRequests.isEmpty())
            assertNull(repository.roomSnapshot.value)
            assertNull(repository.matchSnapshot.value)
        }

    private class LeaveRecordingApiClient(
        private val createRoomResult: OnlineRoomOperationResultDto,
    ) : RemoteOnlineApiClient {
        val privateLeaveRequests =
            mutableListOf<PrivateRoomLeaveRequestDto>()
        val submitActionRequests =
            mutableListOf<OnlinePlayerActionDto>()

        override suspend fun createRoom(
            request: CreateOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto = createRoomResult

        override suspend fun joinRoom(
            request: JoinOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto =
            error("joinRoom not used")

        override suspend fun leavePrivateRoom(
            request: PrivateRoomLeaveRequestDto,
        ): OnlineRoomOperationResultDto {
            privateLeaveRequests += request
            return OnlineRoomOperationResultDto(
                accepted = true,
            )
        }

        override suspend fun submitAction(
            action: OnlinePlayerActionDto,
        ): OnlineActionResultDto {
            submitActionRequests += action
            return error("submitAction not expected")
        }

        override suspend fun fetchRoomSnapshot(
            roomId: String,
        ): OnlineRoomSnapshotDto =
            error("fetchRoomSnapshot not used")

        override suspend fun fetchMatchSnapshot(
            matchId: String,
        ): OnlineMatchSnapshotDto =
            error("fetchMatchSnapshot not used")
    }
}