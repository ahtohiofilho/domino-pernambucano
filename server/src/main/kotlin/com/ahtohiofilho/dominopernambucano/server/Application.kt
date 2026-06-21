package com.ahtohiofilho.dominopernambucano.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val AUTO_FILL_BOTS_ENVIRONMENT_VARIABLE =
    "DOMINO_AUTO_FILL_BOTS_AFTER_TWO_HUMANS"

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
    module(
        store = InMemoryOnlineServerStore(
            autoFillDevelopmentBotsAfterTwoHumanPlayers =
                shouldAutoFillDevelopmentBots(),
        ),
    )
}

fun Application.module(
    store: InMemoryOnlineServerStore,
) {
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
        )
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