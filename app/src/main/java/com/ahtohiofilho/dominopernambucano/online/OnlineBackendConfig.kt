package com.ahtohiofilho.dominopernambucano.online

data class OnlineBackendConfig(
    val mode: OnlineBackendMode,
    val baseUrl: String? = null,
    val connectTimeoutMillis: Long = 10_000L,
    val requestTimeoutMillis: Long = 15_000L,
) {
    companion object {
        val Fake = OnlineBackendConfig(
            mode = OnlineBackendMode.FAKE,
        )

        fun remote(
            baseUrl: String,
        ): OnlineBackendConfig {
            return OnlineBackendConfig(
                mode = OnlineBackendMode.REMOTE,
                baseUrl = baseUrl.trim().ifBlank {
                    null
                },
            )
        }
    }
}