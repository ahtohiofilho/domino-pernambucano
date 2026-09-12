package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

object OnlinePasswordIdentityRoutes {
    const val REGISTER = "accounts/identities/email-password/register"
    const val LOGIN = "accounts/identities/email-password/login"
    const val RESET = "accounts/identities/email-password/reset"
}

const val ONLINE_PASSWORD_MIN_LENGTH = 8
const val ONLINE_PASSWORD_MAX_LENGTH = 128

@Serializable
data class OnlinePasswordLoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class OnlinePasswordRegisterRequestDto(
    val email: String,
    val password: String,
    val code: String,
)

@Serializable
data class OnlinePasswordResetRequestDto(
    val email: String,
    val password: String,
    val code: String,
)
