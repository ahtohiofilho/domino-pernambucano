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
import java.nio.file.Path

private const val AUTO_FILL_BOTS_ENVIRONMENT_VARIABLE =
    "DOMINO_AUTO_FILL_BOTS_AFTER_TWO_HUMANS"

private const val ONLINE_SERVER_STATE_DIRECTORY_ENVIRONMENT_VARIABLE =
    "DOMINO_ONLINE_SERVER_STATE_DIRECTORY"

private const val DEFAULT_ONLINE_SERVER_STATE_DIRECTORY =
    "build/online-server-state"

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

    val store = InMemoryOnlineServerStore(
        autoFillDevelopmentBotsAfterTwoHumanPlayers =
            shouldAutoFillDevelopmentBots(),
        traceLogger = traceLogger,
    )

    module(
        store = store,
        traceArchive = traceArchive,
        persistenceController = OnlineServerPersistenceController(
            store = store,
            stateStore = createDefaultOnlineServerStateStore(),
        ),
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
    persistenceController: OnlineServerPersistenceController? = null,
) {
    persistenceController?.restoreAtStartup()

    installAuthoritativeMatchTicker(
        store = store,
        persistenceController = persistenceController,
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
            when (
                val recoveryStatus = persistenceController
                    ?.currentRecoveryStatus()
                    ?: OnlineServerRecoveryStatus.Ready
            ) {
                OnlineServerRecoveryStatus.Ready -> {
                    call.respond(
                        ServerHealthResponse(
                            status = "ok",
                            recovery = "ready",
                        ),
                    )
                }

                is OnlineServerRecoveryStatus.Invalid -> {
                    call.respond(
                        io.ktor.http.HttpStatusCode.ServiceUnavailable,
                        ServerHealthResponse(
                            status = "degraded",
                            recovery = "invalid",
                            reason = recoveryStatus.reason,
                        ),
                    )
                }
            }
        }

        onlineServerRoutes(
            store = store,
            traceArchive = traceArchive,
            sessionTokenService = sessionTokenService,
            identityResolver = identityResolver,
            persistenceController = persistenceController,
        )
    }
}

private fun Application.installAuthoritativeMatchTicker(
    store: InMemoryOnlineServerStore,
    persistenceController: OnlineServerPersistenceController?,
) {
    val tickerScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default,
    )

    environment.monitor.subscribe(ApplicationStarted) {
        tickerScope.launch {
            while (isActive) {
                delay(AUTHORITATIVE_TICK_INTERVAL_MILLIS)

                if (persistenceController != null) {
                    persistenceController.clearExpiredRooms()
                    persistenceController.advanceAuthoritativeTime()
                } else {
                    store.clearExpiredRooms()
                    store.advanceAuthoritativeTime()
                }
            }
        }
    }

    environment.monitor.subscribe(ApplicationStopping) {
        tickerScope.cancel()
    }
}

private fun createDefaultOnlineServerStateStore(): OnlineServerStateStore {
    val configuredDirectory = System.getenv(
        ONLINE_SERVER_STATE_DIRECTORY_ENVIRONMENT_VARIABLE,
    )?.trim()

    val stateDirectory = if (configuredDirectory.isNullOrBlank()) {
        Path.of(
            DEFAULT_ONLINE_SERVER_STATE_DIRECTORY,
        )
    } else {
        Path.of(configuredDirectory)
    }

    return JsonFileOnlineServerStateStore(stateDirectory)
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
    val recovery: String = "ready",
    val reason: String? = null,
)
