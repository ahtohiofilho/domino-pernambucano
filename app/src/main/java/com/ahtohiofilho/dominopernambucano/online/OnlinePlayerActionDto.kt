package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import kotlinx.serialization.Serializable

@Serializable
enum class OnlinePlayerActionTypeDto {
    PLAY_MOVE,
    PASS_TURN,
    START_NEXT_ROUND,
    START_NEW_MATCH,
    REQUEST_SNAPSHOT,
    LEAVE_ROOM,
}

@Serializable
data class OnlinePlayerActionDto(
    val roomId: String,
    val matchId: String,
    val playerId: String,
    val revision: Long,
    val type: OnlinePlayerActionTypeDto,
    val move: OnlinePlayableMoveDto? = null,
)

@Serializable
data class OnlineActionResultDto(
    val accepted: Boolean,
    val revision: Long? = null,
    val reason: String? = null,
)

fun createOnlinePlayMoveAction(
    roomId: String,
    matchId: String,
    playerId: String,
    revision: Long,
    move: PlayableMove,
): OnlinePlayerActionDto {
    return OnlinePlayerActionDto(
        roomId = roomId,
        matchId = matchId,
        playerId = playerId,
        revision = revision,
        type = OnlinePlayerActionTypeDto.PLAY_MOVE,
        move = move.toOnlineDto(),
    )
}

fun createOnlinePassTurnAction(
    roomId: String,
    matchId: String,
    playerId: String,
    revision: Long,
): OnlinePlayerActionDto {
    return OnlinePlayerActionDto(
        roomId = roomId,
        matchId = matchId,
        playerId = playerId,
        revision = revision,
        type = OnlinePlayerActionTypeDto.PASS_TURN,
    )
}

fun createOnlineStartNextRoundAction(
    roomId: String,
    matchId: String,
    playerId: String,
    revision: Long,
): OnlinePlayerActionDto {
    return OnlinePlayerActionDto(
        roomId = roomId,
        matchId = matchId,
        playerId = playerId,
        revision = revision,
        type = OnlinePlayerActionTypeDto.START_NEXT_ROUND,
    )
}

fun createOnlineStartNewMatchAction(
    roomId: String,
    matchId: String,
    playerId: String,
    revision: Long,
): OnlinePlayerActionDto {
    return OnlinePlayerActionDto(
        roomId = roomId,
        matchId = matchId,
        playerId = playerId,
        revision = revision,
        type = OnlinePlayerActionTypeDto.START_NEW_MATCH,
    )
}

fun createOnlineSnapshotRequestAction(
    roomId: String,
    matchId: String,
    playerId: String,
    revision: Long,
): OnlinePlayerActionDto {
    return OnlinePlayerActionDto(
        roomId = roomId,
        matchId = matchId,
        playerId = playerId,
        revision = revision,
        type = OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT,
    )
}

fun createOnlineLeaveRoomAction(
    roomId: String,
    matchId: String,
    playerId: String,
    revision: Long,
): OnlinePlayerActionDto {
    return OnlinePlayerActionDto(
        roomId = roomId,
        matchId = matchId,
        playerId = playerId,
        revision = revision,
        type = OnlinePlayerActionTypeDto.LEAVE_ROOM,
    )
}