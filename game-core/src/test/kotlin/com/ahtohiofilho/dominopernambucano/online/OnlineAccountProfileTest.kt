package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineAccountProfileTest {
    @Test
    fun public_name_and_table_name_are_normalized_separately() {
        val profile = createOnlineAccountProfile(
            publicDisplayName = "  Antônio   Filho  ",
            tableName = "afi",
            updatedAtEpochMillis = 1_000L,
        )

        assertEquals(
            "Antônio Filho",
            profile.publicDisplayName,
        )
        assertEquals(
            "AFI",
            profile.tableName,
        )
    }

    @Test
    fun blank_table_name_is_generated_from_public_name() {
        val profile = createOnlineAccountProfile(
            publicDisplayName = "Antônio Filho",
            tableName = " ",
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
    fun table_name_rejects_spaces_and_symbols() {
        assertThrows(IllegalArgumentException::class.java) {
            createOnlineAccountProfile(
                publicDisplayName = "Antônio Filho",
                tableName = "A F!",
                updatedAtEpochMillis = 1_000L,
            )
        }
    }
}
