package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEntry
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorRemoteOnlineApiClientTest {
    @Test
    fun create_anonymous_session_posts_to_session_endpoint_and_decodes_result() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()
            val recordedDevelopmentPlayerIds = mutableListOf<String?>()
            val recordedAuthorizationHeaders = mutableListOf<String?>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/sessions/anonymous" to """
                        {
                          "playerId": "anonymous-player-1",
                          "accessToken": "test-access-token",
                          "expiresAtEpochMillis": 1700000000000
                        }
                    """.trimIndent(),
                ),
                recordedRequests = recordedRequests,
                recordedDevelopmentPlayerIds = recordedDevelopmentPlayerIds,
                recordedAuthorizationHeaders = recordedAuthorizationHeaders,
            )

            apiClient.setBearerAccessToken(
                accessToken = "expired-access-token",
            )

            val result = apiClient.createAnonymousSession()

            assertEquals(
                "anonymous-player-1",
                result.playerId,
            )
            assertEquals(
                "test-access-token",
                result.accessToken,
            )
            assertEquals(
                1_700_000_000_000L,
                result.expiresAtEpochMillis,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Post.value,
                        path = "/sessions/anonymous",
                    ),
                ),
                recordedRequests,
            )
            assertEquals(
                listOf(null),
                recordedDevelopmentPlayerIds,
            )
            assertEquals(
                listOf(null),
                recordedAuthorizationHeaders,
            )
        }

    @Test
    fun create_room_posts_to_rooms_and_decodes_result() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()
            val recordedDevelopmentPlayerIds = mutableListOf<String?>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/rooms" to """
                        {
                          "accepted": true,
                          "localSeatIndex": 0,
                          "roomSnapshot": {
                            "roomId": "room-1",
                            "roomCode": "123456",
                            "hostPlayerId": "player-1",
                            "status": "WAITING_FOR_PLAYERS",
                            "players": [
                              {
                                "playerId": "player-1",
                                "name": "Você",
                                "seatIndex": 0,
                                "connected": true
                              }
                            ]
                          }
                        }
                    """.trimIndent(),
                ),
                recordedRequests = recordedRequests,
                recordedDevelopmentPlayerIds = recordedDevelopmentPlayerIds,
            )

            val result = apiClient.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Você",
                ),
            )

            assertEquals(
                true,
                result.accepted,
            )
            assertEquals(
                0,
                result.localSeatIndex,
            )
            assertEquals(
                "room-1",
                result.roomSnapshot?.roomId,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Post.value,
                        path = "/rooms",
                    ),
                ),
                recordedRequests,
            )
            assertEquals(
                listOf("player-1"),
                recordedDevelopmentPlayerIds,
            )
        }

    @Test
    fun create_room_uses_bearer_and_omits_development_header() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()
            val recordedDevelopmentPlayerIds = mutableListOf<String?>()
            val recordedAuthorizationHeaders = mutableListOf<String?>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/rooms" to """
                        {
                          "accepted": true,
                          "localSeatIndex": 0,
                          "roomSnapshot": {
                            "roomId": "room-1",
                            "roomCode": "123456",
                            "hostPlayerId": "anonymous-player-1",
                            "status": "WAITING_FOR_PLAYERS",
                            "players": [
                              {
                                "playerId": "anonymous-player-1",
                                "name": "Você",
                                "seatIndex": 0,
                                "connected": true
                              }
                            ]
                          }
                        }
                    """.trimIndent(),
                ),
                recordedRequests = recordedRequests,
                recordedDevelopmentPlayerIds = recordedDevelopmentPlayerIds,
                recordedAuthorizationHeaders = recordedAuthorizationHeaders,
            )

            apiClient.setBearerAccessToken(
                accessToken = "test-access-token",
            )

            val result = apiClient.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "anonymous-player-1",
                    playerName = "Você",
                ),
            )

            assertEquals(
                true,
                result.accepted,
            )

            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Post.value,
                        path = "/rooms",
                    ),
                ),
                recordedRequests,
            )

            assertEquals(
                listOf("Bearer test-access-token"),
                recordedAuthorizationHeaders,
            )

            assertEquals(
                listOf(null),
                recordedDevelopmentPlayerIds,
            )
        }

    @Test
    fun join_room_posts_to_rooms_join_and_decodes_result() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/rooms/join" to """
                        {
                          "accepted": true,
                          "localSeatIndex": 1,
                          "roomSnapshot": {
                            "roomId": "room-1",
                            "roomCode": "123456",
                            "hostPlayerId": "player-1",
                            "status": "WAITING_FOR_PLAYERS",
                            "players": [
                              {
                                "playerId": "player-1",
                                "name": "Você",
                                "seatIndex": 0,
                                "connected": true
                              },
                              {
                                "playerId": "player-2",
                                "name": "Jogador 2",
                                "seatIndex": 1,
                                "connected": true
                              }
                            ]
                          }
                        }
                    """.trimIndent(),
                ),
                recordedRequests = recordedRequests,
            )

            val result = apiClient.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = "123456",
                    localPlayerId = "player-2",
                    playerName = "Jogador 2",
                ),
            )

            assertEquals(
                true,
                result.accepted,
            )
            assertEquals(
                1,
                result.localSeatIndex,
            )
            assertEquals(
                "room-1",
                result.roomSnapshot?.roomId,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Post.value,
                        path = "/rooms/join",
                    ),
                ),
                recordedRequests,
            )
        }

    @Test
    fun submit_action_posts_to_matches_actions_and_decodes_result() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/matches/actions" to """
                        {
                          "accepted": true,
                          "revision": 2,
                          "actionId": "action-1"
                        }
                    """.trimIndent(),
                ),
                recordedRequests = recordedRequests,
            )

            val action = createOnlinePassTurnAction(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "player-1",
                revision = 1L,
                actionId = "action-1",
            )

            val result = apiClient.submitAction(
                action = action,
            )

            assertEquals(
                true,
                result.accepted,
            )
            assertEquals(
                2L,
                result.revision,
            )
            assertEquals(
                "action-1",
                result.actionId,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Post.value,
                        path = "/matches/actions",
                    ),
                ),
                recordedRequests,
            )
        }

    @Test
    fun fetch_room_snapshot_gets_room_by_id_and_decodes_result() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/rooms/room-1" to """
                        {
                          "roomId": "room-1",
                          "roomCode": "123456",
                          "hostPlayerId": "player-1",
                          "status": "IN_MATCH",
                          "players": [
                            {
                              "playerId": "player-1",
                              "name": "Você",
                              "seatIndex": 0,
                              "connected": true
                            }
                          ],
                          "matchId": "match-1"
                        }
                    """.trimIndent(),
                ),
                recordedRequests = recordedRequests,
            )

            apiClient.setDevelopmentPlayerId(
                playerId = "player-1",
            )

            val result = apiClient.fetchRoomSnapshot(
                roomId = "room-1",
            )

            assertEquals(
                "room-1",
                result.roomId,
            )
            assertEquals(
                "match-1",
                result.matchId,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Get.value,
                        path = "/rooms/room-1",
                    ),
                ),
                recordedRequests,
            )
        }

    @Test
    fun submit_trace_batch_posts_to_traces_and_decodes_result() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()
            val recordedDevelopmentPlayerIds = mutableListOf<String?>()
            val recordedAuthorizationHeaders = mutableListOf<String?>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/traces" to """
                        {
                          "accepted": true,
                          "storedEntryCount": 1
                        }
                    """.trimIndent(),
                ),
                recordedRequests = recordedRequests,
                recordedDevelopmentPlayerIds =
                    recordedDevelopmentPlayerIds,
                recordedAuthorizationHeaders =
                    recordedAuthorizationHeaders,
            )

            apiClient.setBearerAccessToken(
                accessToken = "trace-access-token",
            )

            val result = apiClient.submitTraceBatch(
                batch = OnlineTraceBatchDto(
                    entries = listOf(
                        OnlineTraceEntry(
                            sequence = 1L,
                            event = OnlineTraceEvent(
                                occurredAtEpochMillis = 1_000L,
                                level = OnlineTraceLevel.INFO,
                                source = OnlineTraceSource.CLIENT_UI,
                                type = OnlineTraceType.ANIMATION_FINISHED,
                                context = OnlineTraceContext(
                                    clientSessionId = "android-1",
                                    roomId = "room-1",
                                    matchId = "match-1",
                                ),
                            ),
                        ),
                    ),
                ),
            )

            assertEquals(
                OnlineTraceBatchResultDto(
                    accepted = true,
                    storedEntryCount = 1,
                ),
                result,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Post.value,
                        path = "/traces",
                    ),
                ),
                recordedRequests,
            )
            assertEquals(
                listOf("Bearer trace-access-token"),
                recordedAuthorizationHeaders,
            )
            assertEquals(
                listOf(null),
                recordedDevelopmentPlayerIds,
            )
        }

    @Test
    fun fetch_match_snapshot_gets_match_by_id_and_decodes_result() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()
            val json = createOnlineJson()

            val matchSnapshot = DominoMatchRuntimeState(
                gameState = createInitialDominoGameState(),
                roundNumber = 1,
                localPlayerIndex = 0,
                phase = DominoMatchPhase.WaitingForLocalMove,
                clockPolicy = DominoMatchClockPolicy.Disabled,
                playerClockMillis = emptyList(),
            ).toOnlineSnapshotDto(
                roomId = "room-1",
                matchId = "match-1",
                revision = 3L,
                serverEpochMillis = 1_000L,
            )

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/matches/match-1" to json.encodeToString(matchSnapshot),
                ),
                recordedRequests = recordedRequests,
            )

            apiClient.setDevelopmentPlayerId(
                playerId = "player-1",
            )

            val result = apiClient.fetchMatchSnapshot(
                matchId = "match-1",
            )

            assertEquals(
                "room-1",
                result.roomId,
            )
            assertEquals(
                "match-1",
                result.matchId,
            )
            assertEquals(
                3L,
                result.revision,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Get.value,
                        path = "/matches/match-1",
                    ),
                ),
                recordedRequests,
            )
        }

    @Test
    fun create_room_throws_client_request_exception_when_backend_returns_unauthorized() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()
            val recordedDevelopmentPlayerIds = mutableListOf<String?>()
            val recordedAuthorizationHeaders = mutableListOf<String?>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/rooms" to "",
                ),
                recordedRequests = recordedRequests,
                responseStatusesByPath = mapOf(
                    "/rooms" to HttpStatusCode.Unauthorized,
                ),
                recordedDevelopmentPlayerIds = recordedDevelopmentPlayerIds,
                recordedAuthorizationHeaders = recordedAuthorizationHeaders,
            )

            apiClient.setBearerAccessToken(
                accessToken = "rejected-access-token",
            )

            val failure = try {
                apiClient.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "anonymous-player-1",
                        playerName = "Jogador 1",
                    ),
                )
                null
            } catch (error: Throwable) {
                error
            }

            assertTrue(failure is ClientRequestException)
            assertEquals(
                HttpStatusCode.Unauthorized,
                (failure as ClientRequestException).response.status,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Post.value,
                        path = "/rooms",
                    ),
                ),
                recordedRequests,
            )
            assertEquals(
                listOf(null),
                recordedDevelopmentPlayerIds,
            )
            assertEquals(
                listOf("Bearer rejected-access-token"),
                recordedAuthorizationHeaders,
            )
        }

    @Test
    fun fetch_room_snapshot_throws_client_request_exception_when_backend_returns_unauthorized() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()
            val recordedAuthorizationHeaders = mutableListOf<String?>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/rooms/room-1" to "",
                ),
                recordedRequests = recordedRequests,
                responseStatusesByPath = mapOf(
                    "/rooms/room-1" to HttpStatusCode.Unauthorized,
                ),
                recordedAuthorizationHeaders = recordedAuthorizationHeaders,
            )

            apiClient.setBearerAccessToken(
                accessToken = "rejected-access-token",
            )

            val failure = try {
                apiClient.fetchRoomSnapshot(
                    roomId = "room-1",
                )
                null
            } catch (error: Throwable) {
                error
            }

            assertTrue(failure is ClientRequestException)
            assertEquals(
                HttpStatusCode.Unauthorized,
                (failure as ClientRequestException).response.status,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Get.value,
                        path = "/rooms/room-1",
                    ),
                ),
                recordedRequests,
            )
            assertEquals(
                listOf("Bearer rejected-access-token"),
                recordedAuthorizationHeaders,
            )
        }

    @Test
    fun create_room_throws_server_response_exception_when_backend_returns_internal_server_error() =
        runBlocking {
            val recordedRequests = mutableListOf<RecordedRequest>()

            val apiClient = createApiClient(
                responsesByPath = mapOf(
                    "/rooms" to "",
                ),
                recordedRequests = recordedRequests,
                responseStatusesByPath = mapOf(
                    "/rooms" to HttpStatusCode.InternalServerError,
                ),
            )

            val failure = try {
                apiClient.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "player-1",
                        playerName = "Jogador 1",
                    ),
                )
                null
            } catch (error: Throwable) {
                error
            }

            assertTrue(failure is ServerResponseException)
            assertEquals(
                HttpStatusCode.InternalServerError,
                (failure as ServerResponseException).response.status,
            )
            assertEquals(
                listOf(
                    RecordedRequest(
                        method = HttpMethod.Post.value,
                        path = "/rooms",
                    ),
                ),
                recordedRequests,
            )
        }

    private fun createApiClient(
        responsesByPath: Map<String, String>,
        recordedRequests: MutableList<RecordedRequest>,
        responseStatusesByPath: Map<String, HttpStatusCode> =
            emptyMap(),
        recordedDevelopmentPlayerIds: MutableList<String?>? = null,
        recordedAuthorizationHeaders: MutableList<String?>? = null,
    ): KtorRemoteOnlineApiClient {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath

            recordedRequests += RecordedRequest(
                method = request.method.value,
                path = path,
            )

            recordedDevelopmentPlayerIds?.add(
                request.headers[
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                ],
            )

            recordedAuthorizationHeaders?.add(
                request.headers[
                    HttpHeaders.Authorization,
                ],
            )

            val responseBody = requireNotNull(responsesByPath[path]) {
                "Resposta fake não configurada para $path."
            }

            respond(
                content = responseBody,
                status = responseStatusesByPath[path] ?: HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val httpClient = HttpClient(mockEngine) {
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    createOnlineJson(),
                )
            }
        }

        return KtorRemoteOnlineApiClient(
            config = OnlineBackendConfig.remote(
                baseUrl = "http://localhost",
            ),
            httpClient = httpClient,
        )
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
    )
}