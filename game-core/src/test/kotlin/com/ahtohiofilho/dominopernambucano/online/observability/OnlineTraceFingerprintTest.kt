package com.ahtohiofilho.dominopernambucano.online.observability

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OnlineTraceFingerprintTest {
    @Test
    fun is_deterministic_for_the_same_runtime_state() {
        val runtimeState = createRuntimeState()

        val firstFingerprint = createOnlineTraceStateFingerprint(
            runtimeState = runtimeState,
            automaticPlayerIndexes = setOf(1, 3),
        )

        val secondFingerprint = createOnlineTraceStateFingerprint(
            runtimeState = runtimeState,
            automaticPlayerIndexes = setOf(3, 1),
        )

        assertEquals(firstFingerprint, secondFingerprint)
    }

    @Test
    fun changes_when_authoritative_state_changes() {
        val runtimeState = createRuntimeState()
        val changedRuntimeState = runtimeState.copy(
            gameState = runtimeState.gameState.copy(
                currentPlayerIndex =
                    (runtimeState.gameState.currentPlayerIndex + 1) % 4,
            ),
        )

        val originalFingerprint = createOnlineTraceStateFingerprint(
            runtimeState = runtimeState,
            automaticPlayerIndexes = emptySet(),
        )

        val changedFingerprint = createOnlineTraceStateFingerprint(
            runtimeState = changedRuntimeState,
            automaticPlayerIndexes = emptySet(),
        )

        assertNotEquals(originalFingerprint, changedFingerprint)
    }

    private fun createRuntimeState(): DominoMatchRuntimeState {
        val gameState = createInitialDominoGameState()

        return DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.WaitingForLocalMove,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
        )
    }
}
