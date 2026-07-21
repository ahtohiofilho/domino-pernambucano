package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchClassification
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineServerStoreStateCompatibilityTest {
    @Test
    fun schema_one_without_new_fields_restores_with_safe_defaults() {
        val originalStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val room = startFourHumanMatch(originalStore)
        val matchId = requireNotNull(room.matchId)
        val json = Json {
            encodeDefaults = true
        }
        val encodedState = json.encodeToJsonElement(
            originalStore.snapshotPersistentState(),
        ).jsonObject
        val legacyRoot = encodedState.toMutableMap().apply {
            this["schemaVersion"] = JsonPrimitive(1)
            remove("rankedResults")

            this["matches"] = JsonArray(
                getValue("matches").jsonArray.map { matchElement ->
                    JsonObject(
                        matchElement.jsonObject
                            .toMutableMap()
                            .apply {
                                remove("automaticRoundSeatIndexes")
                                remove("classification")
                                remove("rankedMetricAccumulator")
                            },
                    )
                },
            )
        }
        val legacyState =
            json.decodeFromJsonElement<OnlineServerStoreState>(
                JsonObject(legacyRoot),
            )
        val restoredStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        restoredStore.restorePersistentState(legacyState)

        assertEquals(1, legacyState.schemaVersion)
        assertTrue(legacyState.rankedResults.isEmpty())
        assertEquals(
            RankedMatchClassification.UNRANKED,
            restoredStore.getRankedMatchClassification(matchId),
        )
        assertNull(restoredStore.getRankedMatchResult(matchId))
        assertEquals(
            0,
            requireNotNull(
                restoredStore.getRankedMatchMetricAccumulator(matchId),
            ).completedRounds,
        )
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
    ).let { room ->
        listOf(2, 3, 4).forEach { playerNumber ->
            val result = store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "player-$playerNumber",
                    playerName = "Jogador $playerNumber",
                ),
            )

            assertTrue(result.accepted)
        }

        requireNotNull(store.getRoomSnapshot(room.roomId))
    }
}
