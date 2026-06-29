package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val traceArchive = OnlineTraceArchive()
    val traceLogger = OnlineTraceLogger(
        sink = traceArchive,
    )

    module(
        store = InMemoryOnlineServerStore(
            autoFillDevelopmentBotsAfterTwoHumanPlayers =
                shouldAutoFillDevelopmentBots(),
            traceLogger = traceLogger,
        ),
        traceArchive = traceArchive,
    )
}

fun Application.module(
    store: InMemoryOnlineServerStore,
    traceArchive: OnlineTraceArchive = OnlineTraceArchive(),
    sessionTokenService: OnlineSessionTokenService =
        createDefaultOnlineSessionTokenService(),
    identityResolver: OnlineRequestIdentityResolver =
        createDefaultOnlineRequestIdentityResolver(
            sessionTokenService = sessionTokenService,
        ),
) {
    installAuthoritativeMatchTicker(
        store = store,
    )

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

        onlineServerRoutes(
            store = store,
            traceArchive = traceArchive,
            sessionTokenService = sessionTokenService,
            identityResolver = identityResolver,
        )
    }
}

private fun Application.installAuthoritativeMatchTicker(
    store: InMemoryOnlineServerStore,
) {
    val tickerScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default,
    )

    environment.monitor.subscribe(ApplicationStarted) {
        tickerScope.launch {
            while (isActive) {
                delay(AUTHORITATIVE_TICK_INTERVAL_MILLIS)
                store.advanceAuthoritativeTime()
            }
        }
    }

    environment.monitor.subscribe(ApplicationStopping) {
        tickerScope.cancel()
    }
}

private fun shouldAutoFillDevelopmentBots(): Boolean {
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
