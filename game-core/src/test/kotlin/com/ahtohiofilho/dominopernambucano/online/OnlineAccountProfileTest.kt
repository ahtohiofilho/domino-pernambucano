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
    fun public_name_requires_name_and_surname() {
        assertThrows(IllegalArgumentException::class.java) {
            createOnlineAccountProfile(
                publicDisplayName = "Antônio",
                tableName = "AFI",
                updatedAtEpochMillis = 1_000L,
            )
        }
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
}
