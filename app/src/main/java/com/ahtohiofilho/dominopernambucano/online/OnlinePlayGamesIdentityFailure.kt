package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

enum class OnlinePlayGamesIdentityFailureReason {
    INVALID_PLAY_GAMES_CREDENTIAL,
    ACCOUNT_NOT_FOUND,
    IDENTITY_CONFLICT,
    SERVICE_UNAVAILABLE,
}

class OnlinePlayGamesIdentityException(
    val reason: OnlinePlayGamesIdentityFailureReason,
    cause: Throwable? = null,
) : IllegalStateException(reason.toFailureMessage(), cause)

internal fun ResponseException.toOnlinePlayGamesIdentityExceptionOrSelf():
    Throwable {
    val reason = when (response.status) {
        HttpStatusCode.Unauthorized ->
            OnlinePlayGamesIdentityFailureReason
                .INVALID_PLAY_GAMES_CREDENTIAL

        HttpStatusCode.NotFound ->
            OnlinePlayGamesIdentityFailureReason.ACCOUNT_NOT_FOUND

        HttpStatusCode.Conflict ->
            OnlinePlayGamesIdentityFailureReason.IDENTITY_CONFLICT

        HttpStatusCode.ServiceUnavailable ->
            OnlinePlayGamesIdentityFailureReason.SERVICE_UNAVAILABLE

        else -> return this
    }

    return OnlinePlayGamesIdentityException(
        reason = reason,
        cause = this,
    )
}

private fun OnlinePlayGamesIdentityFailureReason.toFailureMessage(): String {
    return when (this) {
        OnlinePlayGamesIdentityFailureReason
            .INVALID_PLAY_GAMES_CREDENTIAL ->
            "A credencial Play Games foi rejeitada."

        OnlinePlayGamesIdentityFailureReason.ACCOUNT_NOT_FOUND ->
            "Nenhuma conta vinculada a esse perfil Play Games foi encontrada."

        OnlinePlayGamesIdentityFailureReason.IDENTITY_CONFLICT ->
            "O perfil Play Games já está vinculado a outra conta."

        OnlinePlayGamesIdentityFailureReason.SERVICE_UNAVAILABLE ->
            "A validação Play Games está temporariamente indisponível."
    }
}
