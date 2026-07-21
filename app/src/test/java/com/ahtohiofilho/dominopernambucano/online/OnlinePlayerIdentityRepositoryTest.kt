package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlayerIdentityRepositoryTest {
    @Test
    fun get_or_create_persists_generated_player_id_and_default_names() {
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
            DEFAULT_ONLINE_DISPLAY_NAME,
            firstIdentity.displayName,
        )

        assertEquals(
            DEFAULT_ONLINE_TABLE_NAME,
            firstIdentity.tableName,
        )
    }

    @Test
    fun legacy_player_name_migrates_without_changing_player_id() {
        val storage = FakeOnlinePlayerIdentityStorage(
            initialValues = mapOf(
                "online_player_id" to "player-existing-id",
                "online_player_name" to "  Antônio   Filho  ",
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
            "Antônio Filho",
            identity.displayName,
        )

        assertEquals(
            "ANTÔNIO",
            identity.tableName,
        )

        assertEquals(
            "Antônio Filho",
            storage.values["online_display_name"],
        )

        assertEquals(
            "ANTÔNIO",
            storage.values["online_table_name"],
        )
    }

    @Test
    fun updating_display_name_refreshes_generated_table_name() {
        val storage = FakeOnlinePlayerIdentityStorage()

        val repository = OnlinePlayerIdentityRepository(
            storage = storage,
            playerIdFactory = {
                "player-fixed-id"
            },
        )

        val updatedIdentity = repository.updateDisplayName(
            displayName = "  Maria   Eduarda  ",
        )

        assertEquals(
            "Maria Eduarda",
            updatedIdentity.displayName,
        )

        assertEquals(
            "MARIA",
            updatedIdentity.tableName,
        )

        assertEquals(
            updatedIdentity,
            repository.getOrCreate(),
        )
    }

    @Test
    fun custom_table_name_survives_display_name_change() {
        val storage = FakeOnlinePlayerIdentityStorage()

        val repository = OnlinePlayerIdentityRepository(
            storage = storage,
            playerIdFactory = {
                "player-fixed-id"
            },
        )

        repository.updateDisplayName(
            displayName = "Antônio Filho",
        )

        repository.updateTableName(
            tableName = "afi",
        )

        val updatedIdentity = repository.updateDisplayName(
            displayName = "Antônio F.",
        )

        assertEquals(
            "Antônio F.",
            updatedIdentity.displayName,
        )

        assertEquals(
            "AFI",
            updatedIdentity.tableName,
        )
    }

    @Test
    fun reset_table_name_returns_to_generated_value() {
        val storage = FakeOnlinePlayerIdentityStorage()

        val repository = OnlinePlayerIdentityRepository(
            storage = storage,
            playerIdFactory = {
                "player-fixed-id"
            },
        )

        repository.updateDisplayName(
            displayName = "Antônio Filho",
        )

        repository.updateTableName(
            tableName = "afi",
        )

        val resetIdentity = repository.resetTableNameToGenerated()

        assertEquals(
            "ANTÔNIO",
            resetIdentity.tableName,
        )
    }

    @Test
    fun blank_display_name_uses_default_online_display_name() {
        val storage = FakeOnlinePlayerIdentityStorage(
            initialValues = mapOf(
                "online_player_id" to "player-existing-id",
                "online_display_name" to "   ",
            ),
        )

        val repository = OnlinePlayerIdentityRepository(
            storage = storage,
        )

        val identity = repository.getOrCreate()

        assertEquals(
            DEFAULT_ONLINE_DISPLAY_NAME,
            identity.displayName,
        )

        assertEquals(
            DEFAULT_ONLINE_TABLE_NAME,
            identity.tableName,
        )

        assertTrue(
            storage.values.containsValue(
                DEFAULT_ONLINE_DISPLAY_NAME,
            ),
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
