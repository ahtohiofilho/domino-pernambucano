package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEntry
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineTraceRouteTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun trace_route_requires_identity_and_participant_context() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
            )
            val room = requireNotNull(
                store.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "player-1",
                        playerName = "Jogador 1",
                    ),
                ).roomSnapshot,
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    traceArchive = OnlineTraceArchive(
                        outputDirectory = Files.createTempDirectory(
                            "online-trace-route-test",
                        ).toFile(),
                    ),
                )
            }

            val participantBatch = traceBatch(
                roomId = room.roomId,
                playerId = "player-1",
            )

            val missingIdentityResponse = postTraceBatch(
                batch = participantBatch,
                playerId = null,
            )

            assertEquals(
                HttpStatusCode.Unauthorized,
                missingIdentityResponse.status,
            )

            val foreignParticipantResponse = postTraceBatch(
                batch = traceBatch(
                    roomId = room.roomId,
                    playerId = null,
                ),
                playerId = "player-2",
            )

            assertEquals(
                HttpStatusCode.Forbidden,
                foreignParticipantResponse.status,
            )

            val forgedPlayerResponse = postTraceBatch(
                batch = traceBatch(
                    roomId = room.roomId,
                    playerId = "player-2",
                ),
                playerId = "player-1",
            )

            assertEquals(
                HttpStatusCode.Forbidden,
                forgedPlayerResponse.status,
            )

            val acceptedResponse = postTraceBatch(
                batch = participantBatch,
                playerId = "player-1",
            )

            assertEquals(
                HttpStatusCode.OK,
                acceptedResponse.status,
            )

            val result = json.decodeFromString<OnlineTraceBatchResultDto>(
                acceptedResponse.bodyAsText(),
            )

            assertTrue(result.accepted)
            assertEquals(
                1,
                result.storedEntryCount,
            )
        }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.postTraceBatch(
        batch: OnlineTraceBatchDto,
        playerId: String?,
    ) = client.post(
        urlString = "/${OnlineRemoteRoutes.SUBMIT_TRACE_BATCH}",
    ) {
        playerId?.let { resolvedPlayerId ->
            header(
                OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                resolvedPlayerId,
            )
        }
        contentType(
            ContentType.Application.Json,
        )
        setBody(
            json.encodeToString(batch),
        )
    }

    private fun traceBatch(
        roomId: String,
        playerId: String?,
    ): OnlineTraceBatchDto {
        return OnlineTraceBatchDto(
            entries = listOf(
                OnlineTraceEntry(
                    sequence = 1L,
                    event = OnlineTraceEvent(
                        occurredAtEpochMillis = 900L,
                        level = OnlineTraceLevel.INFO,
                        source = OnlineTraceSource.CLIENT_COORDINATOR,
                        type = OnlineTraceType.PRESENTATION_FINISHED,
                        context = OnlineTraceContext(
                            clientSessionId = "android-session-1",
                            roomId = roomId,
                            playerId = playerId,
                        ),
                    ),
                ),
            ),
        )
    }
}
