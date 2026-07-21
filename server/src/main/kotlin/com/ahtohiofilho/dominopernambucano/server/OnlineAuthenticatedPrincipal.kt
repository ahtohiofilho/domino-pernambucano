package com.ahtohiofilho.dominopernambucano.server

import kotlinx.serialization.Serializable

@Serializable
enum class OnlinePrincipalKind {
    ANONYMOUS,
    ACCOUNT,
}

data class OnlineAuthenticatedPrincipal(
    val playerId: String,
    val principalId: String = playerId,
    val sessionId: String = principalId,
    val kind: OnlinePrincipalKind = OnlinePrincipalKind.ANONYMOUS,
    val accountId: String? = null,
)

typealias OnlineRequestIdentity = OnlineAuthenticatedPrincipal
