package com.ahtohiofilho.dominopernambucano.ui.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AndroidAppLanguageManager {
    private const val PreferencesName =
        "domino_pe_application_settings"
    private const val LanguagePreferenceKey =
        "language_preference"

    fun currentSelection(
        context: Context,
    ): AppLanguageSelection {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(
                LocaleManager::class.java,
            )
            val applicationLocales =
                localeManager.applicationLocales

            if (!applicationLocales.isEmpty) {
                val selection =
                    AppLanguageSelection.fromLanguageTag(
                        applicationLocales[0].toLanguageTag(),
                    )

                persistSelection(
                    context = context,
                    selection = selection,
                )

                return selection
            }
        }

        return storedOrInitialSelection(context)
    }

    fun applySelection(
        context: Context,
        selection: AppLanguageSelection,
    ) {
        persistSelection(
            context = context,
            selection = selection,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ensureApplicationLocale(
                context = context,
                selection = selection,
            )
            return
        }

        context.findActivity()?.recreate()
    }

    fun localizedContext(
        baseContext: Context,
    ): Context {
        val selection = currentSelection(
            context = baseContext,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ensureApplicationLocale(
                context = baseContext,
                selection = selection,
            )
            return baseContext
        }

        val locale = Locale.forLanguageTag(
            selection.languageTag,
        )
        val configuration = Configuration(
            baseContext.resources.configuration,
        )

        configuration.setLocales(
            LocaleList(locale),
        )

        return baseContext.createConfigurationContext(
            configuration,
        )
    }

    private fun storedOrInitialSelection(
        context: Context,
    ): AppLanguageSelection {
        val preferences = context.applicationContext
            .getSharedPreferences(
                PreferencesName,
                Context.MODE_PRIVATE,
            )

        val persistedValue = preferences.getString(
            LanguagePreferenceKey,
            null,
        )

        AppLanguageSelection.fromPersistedValue(
            value = persistedValue,
        )?.let { storedSelection ->
            return storedSelection
        }

        val initialSelection =
            resolveInitialLanguageSelection(
                systemLanguage = systemLanguage(),
            )

        persistSelection(
            context = context,
            selection = initialSelection,
        )

        return initialSelection
    }

    private fun persistSelection(
        context: Context,
        selection: AppLanguageSelection,
    ) {
        context.applicationContext
            .getSharedPreferences(
                PreferencesName,
                Context.MODE_PRIVATE,
            )
            .edit()
            .putString(
                LanguagePreferenceKey,
                selection.persistedValue,
            )
            .apply()
    }

    private fun ensureApplicationLocale(
        context: Context,
        selection: AppLanguageSelection,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val localeManager = context.getSystemService(
            LocaleManager::class.java,
        )
        val requestedLocales = LocaleList.forLanguageTags(
            selection.languageTag,
        )

        if (
            localeManager.applicationLocales.toLanguageTags() !=
            requestedLocales.toLanguageTags()
        ) {
            localeManager.applicationLocales = requestedLocales
        }
    }

    private fun systemLanguage(): String? {
        val systemLocales =
            Resources.getSystem().configuration.locales

        return if (systemLocales.isEmpty) {
            null
        } else {
            systemLocales[0].language
        }
    }

    private tailrec fun Context.findActivity(): Activity? {
        return when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }
}
