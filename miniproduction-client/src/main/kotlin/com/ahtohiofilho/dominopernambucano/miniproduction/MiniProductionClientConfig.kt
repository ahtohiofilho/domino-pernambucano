package com.ahtohiofilho.dominopernambucano.miniproduction

import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

internal const val MINI_PRODUCTION_BASE_URL_VARIABLE =
    "DOMINO_MINIPRODUCTION_BASE_URL"
internal const val MINI_PRODUCTION_CLIENT_STATE_DIR_VARIABLE =
    "DOMINO_MINIPRODUCTION_CLIENT_STATE_DIR"
internal const val MINI_PRODUCTION_POPULATION_SIZE_VARIABLE =
    "DOMINO_MINIPRODUCTION_POPULATION_SIZE"
internal const val MINI_PRODUCTION_POLL_INTERVAL_VARIABLE =
    "DOMINO_MINIPRODUCTION_POLL_INTERVAL_MILLIS"

internal const val DEFAULT_SYNTHETIC_POPULATION_SIZE = 15
private const val DEFAULT_POLL_INTERVAL_MILLIS = 750L
private const val PRODUCTION_SERVER_PORT = 8080
private const val MINIMUM_POPULATION_SIZE = 12

internal data class MiniProductionClientConfig(
    val baseUri: URI,
    val stateDirectory: Path,
    val populationSize: Int = DEFAULT_SYNTHETIC_POPULATION_SIZE,
    val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    val requestTimeout: Duration = Duration.ofSeconds(10L),
    val readinessTimeout: Duration = Duration.ofSeconds(60L),
) {
    init {
        require(baseUri.scheme == "http") {
            "A mini-produção local exige HTTP em endereço loopback."
        }
        require(baseUri.rawQuery == null && baseUri.rawFragment == null) {
            "A URL da mini-produção não pode conter query ou fragmento."
        }
        require(baseUri.path.isNullOrBlank() || baseUri.path == "/") {
            "A URL da mini-produção não pode conter caminho adicional."
        }
        require(baseUri.host in LOCAL_HOSTS) {
            "O cliente sintético aceita apenas endereço loopback."
        }
        require(baseUri.port in 1..65_535) {
            "A porta da mini-produção deve ser explícita."
        }
        require(baseUri.port != PRODUCTION_SERVER_PORT) {
            "O cliente sintético não pode usar a porta de produção."
        }
        require(stateDirectory.isAbsolute) {
            "O diretório de estado dos clientes deve ser absoluto."
        }
        require(populationSize in MINIMUM_POPULATION_SIZE..syntheticRoster.size) {
            "A população deve ficar entre $MINIMUM_POPULATION_SIZE e " +
                "${syntheticRoster.size}."
        }
        require(pollIntervalMillis in 250L..5_000L) {
            "O intervalo de polling deve ficar entre 250 e 5000 ms."
        }
        require(!requestTimeout.isZero && !requestTimeout.isNegative)
        require(!readinessTimeout.isZero && !readinessTimeout.isNegative)
    }

    val normalizedBaseUrl: String = baseUri.toString().trimEnd('/')

    companion object {
        fun fromEnvironment(
            readEnvironmentVariable: (String) -> String? = System::getenv,
        ): MiniProductionClientConfig {
            val baseUrl = readEnvironmentVariable(
                MINI_PRODUCTION_BASE_URL_VARIABLE,
            )
                ?.trim()
                ?.takeIf { value -> value.isNotBlank() }
                ?: "http://127.0.0.1:18080"
            val stateDirectoryValue = readEnvironmentVariable(
                MINI_PRODUCTION_CLIENT_STATE_DIR_VARIABLE,
            )
                ?.trim()
                ?.takeIf { value -> value.isNotBlank() }
                ?: throw IllegalStateException(
                    "$MINI_PRODUCTION_CLIENT_STATE_DIR_VARIABLE deve ser " +
                        "configurada explicitamente.",
                )
            val populationSize = readEnvironmentVariable(
                MINI_PRODUCTION_POPULATION_SIZE_VARIABLE,
            )
                ?.trim()
                ?.takeIf { value -> value.isNotBlank() }
                ?.toIntOrNull()
                ?: DEFAULT_SYNTHETIC_POPULATION_SIZE
            val pollIntervalMillis = readEnvironmentVariable(
                MINI_PRODUCTION_POLL_INTERVAL_VARIABLE,
            )
                ?.trim()
                ?.takeIf { value -> value.isNotBlank() }
                ?.toLongOrNull()
                ?: DEFAULT_POLL_INTERVAL_MILLIS

            return MiniProductionClientConfig(
                baseUri = URI.create(baseUrl),
                stateDirectory = Paths.get(stateDirectoryValue),
                populationSize = populationSize,
                pollIntervalMillis = pollIntervalMillis,
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
