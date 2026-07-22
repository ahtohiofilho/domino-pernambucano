package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.BuildConfig

data class GoogleSignInConfig(
    val webClientId: String,
) {
    val isConfigured: Boolean
        get() = webClientId.isNotBlank()

    init {
        require(webClientId == webClientId.trim()) {
            "O Web Client ID Google não pode conter espaços nas extremidades."
        }
    }
}

object GoogleSignInEnvironment {
    val Current = GoogleSignInConfig(
        webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim(),
    )
}
