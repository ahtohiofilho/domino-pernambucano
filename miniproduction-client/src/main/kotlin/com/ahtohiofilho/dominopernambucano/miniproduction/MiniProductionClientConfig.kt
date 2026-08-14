package com.ahtohiofilho.dominopernambucano.miniproduction

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

internal const val SYNTHETIC_RUNTIME_TARGET_VARIABLE =
    "DOMINO_SYNTHETIC_RUNTIME_TARGET"
internal const val SYNTHETIC_BASE_URL_VARIABLE =
    "DOMINO_SYNTHETIC_BASE_URL"
internal const val SYNTHETIC_CLIENT_STATE_DIR_VARIABLE =
    "DOMINO_SYNTHETIC_CLIENT_STATE_DIR"
internal const val SYNTHETIC_POPULATION_SIZE_VARIABLE =
    "DOMINO_SYNTHETIC_POPULATION_SIZE"
internal const val SYNTHETIC_POLL_INTERVAL_VARIABLE =
    "DOMINO_SYNTHETIC_POLL_INTERVAL_MILLIS"
internal const val SYNTHETIC_HEARTBEAT_FILE_VARIABLE =
    "DOMINO_SYNTHETIC_HEARTBEAT_FILE"
internal const val SYNTHETIC_MAX_SILENCE_VARIABLE =
    "DOMINO_SYNTHETIC_MAX_SILENCE_MILLIS"
internal const val SYNTHETIC_PROVISIONING_SECRET_VARIABLE =
    "DOMINO_SYNTHETIC_PROVISIONING_SECRET"
internal const val SYNTHETIC_PROVISIONING_SECRET_FILE_VARIABLE =
    "DOMINO_SYNTHETIC_PROVISIONING_SECRET_FILE"

internal const val MINI_PRODUCTION_BASE_URL_VARIABLE =
    "DOMINO_MINIPRODUCTION_BASE_URL"
internal const val MINI_PRODUCTION_CLIENT_STATE_DIR_VARIABLE =
    "DOMINO_MINIPRODUCTION_CLIENT_STATE_DIR"
internal const val MINI_PRODUCTION_POPULATION_SIZE_VARIABLE =
    "DOMINO_MINIPRODUCTION_POPULATION_SIZE"
internal const val MINI_PRODUCTION_POLL_INTERVAL_VARIABLE =
    "DOMINO_MINIPRODUCTION_POLL_INTERVAL_MILLIS"

internal const val DEFAULT_SYNTHETIC_POPULATION_SIZE = 16
private const val DEFAULT_POLL_INTERVAL_MILLIS = 750L
private const val DEFAULT_MAX_SILENCE_MILLIS = 180_000L
private const val PRODUCTION_SERVER_PORT = 8080
private const val MINIMUM_POPULATION_SIZE = 12

internal enum class SyntheticRuntimeTarget {
    LOCAL_MINIPRODUCTION,
    REMOTE_PRODUCTION,
}

