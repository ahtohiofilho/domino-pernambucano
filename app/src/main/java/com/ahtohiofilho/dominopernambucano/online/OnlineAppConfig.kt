package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.BuildConfig

private const val ONLINE_BACKEND_MODE_FAKE = "fake"
private const val ONLINE_BACKEND_MODE_REMOTE = "remote"

private const val DEFAULT_LOCAL_REMOTE_BACKEND_BASE_URL =
    "http://10.0.2.2:8080"

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
        ): OnlineAppConfig {
            val normalizedMode = backendMode
                .trim()
                .lowercase()

            return when (normalizedMode) {
                ONLINE_BACKEND_MODE_REMOTE -> {
                    val resolvedBaseUrl = backendBaseUrl
                        .trim()
                        .ifBlank {
                            DEFAULT_LOCAL_REMOTE_BACKEND_BASE_URL
                        }

                    remote(
                        baseUrl = resolvedBaseUrl,
                    )
                }

                ONLINE_BACKEND_MODE_FAKE -> Fake

                else -> Fake
            }
        }
    }
}

object OnlineAppEnvironment {
    /*
     * Ambiente online padrão:
     * - fake por default
     * - remoto local apenas quando ativado via Gradle
     *
     * Build padrão:
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
        )
}