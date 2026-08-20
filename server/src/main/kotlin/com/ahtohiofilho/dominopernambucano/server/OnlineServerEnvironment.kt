package com.ahtohiofilho.dominopernambucano.server

internal const val ONLINE_SERVER_ENVIRONMENT_VARIABLE =
    "DOMINO_SERVER_ENVIRONMENT"

internal const val ONLINE_SERVER_PORT_ENVIRONMENT_VARIABLE =
    "DOMINO_SERVER_PORT"

internal const val DEFAULT_ONLINE_SERVER_PORT = 8080
internal const val HOMOLOGATION_ONLINE_SERVER_PORT = 18080
internal const val MINIPRODUCTION_ONLINE_SERVER_PORT = 18080

enum class OnlineServerEnvironment {
    DEVELOPMENT,
    HOMOLOGATION,
    MINIPRODUCTION,
    TEST,
    PRODUCTION,
}

internal val OnlineServerEnvironment.allowsDevelopmentIdentityHeader: Boolean
    get() = this == OnlineServerEnvironment.DEVELOPMENT ||
        this == OnlineServerEnvironment.HOMOLOGATION ||
        this == OnlineServerEnvironment.TEST

internal val OnlineServerEnvironment.allowsDevelopmentBots: Boolean
    get() = this == OnlineServerEnvironment.DEVELOPMENT

internal val OnlineServerEnvironment.allowsPhaseAEmailIdentityRoutes: Boolean
    get() = this == OnlineServerEnvironment.TEST

internal val OnlineServerEnvironment.requiresPersistentState: Boolean
    get() = this == OnlineServerEnvironment.MINIPRODUCTION ||
        this == OnlineServerEnvironment.PRODUCTION

internal val OnlineServerEnvironment.requiresStableSessionSecret: Boolean
    get() = this == OnlineServerEnvironment.MINIPRODUCTION ||
        this == OnlineServerEnvironment.PRODUCTION

internal val OnlineServerEnvironment.usesIsolatedPort: Boolean
    get() = this == OnlineServerEnvironment.HOMOLOGATION ||
        this == OnlineServerEnvironment.MINIPRODUCTION

internal fun resolveOnlineServerEnvironment(
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): OnlineServerEnvironment {
    val configuredValue = readEnvironmentVariable(
        ONLINE_SERVER_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.lowercase()
        ?.takeIf { value ->
            value.isNotBlank()
        }
        ?: throw IllegalStateException(
            "$ONLINE_SERVER_ENVIRONMENT_VARIABLE deve ser configurada explicitamente.",
        )

    return when (configuredValue) {
        "development" -> OnlineServerEnvironment.DEVELOPMENT
        "homologation" -> OnlineServerEnvironment.HOMOLOGATION
        "miniproduction" -> OnlineServerEnvironment.MINIPRODUCTION
        "test" -> OnlineServerEnvironment.TEST
        "production" -> OnlineServerEnvironment.PRODUCTION
        else -> throw IllegalArgumentException(
            "$ONLINE_SERVER_ENVIRONMENT_VARIABLE possui valor invalido: " +
                configuredValue,
        )
    }
}

internal fun resolveOnlineServerPort(
    serverEnvironment: OnlineServerEnvironment,
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): Int {
    val configuredValue = readEnvironmentVariable(
        ONLINE_SERVER_PORT_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.takeIf { value ->
            value.isNotBlank()
        }

    val port = configuredValue
        ?.toIntOrNull()
        ?: when {
            configuredValue != null -> throw IllegalArgumentException(
                "$ONLINE_SERVER_PORT_ENVIRONMENT_VARIABLE possui valor invalido: " +
                    configuredValue,
            )

            serverEnvironment == OnlineServerEnvironment.HOMOLOGATION ->
                HOMOLOGATION_ONLINE_SERVER_PORT

            serverEnvironment == OnlineServerEnvironment.MINIPRODUCTION ->
                MINIPRODUCTION_ONLINE_SERVER_PORT

            else -> DEFAULT_ONLINE_SERVER_PORT
        }

    require(port in 1..65_535) {
        "$ONLINE_SERVER_PORT_ENVIRONMENT_VARIABLE deve estar entre 1 e 65535."
    }

    if (serverEnvironment.usesIsolatedPort) {
        require(port != DEFAULT_ONLINE_SERVER_PORT) {
            "O ambiente isolado nao pode usar a porta padrao de producao " +
                "$DEFAULT_ONLINE_SERVER_PORT."
        }
    }

    return port
}
