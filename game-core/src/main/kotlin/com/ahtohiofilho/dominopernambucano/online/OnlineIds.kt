package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class OnlineRoomId(
    val value: String,
)

@Serializable
data class OnlineMatchId(
    val value: String,
)

@Serializable
data class OnlinePlayerId(
    val value: String,
)

@Serializable
data class OnlineActionId(
    val value: String,
)

fun createOnlineActionId(): String {
    return "action-${UUID.randomUUID()}"
}