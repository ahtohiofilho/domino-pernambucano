package com.ahtohiofilho.dominopernambucano.server

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal val ANONYMOUS_SESSION_RATE_LIMIT_NAME =
    RateLimitName("anonymous-session")
internal val AUTHENTICATED_MUTATION_RATE_LIMIT_NAME =
    RateLimitName("authenticated-mutation")
internal val AUTHENTICATED_READ_RATE_LIMIT_NAME =
    RateLimitName("authenticated-read")
internal val CLIENT_TRACE_RATE_LIMIT_NAME =
    RateLimitName("client-trace")

private const val UNRESOLVED_IDENTITY_RATE_LIMIT_KEY =
    "unresolved-identity"

data class OnlineServerRateLimitPolicy(
    val anonymousSessionRequestLimit: Int = 60,
    val authenticatedMutationRequestLimit: Int = 120,
    val authenticatedReadRequestLimit: Int = 240,
    val clientTraceRequestLimit: Int = 30,
    val refillPeriod: Duration = 1.minutes,
) {
    init {
        require(anonymousSessionRequestLimit > 0)
        require(authenticatedMutationRequestLimit > 0)
        require(authenticatedReadRequestLimit > 0)
        require(clientTraceRequestLimit > 0)
        require(refillPeriod.isPositive())
    }

    companion object {
        val Default = OnlineServerRateLimitPolicy()
    }
}

internal fun Application.installOnlineServerRateLimits(
    policy: OnlineServerRateLimitPolicy,
    identityResolver: OnlineRequestIdentityResolver,
) {
    install(RateLimit) {
        register(ANONYMOUS_SESSION_RATE_LIMIT_NAME) {
            rateLimiter(
                limit = policy.anonymousSessionRequestLimit,
                refillPeriod = policy.refillPeriod,
            )
        }

        register(AUTHENTICATED_MUTATION_RATE_LIMIT_NAME) {
            requestKey { call ->
                identityResolver.resolve(call)?.playerId
                    ?: UNRESOLVED_IDENTITY_RATE_LIMIT_KEY
            }
            rateLimiter(
                limit = policy.authenticatedMutationRequestLimit,
                refillPeriod = policy.refillPeriod,
            )
        }

        register(AUTHENTICATED_READ_RATE_LIMIT_NAME) {
            requestKey { call ->
                identityResolver.resolve(call)?.playerId
                    ?: UNRESOLVED_IDENTITY_RATE_LIMIT_KEY
            }
            rateLimiter(
                limit = policy.authenticatedReadRequestLimit,
                refillPeriod = policy.refillPeriod,
            )
        }

        register(CLIENT_TRACE_RATE_LIMIT_NAME) {
            requestKey { call ->
                identityResolver.resolve(call)?.playerId
                    ?: UNRESOLVED_IDENTITY_RATE_LIMIT_KEY
            }
            rateLimiter(
                limit = policy.clientTraceRequestLimit,
                refillPeriod = policy.refillPeriod,
            )
        }
    }
}

class OnlineServerReadiness(
    private val store: OnlineServerStore,
) {
    @Volatile
    private var runtimeState = RuntimeReadinessState.STARTING

    fun markStarted() {
        if (runtimeState == RuntimeReadinessState.STARTING) {
            runtimeState = RuntimeReadinessState.RUNNING
        }
    }

    fun markTickerFailed() {
        if (runtimeState != RuntimeReadinessState.STOPPING) {
            runtimeState = RuntimeReadinessState.TICKER_FAILED
        }
    }

    fun markStopping() {
        runtimeState = RuntimeReadinessState.STOPPING
    }

    fun isReady(): Boolean {
        return runtimeState == RuntimeReadinessState.RUNNING &&
                store.readiness() == OnlineServerStoreReadiness.READY
    }
}

private enum class RuntimeReadinessState {
    STARTING,
    RUNNING,
    TICKER_FAILED,
    STOPPING,
}
