package com.ahtohiofilho.dominopernambucano.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguagePolicyTest {
    @Test
    fun settings_expose_exactly_three_explicit_languages() {
        assertEquals(
            listOf(
                AppLanguageSelection.PortugueseBrazil,
                AppLanguageSelection.Spanish,
                AppLanguageSelection.English,
            ),
            AppLanguageSelection.entries,
        )
    }

    @Test
    fun portuguese_system_locale_defines_brazilian_portuguese_initially() {
        assertEquals(
            AppLanguageSelection.PortugueseBrazil,
            resolveInitialLanguageSelection("pt"),
        )
        assertEquals(
            AppLanguageSelection.PortugueseBrazil,
            resolveInitialLanguageSelection("pt-PT"),
        )
    }

    @Test
    fun spanish_system_locale_defines_spanish_initially() {
        assertEquals(
            AppLanguageSelection.Spanish,
            resolveInitialLanguageSelection("es"),
        )
        assertEquals(
            AppLanguageSelection.Spanish,
            resolveInitialLanguageSelection("es-MX"),
        )
    }

    @Test
    fun english_system_locale_defines_english_initially() {
        assertEquals(
            AppLanguageSelection.English,
            resolveInitialLanguageSelection("en"),
        )
    }

    @Test
    fun unsupported_or_missing_system_locale_defines_english_initially() {
        assertEquals(
            AppLanguageSelection.English,
            resolveInitialLanguageSelection("fr"),
        )
        assertEquals(
            AppLanguageSelection.English,
            resolveInitialLanguageSelection("de-DE"),
        )
        assertEquals(
            AppLanguageSelection.English,
            resolveInitialLanguageSelection(null),
        )
    }

    @Test
    fun legacy_automatic_value_is_migrated_instead_of_exposed() {
        assertNull(
            AppLanguageSelection.fromPersistedValue("automatic"),
        )
    }
}
