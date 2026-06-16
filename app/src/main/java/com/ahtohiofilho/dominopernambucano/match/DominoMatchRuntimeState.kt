package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState

data class DominoMatchRuntimeState(
    val gameState: DominoGameState,
    val roundNumber: Int,
    val localPlayerIndex: Int,
    val phase: DominoMatchPhase,
    val playerClockMillis: List<Long> = createInitialPlayerClockMillis(
        playerCount = gameState.players.size,
    ),
)