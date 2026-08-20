package com.ahtohiofilho.dominopernambucano.online

import java.util.Locale

internal const val ONLINE_EMAIL_VERIFICATION_CODE_LENGTH = 6

class OnlineEmailIdentityRepository(
    private val apiClient: RemoteOnlineApiClient,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
) {
    suspend fun requestVerificationCode(
        rawEmail: String,
    ): String {
        val email = normalizeOnlineEmailAddressOrNull(rawEmail)
            ?: throw IllegalArgumentException(
                "O e-mail informado é inválido.",
            )

        val response = apiClient.requestEmailCode(
            request = OnlineEmailCodeRequestDto(
                email = email,
            ),
        )
        check(response.accepted) {
            "O servidor não confirmou a solicitação do código."
        }

        return email
    }

    suspend fun linkEmailIdentity(
        rawEmail: String,
        rawCode: String,
    ): OnlineSessionCredential {
        val request = createEmailIdentityRequest(
            rawEmail = rawEmail,
            rawCode = rawCode,
        )

        return sessionCredentialRepository.promoteCurrentCredential {
                accessToken ->
            apiClient.linkEmailIdentity(
                request = request,
                accessToken = accessToken,
            )
        }
    }

    suspend fun recoverEmailAccount(
        rawEmail: String,
        rawCode: String,
    ): OnlineSessionCredential {
        val request = createEmailIdentityRequest(
            rawEmail = rawEmail,
            rawCode = rawCode,
        )

        return sessionCredentialRepository.recoverAccountCredential {
            apiClient.recoverEmailAccount(
                request = request,
            )
        }
    }
}

internal fun normalizeOnlineEmailAddressOrNull(
    rawEmail: String,
): String? {
    val email = rawEmail
        .trim()
        .lowercase(Locale.ROOT)

    if (
        email.length !in 3..254 ||
        email.any { character -> character.isWhitespace() }
    ) {
        return null
    }

    val atIndex = email.indexOf('@')
    if (
        atIndex <= 0 ||
        atIndex != email.lastIndexOf('@') ||
        atIndex >= email.lastIndex
    ) {
        return null
    }

    val localPart = email.substring(0, atIndex)
    val domainPart = email.substring(atIndex + 1)

    if (
        localPart.length > 64 ||
        domainPart.length > 253 ||
        localPart.startsWith('.') ||
        localPart.endsWith('.') ||
        domainPart.startsWith('.') ||
        domainPart.endsWith('.') ||
        localPart.contains("..") ||
        domainPart.contains("..")
    ) {
        return null
    }

    return email
}

internal fun normalizeOnlineEmailCodeOrNull(
    rawCode: String,
): String? {
    val code = rawCode.trim()
    return code.takeIf {
        it.length == ONLINE_EMAIL_VERIFICATION_CODE_LENGTH &&
            it.all { character -> character.isDigit() }
    }
}

private fun createEmailIdentityRequest(
    rawEmail: String,
    rawCode: String,
): OnlineEmailIdentityRequestDto {
    val email = normalizeOnlineEmailAddressOrNull(rawEmail)
        ?: throw IllegalArgumentException(
            "O e-mail informado é inválido.",
        )
    val code = normalizeOnlineEmailCodeOrNull(rawCode)
        ?: throw IllegalArgumentException(
            "O código de verificação deve ter 6 dígitos.",
        )

    return OnlineEmailIdentityRequestDto(
        email = email,
        code = code,
    )
}
