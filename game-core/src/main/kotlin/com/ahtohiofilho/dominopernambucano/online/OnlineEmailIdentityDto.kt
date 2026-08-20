package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

/**
 * Rotas compartilhadas da autenticação por e-mail.
 *
 * A Phase A mantém estas rotas sem exposição produtiva: o servidor somente as
 * instala quando um OnlineEmailVerificationService é injetado explicitamente
 * em ambiente TEST.
 */
object OnlineEmailIdentityRoutes {
    const val REQUEST_CODE = "accounts/identities/email/code"
    const val LINK_IDENTITY = "accounts/identities/email/link"
    const val RECOVER_ACCOUNT = "accounts/identities/email/recover"
}

/**
 * Solicita um código de uso único sem revelar se o e-mail já possui conta.
 */
@Serializable
data class OnlineEmailCodeRequestDto(
    val email: String,
)

@Serializable
data class OnlineEmailCodeRequestResponseDto(
    val accepted: Boolean = true,
)

/**
 * Prova de titularidade do endereço por um código de uso único.
 */
@Serializable
data class OnlineEmailIdentityRequestDto(
    val email: String,
    val code: String,
)
