package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

/**
 * Código OAuth de uso único emitido pelo Play Games Services v2 para o
 * OAuth Web Client ID do servidor.
 */
@Serializable
data class OnlinePlayGamesIdentityRequestDto(
    val serverAuthCode: String,
)
