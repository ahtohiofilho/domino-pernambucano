package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.BuildConfig

data class PlayGamesSignInConfig(
    val projectId: String,
    val webClientId: String,
) {
    val isConfigured: Boolean
        get() = projectId.isNotBlank() && webClientId.isNotBlank()

    init {
        require(projectId == projectId.trim()) {
            "O Project ID do Play Games não pode conter espaços nas extremidades."
        }
        require(webClientId == webClientId.trim()) {
            "O Web Client ID do Play Games não pode conter espaços nas extremidades."
        }
        require(projectId.isBlank() || projectId.all(Char::isDigit)) {
            "O Project ID do Play Games deve conter apenas dígitos."
        }
    }
}

object PlayGamesSignInEnvironment {
    val Current = PlayGamesSignInConfig(
        projectId = BuildConfig.PLAY_GAMES_PROJECT_ID.trim(),
        webClientId = BuildConfig.PLAY_GAMES_WEB_CLIENT_ID.trim(),
    )
}
