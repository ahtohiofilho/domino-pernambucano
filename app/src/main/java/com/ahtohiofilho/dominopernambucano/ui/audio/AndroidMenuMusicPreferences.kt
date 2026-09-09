package com.ahtohiofilho.dominopernambucano.ui.audio

import android.content.Context

object AndroidMenuMusicPreferences {
    private const val PreferencesName =
        "domino_pe_application_settings"

    private const val MenuMusicEnabledKey =
        "menu_music_enabled"

    fun isEnabled(
        context: Context,
    ): Boolean {
        return context
            .getSharedPreferences(
                PreferencesName,
                Context.MODE_PRIVATE,
            )
            .getBoolean(
                MenuMusicEnabledKey,
                true,
            )
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context
            .getSharedPreferences(
                PreferencesName,
                Context.MODE_PRIVATE,
            )
            .edit()
            .putBoolean(
                MenuMusicEnabledKey,
                enabled,
            )
            .apply()
    }
}
