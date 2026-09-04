package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

/**
 * Shared transport contract for the public ranked matchmaking queue.
 *
 * Identity is intentionally absent. The server derives playerId and accountId
 * exclusively from the authenticated session.
 */
@Serializable
data class PublicRankedQueueEnterRequestDto(
    val playerName: String,
)

@Serializable
enum class PublicRankedQueueHttpStatus {
    WAITING,
    MATCHED,
    NOT_QUEUED,
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class PublicRankedQueueHttpResponseDto(
    val status: PublicRankedQueueHttpStatus,
    val queuePosition: Int? = null,
    @kotlinx.serialization.EncodeDefault(
        kotlinx.serialization.EncodeDefault.Mode.NEVER,
    )
    val participantCodes: List<String> = emptyList(),
    val matchId: String? = null,
    val localSeatIndex: Int? = null,
)
