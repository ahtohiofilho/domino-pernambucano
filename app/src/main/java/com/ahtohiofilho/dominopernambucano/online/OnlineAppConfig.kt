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
     * Ponto único de troca do ambiente online.
     *
     * Para continuar no backend fake:
     * Current = OnlineAppConfig.Fake
     *
     * Para testar com backend local no emulador Android:
     * Current = OnlineAppConfig.remote("http://10.0.2.2:8080")
     */
    val Current: OnlineAppConfig =
        OnlineAppConfig.Fake
}