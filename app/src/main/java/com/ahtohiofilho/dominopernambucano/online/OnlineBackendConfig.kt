package com.ahtohiofilho.dominopernambucano.online

data class OnlineBackendConfig(
    val mode: OnlineBackendMode,
    val baseUrl: String? = null,
    val connectTimeoutMillis: Long = 10_000L,
    val requestTimeoutMillis: Long = 15_000L,
    val clientVersionCode: Int? = null,
) {
    init {
        require(
            clientVersionCode == null || clientVersionCode > 0
        ) {
            "clientVersionCode deve ser positivo quando informado."
        }
    }

    companion object {
        val Fake = OnlineBackendConfig(
            mode = OnlineBackendMode.FAKE,
        )

        fun remote(
            baseUrl: String,
            clientVersionCode: Int? = null,
        ): OnlineBackendConfig {
            return OnlineBackendConfig(
                mode = OnlineBackendMode.REMOTE,
                baseUrl = baseUrl.trim().ifBlank {
                    null
                },
                clientVersionCode = clientVersionCode,
            )
        }
    }
}