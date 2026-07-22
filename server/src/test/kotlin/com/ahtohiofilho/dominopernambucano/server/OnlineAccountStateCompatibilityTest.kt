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
    fun schemas_one_to_three_without_external_identities_restore() {
        listOf(1, 2, 3).forEach { schemaVersion ->
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
            assertTrue(state.externalIdentities.isEmpty())
            assertTrue(store.snapshotPersistentState().accounts.isEmpty())
            assertTrue(
                store.snapshotPersistentState().externalIdentities.isEmpty(),
            )
        }
    }
}
