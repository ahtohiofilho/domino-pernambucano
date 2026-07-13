package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.BuildConfig
import java.net.URI

private const val ONLINE_BACKEND_MODE_FAKE = "fake"
private const val ONLINE_BACKEND_MODE_REMOTE = "remote"

private const val DEFAULT_LOCAL_REMOTE_BACKEND_BASE_URL =
    "http://10.0.2.2:8080"

private val DEVELOPMENT_ONLY_REMOTE_BACKEND_HOSTS = setOf(
    "localhost",
    "127.0.0.1",
    "0.0.0.0",
    "10.0.2.2",
)

data class OnlineAppConfig(
    val backendConfig: OnlineBackendConfig,
    val debugOptions: OnlineDebugOptions,
) {
    companion object {
        val Fake = OnlineAppConfig(
            backendConfig = OnlineBackendConfig.Fake,
            debugOptions = OnlineDebugOptions.FakeBackend,
        )

        fun remote(
            baseUrl: String,
        ): OnlineAppConfig {
            return OnlineAppConfig(
                backendConfig = OnlineBackendConfig.remote(
                    baseUrl = baseUrl,
                ),
                debugOptions = OnlineDebugOptions.RealBackend,
            )
        }

        fun fromBuildConfig(
            backendMode: String,
            backendBaseUrl: String,
            allowDevelopmentBackends: Boolean,
        ): OnlineAppConfig {
            val normalizedMode = backendMode
                .trim()
                .lowercase()

            return when (normalizedMode) {
                ONLINE_BACKEND_MODE_REMOTE -> {
                    remote(
                        baseUrl = resolveRemoteBackendBaseUrl(
                            backendBaseUrl = backendBaseUrl,
                            allowDevelopmentBackends =
                                allowDevelopmentBackends,
                        ),
                    )
                }

                ONLINE_BACKEND_MODE_FAKE -> {
                    check(allowDevelopmentBackends) {
                        "Backend online fake não é permitido em produção."
                    }

                    Fake
                }

                else -> throw IllegalArgumentException(
                    "Modo de backend online inválido: '$backendMode'.",
                )
            }
        }

        private fun resolveRemoteBackendBaseUrl(
            backendBaseUrl: String,
            allowDevelopmentBackends: Boolean,
        ): String {
            val normalizedBaseUrl = backendBaseUrl.trim()

            if (allowDevelopmentBackends) {
                return normalizedBaseUrl.ifBlank {
                    DEFAULT_LOCAL_REMOTE_BACKEND_BASE_URL
                }
            }

            check(normalizedBaseUrl.isNotBlank()) {
                "A URL do backend online é obrigatória em produção."
            }

            val parsedBaseUrl = try {
                URI(normalizedBaseUrl)
            } catch (cause: Exception) {
                throw IllegalArgumentException(
                    "A URL do backend online é inválida.",
                    cause,
                )
            }

            require(parsedBaseUrl.scheme.equals("https", ignoreCase = true)) {
                "O backend online de produção deve usar HTTPS."
            }

            val normalizedHost = parsedBaseUrl.host
                ?.trim()
                ?.lowercase()

            require(!normalizedHost.isNullOrBlank()) {
                "A URL do backend online de produção deve informar um host."
            }

            require(normalizedHost !in DEVELOPMENT_ONLY_REMOTE_BACKEND_HOSTS) {
                "Host de desenvolvimento não é permitido em produção."
            }

            return normalizedBaseUrl
        }
    }
}

object OnlineAppEnvironment {
    /*
     * Ambiente online padrão:
     * - builds de desenvolvimento permitem fake e backend local
     * - builds de produção exigem backend remoto HTTPS explícito
     *
     * Build de desenvolvimento padrão:
     * ./gradlew assembleDebug
     *
     * Build apontando para backend local no emulador Android:
     * ./gradlew assembleDebug \
     *   -PonlineBackendMode=remote \
     *   -PonlineBackendBaseUrl=http://10.0.2.2:8080
     *
     * Observação:
     * 10.0.2.2 é o alias usado pelo emulador Android para acessar
     * o localhost da máquina host.
     */
    val Current: OnlineAppConfig =
        OnlineAppConfig.fromBuildConfig(
            backendMode = BuildConfig.ONLINE_BACKEND_MODE,
            backendBaseUrl = BuildConfig.ONLINE_BACKEND_BASE_URL,
            allowDevelopmentBackends = BuildConfig.DEBUG,
        )
}