internal data class MiniProductionClientConfig(
    val baseUri: URI,
    val stateDirectory: Path,
    val syntheticProvisioningSecret: String,
    val runtimeTarget: SyntheticRuntimeTarget =
        SyntheticRuntimeTarget.LOCAL_MINIPRODUCTION,
    val populationSize: Int = DEFAULT_SYNTHETIC_POPULATION_SIZE,
    val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    val requestTimeout: Duration = Duration.ofSeconds(10L),
    val readinessTimeout: Duration = Duration.ofSeconds(60L),
    val heartbeatFile: Path? = null,
    val maxSilenceMillis: Long = DEFAULT_MAX_SILENCE_MILLIS,
) {
    init {
        require(baseUri.rawUserInfo == null) {
            "A URL dos participantes sintéticos não pode conter credenciais."
        }
        require(baseUri.rawQuery == null && baseUri.rawFragment == null) {
            "A URL dos participantes sintéticos não pode conter query ou fragmento."
        }
        require(baseUri.path.isNullOrBlank() || baseUri.path == "/") {
            "A URL dos participantes sintéticos não pode conter caminho adicional."
        }
        require(!baseUri.host.isNullOrBlank()) {
            "A URL dos participantes sintéticos deve possuir host."
        }

        when (runtimeTarget) {
            SyntheticRuntimeTarget.LOCAL_MINIPRODUCTION -> {
                require(baseUri.scheme.equals("http", ignoreCase = true)) {
                    "A mini-produção local exige HTTP em endereço loopback."
                }
                require(baseUri.host.lowercase() in LOCAL_HOSTS) {
                    "A mini-produção local aceita apenas endereço loopback."
                }
                require(baseUri.port in 1..65_535) {
                    "A porta da mini-produção local deve ser explícita."
                }
                require(baseUri.port != PRODUCTION_SERVER_PORT) {
                    "A mini-produção local não pode usar a porta de produção."
                }
            }

            SyntheticRuntimeTarget.REMOTE_PRODUCTION -> {
                require(baseUri.scheme.equals("https", ignoreCase = true)) {
                    "O runtime remoto exige HTTPS."
                }
                require(baseUri.host.lowercase() !in LOCAL_HOSTS) {
                    "O runtime remoto não aceita endereço loopback."
                }
                require(baseUri.port == -1 || baseUri.port == 443) {
                    "O runtime remoto aceita somente a porta HTTPS padrão."
                }
            }
        }

        require(stateDirectory.isAbsolute) {
            "O diretório de estado dos clientes deve ser absoluto."
        }
        require(heartbeatFile == null || heartbeatFile.isAbsolute) {
            "O arquivo de heartbeat deve possuir caminho absoluto."
        }
        require(
            syntheticProvisioningSecret.toByteArray(Charsets.UTF_8).size >= 32,
        ) {
            "$SYNTHETIC_PROVISIONING_SECRET_VARIABLE deve possuir pelo " +
                "menos 32 bytes."
        }
        require(populationSize in MINIMUM_POPULATION_SIZE..syntheticRoster.size) {
            "A população deve ficar entre $MINIMUM_POPULATION_SIZE e " +
                "${syntheticRoster.size}."
        }
        require(pollIntervalMillis in 250L..5_000L) {
            "O intervalo de polling deve ficar entre 250 e 5000 ms."
        }
        require(maxSilenceMillis in 30_000L..900_000L) {
            "A tolerância de silêncio deve ficar entre 30 e 900 segundos."
        }
        require(!requestTimeout.isZero && !requestTimeout.isNegative)
        require(!readinessTimeout.isZero && !readinessTimeout.isNegative)
    }

    val normalizedBaseUrl: String = baseUri.toString().trimEnd('/')

    companion object {
        fun fromEnvironment(
            readEnvironmentVariable: (String) -> String? = System::getenv,
        ): MiniProductionClientConfig {
            fun value(name: String): String? =
                readEnvironmentVariable(name)?.trim()?.takeIf(String::isNotBlank)

            val runtimeTarget = value(SYNTHETIC_RUNTIME_TARGET_VARIABLE)
                ?.let { rawTarget ->
                    runCatching {
                        SyntheticRuntimeTarget.valueOf(rawTarget.uppercase())
                    }.getOrElse {
                        throw IllegalStateException(
                            "$SYNTHETIC_RUNTIME_TARGET_VARIABLE é inválido.",
                        )
                    }
                }
                ?: SyntheticRuntimeTarget.LOCAL_MINIPRODUCTION

            val remoteMode = runtimeTarget ==
                SyntheticRuntimeTarget.REMOTE_PRODUCTION
            val baseUrl = if (remoteMode) {
                value(SYNTHETIC_BASE_URL_VARIABLE)
                    ?: throw IllegalStateException(
                        "$SYNTHETIC_BASE_URL_VARIABLE deve ser configurada " +
                            "explicitamente no runtime remoto.",
                    )
            } else {
                value(SYNTHETIC_BASE_URL_VARIABLE)
                    ?: value(MINI_PRODUCTION_BASE_URL_VARIABLE)
                    ?: "http://127.0.0.1:18080"
            }
            val stateDirectoryValue = if (remoteMode) {
                value(SYNTHETIC_CLIENT_STATE_DIR_VARIABLE)
                    ?: throw IllegalStateException(
                        "$SYNTHETIC_CLIENT_STATE_DIR_VARIABLE deve ser " +
                            "configurada explicitamente no runtime remoto.",
                    )
            } else {
                value(SYNTHETIC_CLIENT_STATE_DIR_VARIABLE)
                    ?: value(MINI_PRODUCTION_CLIENT_STATE_DIR_VARIABLE)
                    ?: throw IllegalStateException(
                        "$MINI_PRODUCTION_CLIENT_STATE_DIR_VARIABLE deve ser " +
                            "configurada explicitamente.",
                    )
            }
            val populationSizeValue =
                value(SYNTHETIC_POPULATION_SIZE_VARIABLE)
                    ?: value(MINI_PRODUCTION_POPULATION_SIZE_VARIABLE)
            val populationSize = populationSizeValue?.let { rawValue ->
                rawValue.toIntOrNull()
                    ?: throw IllegalStateException(
                        "A população sintética deve ser um inteiro.",
                    )
            } ?: DEFAULT_SYNTHETIC_POPULATION_SIZE
            val pollIntervalValue =
                value(SYNTHETIC_POLL_INTERVAL_VARIABLE)
                    ?: value(MINI_PRODUCTION_POLL_INTERVAL_VARIABLE)
            val pollIntervalMillis = pollIntervalValue?.let { rawValue ->
                rawValue.toLongOrNull()
                    ?: throw IllegalStateException(
                        "O intervalo de polling deve ser um inteiro.",
                    )
            } ?: DEFAULT_POLL_INTERVAL_MILLIS
            val heartbeatFile = value(SYNTHETIC_HEARTBEAT_FILE_VARIABLE)
                ?.let(Paths::get)
            val maxSilenceValue = value(SYNTHETIC_MAX_SILENCE_VARIABLE)
            val maxSilenceMillis = maxSilenceValue?.let { rawValue ->
                rawValue.toLongOrNull()
                    ?: throw IllegalStateException(
                        "A tolerância de silêncio deve ser um inteiro.",
                    )
            } ?: DEFAULT_MAX_SILENCE_MILLIS
            val directSecret = value(SYNTHETIC_PROVISIONING_SECRET_VARIABLE)
            val secretFileValue = value(
                SYNTHETIC_PROVISIONING_SECRET_FILE_VARIABLE,
            )
            check(directSecret == null || secretFileValue == null) {
                "Configure somente uma origem para o segredo sintético."
            }
            val syntheticProvisioningSecret = directSecret
                ?: secretFileValue?.let { pathValue ->
                    val path = Paths.get(pathValue)
                    check(path.isAbsolute && Files.isRegularFile(path)) {
                        "$SYNTHETIC_PROVISIONING_SECRET_FILE_VARIABLE deve " +
                            "apontar para um arquivo absoluto existente."
                    }
                    Files.readString(path, StandardCharsets.UTF_8)
                        .trim()
                        .takeIf(String::isNotBlank)
                }
                ?: throw IllegalStateException(
                    "$SYNTHETIC_PROVISIONING_SECRET_VARIABLE ou " +
                        "$SYNTHETIC_PROVISIONING_SECRET_FILE_VARIABLE deve " +
                        "ser configurada explicitamente.",
                )

            return MiniProductionClientConfig(
                baseUri = URI.create(baseUrl),
                stateDirectory = Paths.get(stateDirectoryValue),
                syntheticProvisioningSecret = syntheticProvisioningSecret,
                runtimeTarget = runtimeTarget,
                populationSize = populationSize,
                pollIntervalMillis = pollIntervalMillis,
                heartbeatFile = heartbeatFile,
                maxSilenceMillis = maxSilenceMillis,
            )
        }
    }
}

private val LOCAL_HOSTS = setOf(
    "127.0.0.1",
    "localhost",
    "::1",
    "[::1]",
    "0:0:0:0:0:0:0:1",
)
