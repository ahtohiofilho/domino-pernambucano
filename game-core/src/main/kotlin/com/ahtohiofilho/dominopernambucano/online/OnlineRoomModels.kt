package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import kotlinx.serialization.Serializable

@Serializable
enum class OnlineRoomStatusDto {
    WAITING_FOR_PLAYERS,
    IN_MATCH,
    FINISHED,
    CLOSED,
}

@Serializable
data class OnlineRoomPlayerDto(
    val playerId: String,
    val name: String,
    val seatIndex: Int?,
    val connected: Boolean,
    val participantType: OnlineParticipantTypeDto =
        OnlineParticipantTypeDto.HUMAN,
)

@Serializable
data class OnlineRoomSnapshotDto(
    val roomId: String,
    val roomCode: String,
    val hostPlayerId: String,
    val status: OnlineRoomStatusDto,
    val players: List<OnlineRoomPlayerDto>,
    val matchMode: DominoMatchMode =
        DominoMatchMode.PRIVATE_UNRANKED,
    val matchId: String? = null,
    val createdAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
)

@Serializable
data class CreateOnlineRoomRequestDto(
    val localPlayerId: String,
    val playerName: String,
)

@Serializable
data class JoinOnlineRoomRequestDto(
    val roomCode: String,
    val localPlayerId: String,
    val playerName: String,
)

@Serializable
data class OnlineRoomOperationResultDto(
    val accepted: Boolean,
    val roomSnapshot: OnlineRoomSnapshotDto? = null,
    val localSeatIndex: Int? = null,
    val reason: String? = null,
)