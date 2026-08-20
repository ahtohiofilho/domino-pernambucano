package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

enum class OnlineEmailIdentityFailureReason {
    INVALID_CODE,
    ACCOUNT_NOT_FOUND,
    IDENTITY_CONFLICT,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
}

class OnlineEmailIdentityException(
    val reason: OnlineEmailIdentityFailureReason,
    cause: Throwable? = null,
) : IllegalStateException(reason.name, cause)

internal fun ResponseException.toOnlineEmailIdentityExceptionOrSelf():
    Throwable {
    val reason = when (response.status) {
        HttpStatusCode.Unauthorized ->
            OnlineEmailIdentityFailureReason.INVALID_CODE
        HttpStatusCode.NotFound ->
            OnlineEmailIdentityFailureReason.ACCOUNT_NOT_FOUND
        HttpStatusCode.Conflict ->
            OnlineEmailIdentityFailureReason.IDENTITY_CONFLICT
        HttpStatusCode.TooManyRequests ->
            OnlineEmailIdentityFailureReason.RATE_LIMITED
        HttpStatusCode.ServiceUnavailable ->
            OnlineEmailIdentityFailureReason.SERVICE_UNAVAILABLE
        else -> return this
    }

    return OnlineEmailIdentityException(
        reason = reason,
        cause = this,
    )
}
