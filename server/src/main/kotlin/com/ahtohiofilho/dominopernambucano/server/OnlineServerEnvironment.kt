package com.ahtohiofilho.dominopernambucano.server

internal const val ONLINE_SERVER_ENVIRONMENT_VARIABLE =
    "DOMINO_SERVER_ENVIRONMENT"

internal const val ONLINE_SERVER_PORT_ENVIRONMENT_VARIABLE =
    "DOMINO_SERVER_PORT"

internal const val DEFAULT_ONLINE_SERVER_PORT = 8080
internal const val HOMOLOGATION_ONLINE_SERVER_PORT = 18080

enum class OnlineServerEnvironment {
    DEVELOPMENT,
    HOMOLOGATION,
    TEST,
    PRODUCTION,
}

internal val OnlineServerEnvironment.allowsDevelopmentIdentityHeader: Boolean
    get() = this != OnlineServerEnvironment.PRODUCTION

internal val OnlineServerEnvironment.allowsDevelopmentBots: Boolean
    get() = this == OnlineServerEnvironment.DEVELOPMENT

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

            else -> DEFAULT_ONLINE_SERVER_PORT
        }

    require(port in 1..65_535) {
        "$ONLINE_SERVER_PORT_ENVIRONMENT_VARIABLE deve estar entre 1 e 65535."
    }

    if (serverEnvironment == OnlineServerEnvironment.HOMOLOGATION) {
        require(port != DEFAULT_ONLINE_SERVER_PORT) {
            "A homologacao nao pode usar a porta padrao de producao " +
                "$DEFAULT_ONLINE_SERVER_PORT."
        }
    }

    return port
}
