package com.ahtohiofilho.dominopernambucano.server

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAccountStateCompatibilityTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun schemas_one_and_two_without_accounts_restore_with_empty_registry() {
        listOf(1, 2).forEach { schemaVersion ->
            val state = json.decodeFromString<OnlineServerStoreState>(
                """
                {
                  "schemaVersion": $schemaVersion,
                  "nextRoomSequence": 1,
                  "nextMatchSequence": 1,
                  "rooms": [],
                  "matches": [],
                  "actionResults": [],
                  "rankedResults": []
                }
                """.trimIndent(),
            )
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
            )

            store.restorePersistentState(state)

            assertTrue(state.accounts.isEmpty())
            assertTrue(store.snapshotPersistentState().accounts.isEmpty())
        }
    }
}
