package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove

internal fun shouldAdvanceFakePlayerForSnapshotRequest(
    room: OnlineRoomSnapshotDto,
    gameState: DominoGameState,
): Boolean {
    if (isRoundFinished(gameState) || isGameFinished(gameState)) {
        return false
    }

    val currentParticipant = findOnlineParticipantForSeat(
        room = room,
        seatIndex = gameState.currentPlayerIndex,
    ) ?: return false

    return currentParticipant.participantType ==
            OnlineParticipantTypeDto.APPLICATION
}

internal fun createFakeCurrentTurnAction(
    room: OnlineRoomSnapshotDto,
    snapshot: OnlineMatchSnapshotDto,
    gameState: DominoGameState,
): OnlinePlayerActionDto? {
    val currentParticipant = findOnlineParticipantForSeat(
        room = room,
        seatIndex = gameState.currentPlayerIndex,
    ) ?: return null

    val move = findBasicBotMove(
        state = gameState,
    )

    return if (move != null) {
        createOnlinePlayMoveAction(
            roomId = snapshot.roomId,
            matchId = snapshot.matchId,
            playerId = currentParticipant.playerId,
            revision = snapshot.revision,
            move = move,
        )
    } else {
        createOnlinePassTurnAction(
            roomId = snapshot.roomId,
            matchId = snapshot.matchId,
            playerId = currentParticipant.playerId,
            revision = snapshot.revision,
        )
    }
}

private fun findOnlineParticipantForSeat(
    room: OnlineRoomSnapshotDto,
    seatIndex: Int,
): OnlineRoomPlayerDto? {
    return room.players.firstOrNull { player ->
        player.seatIndex == seatIndex
    }
}
