package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlinePlayerNamesTest {
    @Test
    fun display_name_collapses_whitespace_and_preserves_accents() {
        assertEquals(
            "Antônio Filho",
            normalizeOnlineDisplayName(
                rawName = "  Antônio   Filho  ",
            ),
        )
    }

    @Test
    fun generated_table_name_uses_first_public_name() {
        assertEquals(
            "ANTÔNIO",
            createDefaultOnlineTableName(
                displayName = "Antônio Filho",
            ),
        )
    }

    @Test
    fun generated_generic_player_name_keeps_numeric_distinction() {
        assertEquals(
            "JOGADOR2",
            createDefaultOnlineTableName(
                displayName = "Jogador 2",
            ),
        )
    }

    @Test
    fun explicit_table_name_is_uppercase_filtered_and_limited() {
        assertEquals(
            "AFI2026",
            normalizeOnlineTableName(
                rawName = " a-f_i 2026 ",
                fallbackDisplayName = "Antônio Filho",
            ),
        )
    }

    @Test
    fun room_player_exposes_public_and_generated_table_names() {
        val player = OnlineRoomPlayerDto(
            playerId = "player-1",
            name = "Antônio Filho",
            seatIndex = 0,
            connected = true,
        )

        assertEquals(
            "Antônio Filho",
            player.resolvedDisplayName,
        )

        assertEquals(
            "ANTÔNIO",
            player.resolvedTableName,
        )
    }
}
