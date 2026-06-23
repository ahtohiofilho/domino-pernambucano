package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import io.ktor.server.application.ApplicationCall

data class OnlineRequestIdentity(
    val playerId: String,
)

fun interface OnlineRequestIdentityResolver {
    fun resolve(
        call: ApplicationCall,
    ): OnlineRequestIdentity?
}

/*
 * Resolver exclusivo de homologação local/remota controlada.
 *
 * Em produção, esta implementação deve ser substituída por um resolver que
 * valide JWT e construa a identidade a partir do principal autenticado.
 */
object DevelopmentHeaderOnlineRequestIdentityResolver :
    OnlineRequestIdentityResolver {
    override fun resolve(
        call: ApplicationCall,
    ): OnlineRequestIdentity? {
        val playerId = call.request.headers[
            OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
        ]
            ?.trim()
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return null

        return OnlineRequestIdentity(
            playerId = playerId,
        )
    }
}
