package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

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