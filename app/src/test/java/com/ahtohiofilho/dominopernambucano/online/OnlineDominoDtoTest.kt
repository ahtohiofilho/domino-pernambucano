package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.createInitialPlayerClockMillis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineDominoDtoTest {
    @Test
    fun match_snapshot_can_round_trip_through_json() {
        val gameState = createInitialDominoGameState()
        val clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound

        val runtimeState = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.RoundIntro,
            clockPolicy = clockPolicy,
            playerClockMillis = createInitialPlayerClockMillis(
                playerCount = gameState.players.size,
                clockPolicy = clockPolicy,
            ),
        )

        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = 1L,
            serverEpochMillis = 1_000L,
        )

        val json = Json.encodeToString(snapshot)
        val restoredSnapshot = Json.decodeFromString<OnlineMatchSnapshotDto>(json)

        val restoredRuntimeState = restoredSnapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        assertEquals(
            snapshot.roomId,
            restoredSnapshot.roomId,
        )

        assertEquals(
            snapshot.matchId,
            restoredSnapshot.matchId,
        )

        assertEquals(
            snapshot.revision,
            restoredSnapshot.revision,
        )

        assertEquals(
            runtimeState.roundNumber,
            restoredRuntimeState.roundNumber,
        )

        assertEquals(
            runtimeState.phase,
            restoredRuntimeState.phase,
        )

        assertEquals(
            runtimeState.gameState.players.size,
            restoredRuntimeState.gameState.players.size,
        )

        assertEquals(
            runtimeState.gameState.sleepingPieces.size,
            restoredRuntimeState.gameState.sleepingPieces.size,
        )
    }
}