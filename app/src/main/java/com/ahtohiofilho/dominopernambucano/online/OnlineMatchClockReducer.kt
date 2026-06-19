package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import com.ahtohiofilho.dominopernambucano.domain.passTurn
import com.ahtohiofilho.dominopernambucano.domain.playMoveForCurrentPlayer
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.decrementPlayerClockMillis
import com.ahtohiofilho.dominopernambucano.match.findRandomPlayableMove
import com.ahtohiofilho.dominopernambucano.match.isPlayerClockExpired

internal fun reduceOnlineAuthoritativeClock(
    runtimeState: DominoMatchRuntimeState,
    elapsedMillis: Long,
): DominoMatchRuntimeState {
    if (!runtimeState.clockPolicy.enabled) {
        return runtimeState
    }

    if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
        return runtimeState
    }

    val gameState = runtimeState.gameState

    if (isRoundFinished(gameState) || isGameFinished(gameState)) {
        return runtimeState
    }

    val currentPlayerIndex = gameState.currentPlayerIndex

    if (
        isPlayerClockExpired(
            clocks = runtimeState.playerClockMillis,
            playerIndex = currentPlayerIndex,
        )
    ) {
        return forceOnlineTurn(
            runtimeState = runtimeState,
        )
    }

    if (elapsedMillis <= 0L) {
        return runtimeState
    }

    val updatedClocks = decrementPlayerClockMillis(
        clocks = runtimeState.playerClockMillis,
        playerIndex = currentPlayerIndex,
        elapsedMillis = elapsedMillis,
    )

    val clockedRuntimeState = runtimeState.copy(
        playerClockMillis = updatedClocks,
    )

    if (
        !isPlayerClockExpired(
            clocks = updatedClocks,
            playerIndex = currentPlayerIndex,
        )
    ) {
        return clockedRuntimeState
    }

    return forceOnlineTurn(
        runtimeState = clockedRuntimeState,
    )
}

private fun forceOnlineTurn(
    runtimeState: DominoMatchRuntimeState,
): DominoMatchRuntimeState {
    val forcedGameState = forceOnlineTurnForCurrentPlayer(
        gameState = runtimeState.gameState,
    )

    return runtimeState.copy(
        gameState = forcedGameState,
        phase = determineOnlineNextPhase(
            gameState = forcedGameState,
        ),
    )
}

private fun forceOnlineTurnForCurrentPlayer(
    gameState: DominoGameState,
): DominoGameState {
    val move = findRandomPlayableMove(
        state = gameState,
    )

    return if (move != null) {
        playMoveForCurrentPlayer(
            state = gameState,
            playableMove = move,
        )
    } else {
        passTurn(
            state = gameState,
        )
    }
}