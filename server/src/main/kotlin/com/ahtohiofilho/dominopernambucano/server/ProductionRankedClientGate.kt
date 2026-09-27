package com.ahtohiofilho.dominopernambucano.server

internal const val PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE =
    "DOMINO_PRODUCTION_RANKED_ACCESS_MODE"

internal const val PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE =
    "DOMINO_PRODUCTION_REQUIRED_CLIENT_VERSION_CODE"

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

                    if (
                        suppliedVersion != null &&
                        suppliedVersion == requiredClientVersionCode
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
                )

                "versioned" -> {
                    val rawVersion = readEnvironmentVariable(
                        PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE,
                    )
                        ?.trim()
                        ?.takeIf { value -> value.isNotBlank() }
                        ?: throw IllegalStateException(
                            "$PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE " +
                                "deve ser configurada no modo versioned.",
                        )

                    val versionCode = rawVersion.toIntOrNull()
                    require(versionCode != null && versionCode > 0) {
                        "$PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE " +
                            "deve ser um inteiro positivo."
                    }

                    ProductionRankedClientGate(
                        mode = ProductionRankedAccessMode.VERSIONED,
                        requiredClientVersionCode = versionCode,
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
            )

        fun versionedForTest(
            requiredClientVersionCode: Int,
        ): ProductionRankedClientGate {
            require(requiredClientVersionCode > 0)

            return ProductionRankedClientGate(
                mode = ProductionRankedAccessMode.VERSIONED,
                requiredClientVersionCode = requiredClientVersionCode,
            )
        }
    }
}