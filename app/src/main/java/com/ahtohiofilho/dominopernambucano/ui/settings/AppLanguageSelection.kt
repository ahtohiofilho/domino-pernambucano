package com.ahtohiofilho.dominopernambucano.ui.settings

enum class AppLanguageSelection(
    val persistedValue: String,
    val languageTag: String,
) {
    PortugueseBrazil(
        persistedValue = "pt-BR",
        languageTag = "pt-BR",
    ),
    Spanish(
        persistedValue = "es",
        languageTag = "es",
    ),
    English(
        persistedValue = "en",
        languageTag = "en",
    ),
    ;

    companion object {
        fun fromPersistedValue(
            value: String?,
        ): AppLanguageSelection? {
            return entries.firstOrNull { selection ->
                selection.persistedValue == value
            }
        }

        fun fromLanguageTag(
            languageTag: String?,
        ): AppLanguageSelection {
            return resolveInitialLanguageSelection(
                systemLanguage = languageTag,
            )
        }
    }
}

internal fun resolveInitialLanguageSelection(
    systemLanguage: String?,
): AppLanguageSelection {
    val language = systemLanguage
        ?.substringBefore('-')
        ?.substringBefore('_')
        ?.lowercase()

    return when (language) {
        "pt" -> AppLanguageSelection.PortugueseBrazil
        "es" -> AppLanguageSelection.Spanish
        else -> AppLanguageSelection.English
    }
}
