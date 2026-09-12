package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

enum class OnlinePasswordIdentityFailureReason {
    INVALID_REQUEST,
    UNAUTHORIZED,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_EXISTS,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
}

class OnlinePasswordIdentityException(
    val reason: OnlinePasswordIdentityFailureReason,
    cause: Throwable? = null,
) : IllegalStateException(reason.name, cause)

internal fun ResponseException.toOnlinePasswordIdentityExceptionOrSelf():
    Throwable {
    val reason = when (response.status) {
        HttpStatusCode.BadRequest ->
            OnlinePasswordIdentityFailureReason.INVALID_REQUEST
        HttpStatusCode.Unauthorized ->
            OnlinePasswordIdentityFailureReason.UNAUTHORIZED
        HttpStatusCode.NotFound ->
            OnlinePasswordIdentityFailureReason.ACCOUNT_NOT_FOUND
        HttpStatusCode.Conflict ->
            OnlinePasswordIdentityFailureReason.ACCOUNT_EXISTS
        HttpStatusCode.TooManyRequests ->
            OnlinePasswordIdentityFailureReason.RATE_LIMITED
        HttpStatusCode.ServiceUnavailable ->
            OnlinePasswordIdentityFailureReason.SERVICE_UNAVAILABLE
        else -> return this
    }

    return OnlinePasswordIdentityException(
        reason = reason,
        cause = this,
    )
}
