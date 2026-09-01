package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DominoBotTurnWatchdogPolicyTest {
    @Test
    fun waiting_non_local_turn_is_eligible_for_local_watchdog() {
        val gameState = createInitialDominoGameState().copy(
            currentPlayerIndex = 2,
        )

        val runtimeState = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 6,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.WaitingForLocalMove,
            clockPolicy = DominoMatchClockPolicy.Disabled,
            playerClockMillis = emptyList(),
        )

        assertTrue(
            shouldRecoverLocalBotTurn(runtimeState),
        )
    }

    @Test
    fun waiting_local_turn_is_not_eligible_for_local_watchdog() {
        val gameState = createInitialDominoGameState().copy(
            currentPlayerIndex = 0,
        )

        val runtimeState = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 6,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.WaitingForLocalMove,
            clockPolicy = DominoMatchClockPolicy.Disabled,
            playerClockMillis = emptyList(),
        )

        assertFalse(
            shouldRecoverLocalBotTurn(runtimeState),
        )
    }

    @Test
    fun non_waiting_phase_is_not_eligible_for_local_watchdog() {
        val gameState = createInitialDominoGameState().copy(
            currentPlayerIndex = 2,
        )

        val runtimeState = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 6,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.RoundIntro,
            clockPolicy = DominoMatchClockPolicy.Disabled,
            playerClockMillis = emptyList(),
        )

        assertFalse(
            shouldRecoverLocalBotTurn(runtimeState),
        )
    }
}