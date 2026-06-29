package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

data class OnlineRequestIdentity(
    val playerId: String,
)

fun interface OnlineRequestIdentityResolver {
    fun resolve(
        call: ApplicationCall,
    ): OnlineRequestIdentity?
}

class CompositeOnlineRequestIdentityResolver(
    private val resolvers: List<OnlineRequestIdentityResolver>,
) : OnlineRequestIdentityResolver {
    override fun resolve(
        call: ApplicationCall,
    ): OnlineRequestIdentity? {
        return resolvers.firstNotNullOfOrNull { resolver ->
            resolver.resolve(
                call = call,
            )
        }
    }
}

class BearerOnlineRequestIdentityResolver(
    private val sessionTokenService: OnlineSessionTokenService,
) : OnlineRequestIdentityResolver {
    override fun resolve(
        call: ApplicationCall,
    ): OnlineRequestIdentity? {
        val authorizationHeader = call.request.headers[
            HttpHeaders.Authorization,
        ] ?: return null

        val token = authorizationHeader
            .trim()
            .takeIf { value ->
                value.startsWith(
                    prefix = "Bearer ",
                    ignoreCase = true,
                )
            }
            ?.drop("Bearer ".length)
            ?.trim()
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: return null

        return sessionTokenService.resolveAccessToken(
            accessToken = token,
        )
    }
}

internal fun createDefaultOnlineRequestIdentityResolver(
    sessionTokenService: OnlineSessionTokenService,
): OnlineRequestIdentityResolver {
    return CompositeOnlineRequestIdentityResolver(
        resolvers = listOf(
            BearerOnlineRequestIdentityResolver(
                sessionTokenService = sessionTokenService,
            ),
            DevelopmentHeaderOnlineRequestIdentityResolver,
        ),
    )
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
