package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

@Serializable
data class PrivateRoomSeatChangeRequestDto(
    val roomId: String,
    val localPlayerId: String,
    val targetSeatIndex: Int,
)

@Serializable
data class PrivateRoomCompleteRequestDto(
    val roomId: String,
    val localPlayerId: String,
)

@Serializable
data class PrivateRoomRemoveAutomaticPlayerRequestDto(
    val roomId: String,
    val localPlayerId: String,
    val targetSeatIndex: Int,
)

@Serializable
data class PrivateRoomStartRequestDto(
    val roomId: String,
    val localPlayerId: String,
)

@Serializable
data class PrivateRoomLeaveRequestDto(
    val roomId: String,
    val localPlayerId: String,
)