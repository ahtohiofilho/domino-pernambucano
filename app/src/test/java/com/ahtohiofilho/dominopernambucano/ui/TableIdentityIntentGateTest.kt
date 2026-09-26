package com.ahtohiofilho.dominopernambucano.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableIdentityIntentGateTest {
    @Test
    fun four_intents_share_the_same_gate() {
        assertEquals(
            setOf(
                TableIdentityIntent.ONLINE_MATCHMAKING,
                TableIdentityIntent.OFFLINE_MATCH,
                TableIdentityIntent.CREATE_PRIVATE_ROOM,
                TableIdentityIntent.JOIN_PRIVATE_ROOM,
            ),
            TableIdentityIntent.values().toSet(),
        )
    }

    @Test
    fun valid_connected_account_code_is_an_existing_record() {
        val result = resolveTableIdentityGate(
            accountConnected = true,
            connectedCodeConfirmedByPlayer = true,
            connectedTableCode = "afi",
            storedOfflineTableCode = null,
            displayName = "Antônio Filho",
        )
        assertFalse(result.requiresConfirmation)
        assertEquals("AFI", result.confirmedCode)
    }

    @Test
    fun unconfirmed_connected_three_letter_value_is_only_a_suggestion() {
        val result = resolveTableIdentityGate(
            accountConnected = true,
            connectedCodeConfirmedByPlayer = false,
            connectedTableCode = "ant",
            storedOfflineTableCode = null,
            displayName = "Antônio Filho",
        )

        assertTrue(result.requiresConfirmation)
        assertNull(result.confirmedCode)
        assertEquals("ANT", result.suggestedCode)
    }

    @Test
    fun valid_offline_code_is_an_existing_record() {
        val result = resolveTableIdentityGate(
            accountConnected = false,
            connectedCodeConfirmedByPlayer = false,
            connectedTableCode = "JOGADOR",
            storedOfflineTableCode = "cai",
            displayName = "Caio Silva",
        )
        assertFalse(result.requiresConfirmation)
        assertEquals("CAI", result.confirmedCode)
    }

    @Test
    fun automatic_suggestion_never_counts_as_confirmation() {
        val result = resolveTableIdentityGate(
            accountConnected = false,
            connectedCodeConfirmedByPlayer = false,
            connectedTableCode = "ant",
            storedOfflineTableCode = null,
            displayName = "Antônio Filho",
        )
        assertTrue(result.requiresConfirmation)
        assertNull(result.confirmedCode)
        assertEquals("ANT", result.suggestedCode)
    }

    @Test
    fun missing_record_suggests_three_characters_from_display_name() {
        val result = resolveTableIdentityGate(
            accountConnected = false,
            connectedCodeConfirmedByPlayer = false,
            connectedTableCode = "JOGADOR",
            storedOfflineTableCode = null,
            displayName = "Antônio Filho",
        )
        assertTrue(result.requiresConfirmation)
        assertNull(result.confirmedCode)
        assertEquals("ANT", result.suggestedCode)
    }
}