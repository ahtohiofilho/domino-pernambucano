package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.hasPlayablePiece
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import com.ahtohiofilho.dominopernambucano.domain.passTurn
import com.ahtohiofilho.dominopernambucano.domain.playMoveForCurrentPlayer
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove

private const val FAKE_PLAYER_ID_PREFIX = "fake-player-"

internal fun shouldAdvanceFakePlayerForSnapshotRequest(
    room: OnlineRoomSnapshotDto,
    gameState: DominoGameState,
): Boolean {
    if (isRoundFinished(gameState) || isGameFinished(gameState)) {
        return false
    }

    val currentPlayerId = findOnlinePlayerIdForSeat(
        room = room,
        seatIndex = gameState.currentPlayerIndex,
    ) ?: return false

    return isFakeOnlinePlayerId(currentPlayerId)
}

internal fun advanceSingleFakeTurn(
    gameState: DominoGameState,
): DominoGameState {
    if (
        !hasPlayablePiece(
            state = gameState,
            playerIndex = gameState.currentPlayerIndex,
        )
    ) {
        return passTurn(
            state = gameState,
        )
    }

    val botMove = findBasicBotMove(
        state = gameState,
    )

    return if (botMove != null) {
        playMoveForCurrentPlayer(
            state = gameState,
            playableMove = botMove,
        )
    } else {
        passTurn(
            state = gameState,
        )
    }
}

private fun findOnlinePlayerIdForSeat(
    room: OnlineRoomSnapshotDto,
    seatIndex: Int,
): String? {
    return room.players.firstOrNull { player ->
        player.seatIndex == seatIndex
    }?.playerId
}

private fun isFakeOnlinePlayerId(
    playerId: String,
): Boolean {
    return playerId.startsWith(FAKE_PLAYER_ID_PREFIX)
}