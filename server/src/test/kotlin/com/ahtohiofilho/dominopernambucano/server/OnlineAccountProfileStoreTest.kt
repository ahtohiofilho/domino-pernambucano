package com.ahtohiofilho.dominopernambucano.server

import java.io.File
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineAccountProfileStoreTest {
    @Test
    fun account_profile_is_authoritative_and_batch_readable() {
        var now = 1_000L
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            accountIdFactory = { "account-1" },
        )
        val account = requireNotNull(
            store.promoteAccount(
                playerId = "player-1",
            ),
        )

        assertNull(
            store.getAccountProfile(
                accountId = account.accountId,
            ),
        )

        now = 2_000L
        val profile = requireNotNull(
            store.updateAccountProfile(
                accountId = account.accountId,
                publicDisplayName = "Antônio Filho",
                tableName = "afi",
            ),
        )

        assertEquals(
            "Antônio Filho",
            profile.publicDisplayName,
        )
        assertEquals(
            "AFI",
            profile.tableName,
        )
        assertEquals(
            profile,
            store.getAccountProfile(
                accountId = account.accountId,
            ),
        )
        assertEquals(
            mapOf(
                account.accountId to "Antônio Filho",
            ),
            store.getPublicDisplayNames(
                accountIds = setOf(
                    account.accountId,
                    "account-missing",
                ),
            ),
        )
    }

    @Test
    fun profile_update_does_not_create_or_merge_accounts() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        assertNull(
            store.updateAccountProfile(
                accountId = "account-missing",
                publicDisplayName = "Antônio Filho",
                tableName = "AFI",
            ),
        )
        assertEquals(
            emptyList<OnlineServerAccount>(),
            store.snapshotPersistentState().accounts,
        )
    }

    @Test
    fun profile_survives_persistent_store_restart() {
        val root = File(
            System.getProperty("java.io.tmpdir"),
            "online-account-profile-${UUID.randomUUID()}",
        )
        val stateFile = File(root, "authoritative-state.json")

        try {
            val firstStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-1" },
            )
            val account = requireNotNull(
                firstStore.promoteAccount(
                    playerId = "player-1",
                ),
            )

            firstStore.updateAccountProfile(
                accountId = account.accountId,
                publicDisplayName = "Antônio Filho",
                tableName = "AFI",
            )
            firstStore.close()

            val restartedStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 2_000L },
            )

            assertEquals(
                "Antônio Filho",
                restartedStore.getAccountProfile(
                    accountId = account.accountId,
                )?.publicDisplayName,
            )
            assertEquals(
                "AFI",
                restartedStore.getAccountProfile(
                    accountId = account.accountId,
                )?.tableName,
            )
            restartedStore.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacy_table_name_remains_readable_after_restore() {
        val store = InMemoryOnlineServerStore()
        val legacyAccount = OnlineServerAccount(
            accountId = "account-legacy",
            playerId = "player-legacy",
            createdAtEpochMillis = 1_000L,
            publicDisplayName = "Antônio Filho",
            tableName = "ANTÔNIO",
            profileUpdatedAtEpochMillis = 2_000L,
        )

        store.restorePersistentState(
            OnlineServerStoreState(
                accounts = listOf(legacyAccount),
            ),
        )

        assertEquals(
            "ANTÔNIO",
            store.getAccountProfile(
                accountId = legacyAccount.accountId,
            )?.tableName,
        )
    }

    @Test
    fun profile_update_requires_three_character_table_code() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 2_000L },
            accountIdFactory = { "account-1" },
        )
        val account = requireNotNull(
            store.promoteAccount(
                playerId = "player-1",
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            store.updateAccountProfile(
                accountId = account.accountId,
                publicDisplayName = "Antônio Filho",
                tableName = "ANTÔNIO",
            )
        }

        assertNull(
            store.getAccountProfile(
                accountId = account.accountId,
            ),
        )
    }

    @Test
    fun schema_six_accounts_migrate_without_inventing_profile() {
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
        val legacyState = OnlineServerStoreState(
            schemaVersion = 6,
            accounts = listOf(
                OnlineServerAccount(
                    accountId = "account-1",
                    playerId = "player-1",
                    createdAtEpochMillis = 1_000L,
                ),
            ),
        )
        val encoded = json.encodeToString(legacyState)
            .replace(
                "\"schemaVersion\":7",
                "\"schemaVersion\":6",
            )
        val decoded = json.decodeFromString<OnlineServerStoreState>(
            encoded,
        )
        val store = InMemoryOnlineServerStore()

        store.restorePersistentState(decoded)

        assertEquals(
            ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
            store.snapshotPersistentState().schemaVersion,
        )
        assertNull(
            store.getAccountProfile(
                accountId = "account-1",
            ),
        )
    }

    @Test
    fun persisted_state_rejects_partial_or_invalid_profile() {
        val account = OnlineServerAccount(
            accountId = "account-1",
            playerId = "player-1",
            createdAtEpochMillis = 1_000L,
            publicDisplayName = "Antônio Filho",
            tableName = null,
            profileUpdatedAtEpochMillis = 2_000L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            InMemoryOnlineServerStore().restorePersistentState(
                OnlineServerStoreState(
                    accounts = listOf(account),
                ),
            )
        }
    }
}
