package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val AUTO_FILL_BOTS_ENVIRONMENT_VARIABLE =
    "DOMINO_AUTO_FILL_BOTS_AFTER_TWO_HUMANS"

/*
 * O intervalo também é a cadência máxima das ações automáticas. Como o store
 * publica no máximo uma transição por partida a cada tick, bot, toque por
 * timeout e jogada automática não chegam ao cliente em rajada.
 */
private const val AUTHORITATIVE_TICK_INTERVAL_MILLIS = 700L

fun main() {
    embeddedServer(
        factory = Netty,
        host = "0.0.0.0",
        port = 8080,
        module = Application::module,
    ).start(
        wait = true,
    )
}

fun Application.module() {
    val serverEnvironment = resolveOnlineServerEnvironment()
    val traceIngestionPolicy = OnlineTraceIngestionPolicy.Default
    val traceArchive = OnlineTraceArchive(
        ingestionPolicy = traceIngestionPolicy,
    )
    val serverTraceSink = BoundedAsyncOnlineTraceSink(
        delegate = traceArchive,
    )
    val traceLogger = OnlineTraceLogger(
        sink = serverTraceSink,
    )
    val store = createDefaultOnlineServerStore(
        serverEnvironment = serverEnvironment,
        autoFillDevelopmentBotsAfterTwoHumanPlayers =
            shouldAutoFillDevelopmentBots(
                serverEnvironment = serverEnvironment,
            ),
        traceLogger = traceLogger,
    )

    try {
        module(
            store = store,
            serverEnvironment = serverEnvironment,
            traceIngestionPolicy = traceIngestionPolicy,
            traceArchive = traceArchive,
            serverTraceSink = serverTraceSink,
        )
    } catch (error: Exception) {
        store.close()
        throw error
    }
}

fun Application.module(
    store: OnlineServerStore,
    serverEnvironment: OnlineServerEnvironment,
    traceIngestionPolicy: OnlineTraceIngestionPolicy =
        OnlineTraceIngestionPolicy.Default,
    traceArchive: OnlineTraceArchive = OnlineTraceArchive(
        ingestionPolicy = traceIngestionPolicy,
    ),
    serverTraceSink: BoundedAsyncOnlineTraceSink? = null,
    sessionTokenService: OnlineSessionTokenService =
        createDefaultOnlineSessionTokenService(
            serverEnvironment = serverEnvironment,
        ),
    identityResolver: OnlineRequestIdentityResolver =
        createDefaultOnlineRequestIdentityResolver(
            sessionTokenService = sessionTokenService,
            serverEnvironment = serverEnvironment,
        ),
    googleIdentityTokenVerifier: OnlineGoogleIdentityTokenVerifier =
        createDefaultOnlineGoogleIdentityTokenVerifier(),
    rateLimitPolicy: OnlineServerRateLimitPolicy =
        OnlineServerRateLimitPolicy.Default,
    nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    readiness: OnlineServerReadiness = OnlineServerReadiness(
        store = store,
    ),
    authoritativeTickIntervalMillis: Long =
        AUTHORITATIVE_TICK_INTERVAL_MILLIS,
) {
    installAuthoritativeMatchTicker(
        store = store,
        serverTraceSink = serverTraceSink,
        readiness = readiness,
        tickIntervalMillis = authoritativeTickIntervalMillis,
    )

    installOnlineServerRateLimits(
        policy = rateLimitPolicy,
        identityResolver = identityResolver,
    )

    install(RequestBodyLimit) {
        bodyLimit {
            traceIngestionPolicy.maxRequestBodyBytes
        }
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }

    routing {
        get("/health") {
            call.respond(
                ServerHealthResponse(
                    status = "ok",
                ),
            )
        }

        get("/ready") {
            val isReady = readiness.isReady()

            call.respond(
                if (isReady) {
                    HttpStatusCode.OK
                } else {
                    HttpStatusCode.ServiceUnavailable
                },
                ServerHealthResponse(
                    status = if (isReady) {
                        "ready"
                    } else {
                        "not_ready"
                    },
                ),
            )
        }

        onlineServerRoutes(
            store = store,
            traceArchive = traceArchive,
            sessionTokenService = sessionTokenService,
            identityResolver = identityResolver,
            googleIdentityTokenVerifier = googleIdentityTokenVerifier,
            nowEpochMillis = nowEpochMillis,
        )
    }
}

private fun Application.installAuthoritativeMatchTicker(
    store: OnlineServerStore,
    serverTraceSink: BoundedAsyncOnlineTraceSink?,
    readiness: OnlineServerReadiness,
    tickIntervalMillis: Long,
) {
    require(tickIntervalMillis > 0L) {
        "O intervalo do ticker autoritativo deve ser positivo."
    }

    val tickerScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default,
    )

    monitor.subscribe(ApplicationStarted) {
        readiness.markStarted()

        tickerScope.launch {
            try {
                while (isActive) {
                    delay(tickIntervalMillis)
                    store.advanceAuthoritativeTime()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                readiness.markTickerFailed()
                log.error(
                    "Authoritative match ticker failed.",
                    error,
                )
            }
        }
    }

    monitor.subscribe(ApplicationStopping) {
        readiness.markStopping()
        tickerScope.cancel()

        try {
            serverTraceSink?.let { sink ->
                runBlocking {
                    sink.shutdown()
                }
            }
        } finally {
            store.close()
        }
    }
}

private fun shouldAutoFillDevelopmentBots(
    serverEnvironment: OnlineServerEnvironment,
): Boolean {
    if (!serverEnvironment.allowsDevelopmentBots) {
        return false
    }

    return System.getenv(
        AUTO_FILL_BOTS_ENVIRONMENT_VARIABLE,
    ).equals(
        "true",
        ignoreCase = true,
    )
}

@Serializable
data class ServerHealthResponse(
    val status: String,
)
