package com.ahtohiofilho.dominopernambucano.ui.audio

import android.content.Context

object AndroidSoundEffectsPreferences {
    private const val PreferencesName =
        "domino_pe_application_settings"

    private const val SoundEffectsEnabledKey =
        "sound_effects_enabled"

    fun isEnabled(
        context: Context,
    ): Boolean {
        return context
            .getSharedPreferences(
                PreferencesName,
                Context.MODE_PRIVATE,
            )
            .getBoolean(
                SoundEffectsEnabledKey,
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
                SoundEffectsEnabledKey,
                enabled,
            )
            .apply()
    }
}
