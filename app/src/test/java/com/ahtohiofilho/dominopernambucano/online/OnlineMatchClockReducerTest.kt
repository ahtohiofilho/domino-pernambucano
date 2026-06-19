package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.createInitialPlayerClockMillis
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMatchClockReducerTest {
    @Test
    fun already_expired_current_player_is_forced_even_without_elapsed_time() {
        val gameState = createInitialDominoGameState()
        val currentPlayerIndex = gameState.currentPlayerIndex

        val expiredClocks = createInitialPlayerClockMillis(
            playerCount = gameState.players.size,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
        ).mapIndexed { index, remainingMillis ->
            if (index == currentPlayerIndex) {
                0L
            } else {
                remainingMillis
            }
        }

        val runtimeState = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.WaitingForLocalMove,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
            playerClockMillis = expiredClocks,
        )

        val reducedState = reduceOnlineAuthoritativeClock(
            runtimeState = runtimeState,
            elapsedMillis = 0L,
        )

        assertNotEquals(
            runtimeState.gameState,
            reducedState.gameState,
        )

        assertTrue(
            reducedState.phase == DominoMatchPhase.WaitingForLocalMove ||
                    reducedState.phase is DominoMatchPhase.PresentingPass ||
                    reducedState.phase == DominoMatchPhase.RoundSummary ||
                    reducedState.phase == DominoMatchPhase.MatchFinished,
        )
    }
}