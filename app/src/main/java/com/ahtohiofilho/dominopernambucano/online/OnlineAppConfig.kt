package com.ahtohiofilho.dominopernambucano.online

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
    }
}

object OnlineAppEnvironment {
    /*
     * Backend fake:
     * Current = OnlineAppConfig.Fake
     *
     * Backend local no emulador Android:
     * Current = OnlineAppConfig.remote("http://10.0.2.2:8080")
     *
     * Observação:
     * 10.0.2.2 é o alias usado pelo emulador Android para acessar
     * o localhost da máquina host.
     */
    val Current: OnlineAppConfig =
        OnlineAppConfig.remote("http://10.0.2.2:8080")
}