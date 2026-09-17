package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.createOnlinePassTurnAction
import com.ahtohiofilho.dominopernambucano.online.createOnlinePlayMoveAction
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePollingCacheHardeningTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun updates_response_is_no_store_and_identical_get_observes_later_revision() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
            )
            val room = startFourHumanMatch(store)
            val matchId = requireNotNull(room.matchId)
            val initial = requireNotNull(store.getMatchSnapshot(matchId))

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    authoritativeTickIntervalMillis = 60_000L,
                )
            }

            val cachedClient = createClient {
                install(HttpCache)
            }

            val url =
                "/${OnlineRemoteRoutes.matchSnapshotUpdates(matchId)}" +
                    "?afterRevision=${initial.revision}"

            val firstResponse = cachedClient.get(url) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
            }

            assertEquals(HttpStatusCode.OK, firstResponse.status)

            val firstCacheControl =
                firstResponse.headers[HttpHeaders.CacheControl] ?: "<absent>"
            val firstBody = firstResponse.bodyAsText()
            val firstSnapshots =
                json.decodeFromString<List<OnlineMatchSnapshotDto>>(firstBody)

            assertTrue(firstSnapshots.isEmpty())
            assertTrue(
                "Expected no-store but Cache-Control was $firstCacheControl",
                firstCacheControl
                    .split(',')
                    .map { it.trim().lowercase() }
                    .contains("no-store"),
            )

            val actionResult = submitLegalActionForCurrentPlayer(
                store = store,
                roomId = room.roomId,
                matchId = matchId,
                snapshot = initial,
            )
            assertTrue(actionResult.accepted)

            val secondResponse = cachedClient.get(url) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
            }

            val secondBody = secondResponse.bodyAsText()
            val secondSnapshots =
                json.decodeFromString<List<OnlineMatchSnapshotDto>>(secondBody)

            assertTrue(
                secondSnapshots.any {
                    snapshot -> snapshot.revision > initial.revision
                },
            )
        }

    private fun submitLegalActionForCurrentPlayer(
        store: InMemoryOnlineServerStore,
        roomId: String,
        matchId: String,
        snapshot: OnlineMatchSnapshotDto,
    ) = snapshot.toRuntimeState(
        localPlayerIndex = snapshot.gameState.currentPlayerIndex,
    ).let { runtimeState ->
        val playerIndex = runtimeState.gameState.currentPlayerIndex
        val playerId = "player-${playerIndex + 1}"
        val move = findBasicBotMove(runtimeState.gameState)

        val action = if (move != null) {
            createOnlinePlayMoveAction(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                revision = snapshot.revision,
                move = move,
            )
        } else {
            createOnlinePassTurnAction(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                revision = snapshot.revision,
            )
        }

        store.submitAction(action)
    }

    private fun startFourHumanMatch(
        store: InMemoryOnlineServerStore,
    ) = requireNotNull(
        store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "Jogador 1",
            ),
        ).roomSnapshot,
    ).let { waitingRoom ->
        (2..4).forEach { playerNumber ->
            store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = waitingRoom.roomCode,
                    localPlayerId = "player-$playerNumber",
                    playerName = "Jogador $playerNumber",
                ),
            )
        }

        requireNotNull(
            store.startPrivateRoom(
                PrivateRoomStartRequestDto(
                    roomId = waitingRoom.roomId,
                    localPlayerId = "player-1",
                ),
            ).roomSnapshot,
        )
    }
}