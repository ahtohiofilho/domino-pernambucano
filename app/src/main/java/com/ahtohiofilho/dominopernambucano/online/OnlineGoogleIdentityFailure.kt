package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

enum class OnlineGoogleIdentityFailureReason {
    INVALID_GOOGLE_CREDENTIAL,
    ACCOUNT_NOT_FOUND,
    IDENTITY_CONFLICT,
    SERVICE_UNAVAILABLE,
}

class OnlineGoogleIdentityException(
    val reason: OnlineGoogleIdentityFailureReason,
    cause: Throwable? = null,
) : IllegalStateException(reason.toFailureMessage(), cause)

internal fun ResponseException.toOnlineGoogleIdentityExceptionOrSelf():
    Throwable {
    val reason = when (response.status) {
        HttpStatusCode.Unauthorized ->
            OnlineGoogleIdentityFailureReason.INVALID_GOOGLE_CREDENTIAL

        HttpStatusCode.NotFound ->
            OnlineGoogleIdentityFailureReason.ACCOUNT_NOT_FOUND

        HttpStatusCode.Conflict ->
            OnlineGoogleIdentityFailureReason.IDENTITY_CONFLICT

        HttpStatusCode.ServiceUnavailable ->
            OnlineGoogleIdentityFailureReason.SERVICE_UNAVAILABLE

        else -> return this
    }

    return OnlineGoogleIdentityException(
        reason = reason,
        cause = this,
    )
}

private fun OnlineGoogleIdentityFailureReason.toFailureMessage(): String {
    return when (this) {
        OnlineGoogleIdentityFailureReason.INVALID_GOOGLE_CREDENTIAL ->
            "A credencial Google foi rejeitada."

        OnlineGoogleIdentityFailureReason.ACCOUNT_NOT_FOUND ->
            "Nenhuma conta vinculada a essa identidade Google foi encontrada."

        OnlineGoogleIdentityFailureReason.IDENTITY_CONFLICT ->
            "A identidade Google já está vinculada a outra conta."

        OnlineGoogleIdentityFailureReason.SERVICE_UNAVAILABLE ->
            "A validação da identidade Google está temporariamente indisponível."
    }
}
