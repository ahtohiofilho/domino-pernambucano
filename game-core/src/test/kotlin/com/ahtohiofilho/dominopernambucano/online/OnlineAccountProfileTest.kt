package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAccountProfileTest {
    @Test
    fun public_name_and_table_code_are_normalized_separately() {
        val profile = createOnlineAccountProfile(
            publicDisplayName = "  Antônio   Filho  ",
            tableName = "a1f",
            updatedAtEpochMillis = 1_000L,
        )

        assertEquals(
            "Antônio Filho",
            profile.publicDisplayName,
        )
        assertEquals(
            "A1F",
            profile.tableName,
        )
    }

    @Test
    fun blank_table_code_is_rejected_for_account_profile() {
        assertThrows(IllegalArgumentException::class.java) {
            createOnlineAccountProfile(
                publicDisplayName = "Antônio Filho",
                tableName = " ",
                updatedAtEpochMillis = 1_000L,
            )
        }
    }

    @Test
    fun table_code_requires_exactly_three_ascii_alphanumeric_characters() {
        assertFalse(isValidOnlineAccountTableCode("AF"))
        assertTrue(isValidOnlineAccountTableCode("af1"))
        assertFalse(isValidOnlineAccountTableCode("AFI2"))
        assertFalse(isValidOnlineAccountTableCode("AÇI"))
        assertFalse(isValidOnlineAccountTableCode("A-I"))
    }

    @Test
    fun account_short_name_suggestion_is_three_ascii_characters() {
        assertEquals(
            "JOG",
            suggestOnlineAccountTableCode("Jogador"),
        )
        assertEquals(
            "ANT",
            suggestOnlineAccountTableCode("Antônio Filho"),
        )
        assertEquals(
            "A1F",
            normalizeOnlineAccountTableCodeInput("á-1_fXYZ"),
        )
    }

    @Test
    fun legacy_table_name_remains_readable_without_truncation() {
        val profile = createLegacyCompatibleOnlineAccountProfile(
            publicDisplayName = "Antônio Filho",
            tableName = "antônio",
            updatedAtEpochMillis = 1_000L,
        )

        assertEquals(
            "ANTÔNIO",
            profile.tableName,
        )
    }

    @Test
    fun public_name_allows_a_single_valid_name() {
        val profile = createOnlineAccountProfile(
            publicDisplayName = "Alice",
            tableName = "ALI",
            updatedAtEpochMillis = 1_000L,
        )

        assertEquals("Alice", profile.publicDisplayName)
        assertEquals("ALI", profile.tableName)
    }

    @Test
    fun table_code_rejects_spaces_and_symbols() {
        assertThrows(IllegalArgumentException::class.java) {
            createOnlineAccountProfile(
                publicDisplayName = "Antônio Filho",
                tableName = "A F",
                updatedAtEpochMillis = 1_000L,
            )
        }
    }

    @Test
    fun public_name_preserves_latin_accents_and_normalizes_nfc() {
        assertEquals(
            "Jos\u00E9 Gonz\u00E1lez",
            normalizeOnlinePublicDisplayName(
                "  Jose\u0301   Gonza\u0301lez  ",
            ),
        )
        assertEquals(
            "Mar\u00EDa-Jos\u00E9 Mu\u00F1oz",
            normalizeOnlinePublicDisplayName(
                "Mar\u00EDa-Jos\u00E9 Mu\u00F1oz",
            ),
        )
    }

    @Test
    fun unicode_public_name_does_not_relax_table_code_policy() {
        val profile = createOnlineAccountProfile(
            publicDisplayName = "Jo\u00E3o Mu\u00F1oz",
            tableName = "JM1",
            updatedAtEpochMillis = 1_000L,
        )

        assertEquals(
            "Jo\u00E3o Mu\u00F1oz",
            profile.publicDisplayName,
        )
        assertEquals("JM1", profile.tableName)
        assertFalse(isValidOnlineAccountTableCode("J\u00D51"))
        assertTrue(isValidOnlineAccountTableCode("JM1"))
    }

}
