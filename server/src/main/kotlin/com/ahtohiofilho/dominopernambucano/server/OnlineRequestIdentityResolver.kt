package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

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

class StoreValidatedAccountIdentityResolver(
    private val delegate: OnlineRequestIdentityResolver,
    private val store: OnlineServerStore,
) : OnlineRequestIdentityResolver {
    override fun resolve(
        call: ApplicationCall,
    ): OnlineRequestIdentity? {
        val identity = delegate.resolve(call)
            ?: return null

        if (identity.kind != OnlinePrincipalKind.ACCOUNT) {
            return identity
        }

        val accountId = identity.accountId
            ?.takeIf { value -> value.isNotBlank() }
            ?: return null

        return identity.takeIf {
            store.isAccountIdentityActive(
                accountId = accountId,
                playerId = identity.playerId,
            )
        }
    }
}

internal fun createDefaultOnlineRequestIdentityResolver(
    sessionTokenService: OnlineSessionTokenService,
    serverEnvironment: OnlineServerEnvironment,
    store: OnlineServerStore? = null,
): OnlineRequestIdentityResolver {
    val resolvers = buildList {
        val bearerResolver = BearerOnlineRequestIdentityResolver(
            sessionTokenService = sessionTokenService,
        )

        add(
            store?.let { authoritativeStore ->
                StoreValidatedAccountIdentityResolver(
                    delegate = bearerResolver,
                    store = authoritativeStore,
                )
            } ?: bearerResolver,
        )

        if (serverEnvironment.allowsDevelopmentIdentityHeader) {
            add(DevelopmentHeaderOnlineRequestIdentityResolver)
        }
    }

    return CompositeOnlineRequestIdentityResolver(
        resolvers = resolvers,
    )
}

/*
 * Resolver exclusivo de homologação local/remota controlada.
 *
 * Em produção, esta implementação não é registrada. O principal sintético
 * abaixo existe apenas para manter os fluxos de desenvolvimento compatíveis
 * com o contrato autenticado abstrato.
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
            principalId = "development-principal:$playerId",
            sessionId = "development-session:$playerId",
            kind = OnlinePrincipalKind.ANONYMOUS,
            accountId = null,
        )
    }
}
