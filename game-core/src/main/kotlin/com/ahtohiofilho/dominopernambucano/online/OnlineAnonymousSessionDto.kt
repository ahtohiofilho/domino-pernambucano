package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

/*
 * Credencial temporária emitida pelo servidor para identificar uma instalação
 * anônima. O token não deve ser registrado em logs ou anexado a traces.
 */
@Serializable
data class OnlineAnonymousSessionDto(
    val playerId: String,
    val accessToken: String,
    val expiresAtEpochMillis: Long,
)
