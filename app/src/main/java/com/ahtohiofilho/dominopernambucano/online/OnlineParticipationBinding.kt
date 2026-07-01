package com.ahtohiofilho.dominopernambucano.online

data class OnlineParticipationBinding(
    val roomId: String,
    val matchId: String? = null,
    val playerId: String,
    val localSeatIndex: Int,
)