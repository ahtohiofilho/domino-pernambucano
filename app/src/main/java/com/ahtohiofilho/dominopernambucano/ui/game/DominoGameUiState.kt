package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase

data class DominoGameUiState(
    val gameState: DominoGameState,
    val roundNumber: Int,
    val localPlayerIndex: Int,
    val phase: DominoMatchPhase,
    val localPlayableMoves: List<PlayableMove>,
    val playerClockMillis: List<Long>,
    val playerClockReserveMillis: List<Long>,
    val isTurnClockEnabled: Boolean,
    val turnClockTotalMillis: Long,
    val matchMode: DominoMatchMode = DominoMatchMode.OFFLINE_LOCAL,
    val onlinePresentationId: String? = null,
    val onlineSnapshotRevision: Long? = null,
    val postMatchStatistics: PostMatchStatistics? = null,
)