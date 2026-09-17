package com.ahtohiofilho.dominopernambucano.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePlayerIdentityTest {
    @Test
    fun table_code_is_normalized_to_three_uppercase_alphanumeric_characters() {
        assertEquals(
            "A7B",
            normalizeOfflineTableCode(" a-7b9 "),
        )
        assertTrue(isValidOfflineTableCode("A7B"))
        assertFalse(isValidOfflineTableCode("AB"))
    }

    @Test
    fun local_match_uses_human_code_plus_three_stable_local_bot_codes() {
        val codes = buildOfflinePlayerTableCodes(
            OfflinePlayerIdentity(
                displayName = "Jogador local",
                tableCode = "PE1",
            ),
        )

        assertEquals(
            listOf("PE1", "B01", "B02", "B03"),
            codes,
        )
    }

    @Test
    fun local_bot_codes_never_duplicate_the_human_code() {
        val codes = buildOfflinePlayerTableCodes(
            OfflinePlayerIdentity(
                displayName = "Jogador local",
                tableCode = "B01",
            ),
        )

        assertEquals(
            listOf("B01", "B02", "B03", "B04"),
            codes,
        )
        assertEquals(4, codes.distinct().size)
    }

    @Test
    fun display_name_preserves_latin_accents_and_normalizes_nfc() {
        assertEquals(
            "Jos\u00E9 Gonz\u00E1lez",
            normalizeOfflineDisplayName(
                "  Jose\u0301 Gonza\u0301lez  ",
            ),
        )
        assertEquals(
            "Jo\u00E3o Mu\u00F1oz",
            normalizeOfflineDisplayName("Jo\u00E3o Mu\u00F1oz"),
        )
        assertEquals(
            "Mar\u00EDa-Jos\u00E9",
            normalizeOfflineDisplayName("Mar\u00EDa-Jos\u00E9"),
        )
    }

    @Test
    fun unicode_display_name_does_not_relax_offline_table_code() {
        assertEquals(
            "J1",
            normalizeOfflineTableCode("J\u00D51"),
        )
        assertFalse(isValidOfflineTableCode("J\u00D51"))
        assertTrue(isValidOfflineTableCode("J01"))
    }

    @Test
    fun connected_profile_wins_over_saved_guest_identity() {
        val resolved = resolveLocalMatchPlayerIdentity(
            accountConnected = true,
            connectedDisplayName = "  Antônio Celular  ",
            connectedTableCode = " ant ",
            storedOfflineIdentity = OfflinePlayerIdentity(
                displayName = "Convidado",
                tableCode = "G01",
            ),
        )

        assertEquals(
            OfflinePlayerIdentity(
                displayName = "Antônio Celular",
                tableCode = "ANT",
            ),
            resolved,
        )
    }

    @Test
    fun connected_profile_works_without_saved_guest_identity() {
        val resolved = resolveLocalMatchPlayerIdentity(
            accountConnected = true,
            connectedDisplayName = "Antônio Celular",
            connectedTableCode = "ANT",
            storedOfflineIdentity = null,
        )

        assertEquals(
            OfflinePlayerIdentity(
                displayName = "Antônio Celular",
                tableCode = "ANT",
            ),
            resolved,
        )
    }

    @Test
    fun disconnected_player_keeps_saved_guest_identity() {
        val guestIdentity = OfflinePlayerIdentity(
            displayName = "Convidado",
            tableCode = "G01",
        )

        val resolved = resolveLocalMatchPlayerIdentity(
            accountConnected = false,
            connectedDisplayName = "Conta ignorada",
            connectedTableCode = "ACC",
            storedOfflineIdentity = guestIdentity,
        )

        assertEquals(
            guestIdentity,
            resolved,
        )
    }

    @Test
    fun invalid_connected_profile_falls_back_to_saved_guest_identity() {
        val guestIdentity = OfflinePlayerIdentity(
            displayName = "Convidado",
            tableCode = "G01",
        )

        val resolved = resolveLocalMatchPlayerIdentity(
            accountConnected = true,
            connectedDisplayName = "Conta",
            connectedTableCode = "AB",
            storedOfflineIdentity = guestIdentity,
        )

        assertEquals(
            guestIdentity,
            resolved,
        )
    }

    @Test
    fun disconnected_player_without_guest_identity_requires_setup() {
        val resolved = resolveLocalMatchPlayerIdentity(
            accountConnected = false,
            connectedDisplayName = "Conta ignorada",
            connectedTableCode = "ACC",
            storedOfflineIdentity = null,
        )

        assertNull(resolved)
    }

}
