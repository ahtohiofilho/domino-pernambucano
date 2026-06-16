package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase

data class DominoGameUiState(
    val gameState: DominoGameState,
    val roundNumber: Int,
    val localPlayerIndex: Int,
    val phase: DominoMatchPhase,
    val localPlayableMoves: List<PlayableMove>,
    val playerClockMillis: List<Long>,
)