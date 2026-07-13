package com.ahtohiofilho.dominopernambucano.online

data class OnlineDevelopmentParticipantRequest(
    val roomCode: String,
    val playerId: String,
    val playerName: String,
)

interface OnlineDevelopmentParticipantCompletion {
    suspend fun addApplicationParticipant(
        request: OnlineDevelopmentParticipantRequest,
    ): OnlineRoomOperationResultDto
}
