package com.ahtohiofilho.dominopernambucano.server

internal const val ONLINE_SERVER_ENVIRONMENT_VARIABLE =
    "DOMINO_SERVER_ENVIRONMENT"

enum class OnlineServerEnvironment {
    DEVELOPMENT,
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
        "test" -> OnlineServerEnvironment.TEST
        "production" -> OnlineServerEnvironment.PRODUCTION
        else -> throw IllegalArgumentException(
            "$ONLINE_SERVER_ENVIRONMENT_VARIABLE possui valor invalido: " +
                    configuredValue,
        )
    }
}
