package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

/**
 * Credencial emitida depois da promoção autoritativa de uma sessão anônima.
 * O playerId permanece estável; accountId identifica a conta persistente.
 */
@Serializable
data class OnlineAccountSessionDto(
    val accountId: String,
    val playerId: String,
    val accessToken: String,
    val expiresAtEpochMillis: Long,
)
