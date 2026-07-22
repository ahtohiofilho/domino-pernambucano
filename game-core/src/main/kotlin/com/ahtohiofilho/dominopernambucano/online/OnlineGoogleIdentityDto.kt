package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

/**
 * Prova de titularidade emitida pelo Google para o client ID do servidor.
 * O backend valida o token e persiste apenas o subject estavel (sub).
 */
@Serializable
data class OnlineGoogleIdentityRequestDto(
    val idToken: String,
)
