package com.ahtohiofilho.dominopernambucano.online

enum class OnlineSessionKind {
    ANONYMOUS,
    ACCOUNT,
}

data class OnlineSessionCredential(
    val sessionKind: OnlineSessionKind,
    val playerId: String,
    val accessToken: String,
    val expiresAtEpochMillis: Long,
    val accountId: String? = null,
)

internal fun OnlineAnonymousSessionDto.toOnlineSessionCredential():
    OnlineSessionCredential {
    return OnlineSessionCredential(
        sessionKind = OnlineSessionKind.ANONYMOUS,
        playerId = playerId,
        accessToken = accessToken,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )
}

internal fun OnlineAccountSessionDto.toOnlineSessionCredential():
    OnlineSessionCredential {
    return OnlineSessionCredential(
        sessionKind = OnlineSessionKind.ACCOUNT,
        accountId = accountId,
        playerId = playerId,
        accessToken = accessToken,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )
}
