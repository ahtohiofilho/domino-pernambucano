package com.ahtohiofilho.dominopernambucano.server

internal const val PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE =
    "DOMINO_PRODUCTION_RANKED_ACCESS_MODE"

internal const val PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE =
    "DOMINO_PRODUCTION_REQUIRED_CLIENT_VERSION_CODE"

internal const val PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE =
    "DOMINO_PRODUCTION_ALLOWED_CLIENT_VERSION_CODES"

internal enum class ProductionRankedAccessMode {
    UNRESTRICTED,
    CLOSED,
    VERSIONED,
}

internal enum class ProductionRankedClientDecision {
    ALLOWED,
    MAINTENANCE,
    UPDATE_REQUIRED,
}

internal class ProductionRankedClientGate private constructor(
    private val mode: ProductionRankedAccessMode,
    private val requiredClientVersionCode: Int?,
    private val allowedClientVersionCodes: Set<Int>?,
) {
    fun evaluate(
        suppliedClientVersionCode: String?,
        syntheticAccount: Boolean,
    ): ProductionRankedClientDecision {
        return when (mode) {
            ProductionRankedAccessMode.UNRESTRICTED ->
                ProductionRankedClientDecision.ALLOWED

            ProductionRankedAccessMode.CLOSED ->
                ProductionRankedClientDecision.MAINTENANCE

            ProductionRankedAccessMode.VERSIONED -> {
                if (syntheticAccount) {
                    ProductionRankedClientDecision.ALLOWED
                } else {
                    val suppliedVersion =
                        suppliedClientVersionCode
                            ?.trim()
                            ?.toIntOrNull()

                    val explicitlyAllowedVersions =
                        allowedClientVersionCodes
                            ?: requiredClientVersionCode
                                ?.let(::setOf)
                            ?: emptySet()

                    if (
                        suppliedVersion != null &&
                        suppliedVersion in explicitlyAllowedVersions
                    ) {
                        ProductionRankedClientDecision.ALLOWED
                    } else {
                        ProductionRankedClientDecision.UPDATE_REQUIRED
                    }
                }
            }
        }
    }

    companion object {
        val Unrestricted = ProductionRankedClientGate(
            mode = ProductionRankedAccessMode.UNRESTRICTED,
            requiredClientVersionCode = null,
            allowedClientVersionCodes = null,
        )

        fun fromEnvironment(
            serverEnvironment: OnlineServerEnvironment,
            readEnvironmentVariable: (String) -> String? =
                System::getenv,
        ): ProductionRankedClientGate {
            if (serverEnvironment != OnlineServerEnvironment.PRODUCTION) {
                return Unrestricted
            }

            val configuredMode = readEnvironmentVariable(
                PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE,
            )
                ?.trim()
                ?.lowercase()
                ?.takeIf { value -> value.isNotBlank() }
                ?: "closed"

            return when (configuredMode) {
                "closed" -> ProductionRankedClientGate(
                    mode = ProductionRankedAccessMode.CLOSED,
                    requiredClientVersionCode = null,
                    allowedClientVersionCodes = null,
                )

                "versioned" -> {
                    val rawAllowedVersions = readEnvironmentVariable(
                        PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE,
                    )
                        ?.trim()
                        ?.takeIf { value -> value.isNotBlank() }

                    val allowedVersions = rawAllowedVersions?.let { raw ->
                        raw.split(",")
                            .map { token -> token.trim() }
                            .also { tokens ->
                                require(
                                    tokens.isNotEmpty() &&
                                        tokens.none(String::isBlank)
                                ) {
                                    "$PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE " +
                                        "deve conter inteiros positivos separados por virgula."
                                }
                            }
                            .map { token ->
                                token.toIntOrNull()
                                    ?.takeIf { value -> value > 0 }
                                    ?: throw IllegalArgumentException(
                                        "$PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE " +
                                            "deve conter apenas inteiros positivos.",
                                    )
                            }
                            .toSet()
                            .also { values ->
                                require(values.isNotEmpty()) {
                                    "$PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE " +
                                        "nao pode ser vazia."
                                }
                            }
                    }

                    val rawVersion = if (allowedVersions == null) {
                        readEnvironmentVariable(
                            PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE,
                        )
                            ?.trim()
                            ?.takeIf { value -> value.isNotBlank() }
                            ?: throw IllegalStateException(
                                "$PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE " +
                                    "deve ser configurada no modo versioned quando " +
                                    "$PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE " +
                                    "nao estiver definida.",
                            )
                    } else {
                        null
                    }

                    val versionCode = rawVersion?.let { value ->
                        value.toIntOrNull()
                            ?.takeIf { parsed -> parsed > 0 }
                            ?: throw IllegalArgumentException(
                                "$PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE " +
                                    "deve ser um inteiro positivo.",
                            )
                    }

                    ProductionRankedClientGate(
                        mode = ProductionRankedAccessMode.VERSIONED,
                        requiredClientVersionCode = versionCode,
                        allowedClientVersionCodes = allowedVersions,
                    )
                }

                else -> throw IllegalArgumentException(
                    "$PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE " +
                        "possui valor invalido: $configuredMode",
                )
            }
        }

        fun closedForTest(): ProductionRankedClientGate =
            ProductionRankedClientGate(
                mode = ProductionRankedAccessMode.CLOSED,
                requiredClientVersionCode = null,
                allowedClientVersionCodes = null,
            )

        fun versionedForTest(
            requiredClientVersionCode: Int,
        ): ProductionRankedClientGate {
            require(requiredClientVersionCode > 0)

            return ProductionRankedClientGate(
                mode = ProductionRankedAccessMode.VERSIONED,
                requiredClientVersionCode = requiredClientVersionCode,
                allowedClientVersionCodes = null,
            )
        }
    }
}