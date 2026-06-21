package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlayerIdentityRepositoryTest {
    @Test
    fun get_or_create_persists_generated_player_id_for_next_read() {
        val storage = FakeOnlinePlayerIdentityStorage()

        val repository = OnlinePlayerIdentityRepository(
            storage = storage,
            playerIdFactory = {
                "player-fixed-id"
            },
        )

        val firstIdentity = repository.getOrCreate()
        val secondIdentity = repository.getOrCreate()

        assertEquals(
            "player-fixed-id",
            firstIdentity.playerId,
        )

        assertEquals(
            firstIdentity,
            secondIdentity,
        )

        assertEquals(
            DEFAULT_ONLINE_PLAYER_NAME,
            firstIdentity.playerName,
        )
    }

    @Test
    fun update_player_name_keeps_same_player_id() {
        val storage = FakeOnlinePlayerIdentityStorage()

        val repository = OnlinePlayerIdentityRepository(
            storage = storage,
            playerIdFactory = {
                "player-fixed-id"
            },
        )

        val initialIdentity = repository.getOrCreate()

        val updatedIdentity = repository.updatePlayerName(
            playerName = "  Antônio   Filho  ",
        )

        assertEquals(
            initialIdentity.playerId,
            updatedIdentity.playerId,
        )

        assertEquals(
            "Antônio Filho",
            updatedIdentity.playerName,
        )

        assertEquals(
            updatedIdentity,
            repository.getOrCreate(),
        )
    }

    @Test
    fun blank_name_uses_default_online_player_name() {
        val storage = FakeOnlinePlayerIdentityStorage(
            initialValues = mapOf(
                "online_player_id" to "player-existing-id",
                "online_player_name" to "   ",
            ),
        )

        val repository = OnlinePlayerIdentityRepository(
            storage = storage,
        )

        val identity = repository.getOrCreate()

        assertEquals(
            "player-existing-id",
            identity.playerId,
        )

        assertEquals(
            DEFAULT_ONLINE_PLAYER_NAME,
            identity.playerName,
        )

        assertTrue(
            storage.values.containsValue(
                DEFAULT_ONLINE_PLAYER_NAME,
            )
        )
    }
}

private class FakeOnlinePlayerIdentityStorage(
    initialValues: Map<String, String> = emptyMap(),
) : OnlinePlayerIdentityStorage {
    val values = initialValues.toMutableMap()

    override fun readString(
        key: String,
    ): String? {
        return values[key]
    }

    override fun writeString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}