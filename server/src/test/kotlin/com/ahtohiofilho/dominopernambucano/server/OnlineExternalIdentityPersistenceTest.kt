package com.ahtohiofilho.dominopernambucano.server

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineExternalIdentityPersistenceTest {
    @Test
    fun google_identity_and_canonical_account_survive_restart() {
        val root = File(
            System.getProperty("java.io.tmpdir"),
            "online-external-identity-${UUID.randomUUID()}",
        )
        val stateFile = File(root, "authoritative-state.json")

        try {
            val firstStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-1" },
            )
            val linked = firstStore.linkExternalIdentity(
                playerId = "player-1",
                provider = OnlineExternalIdentityProvider.GOOGLE,
                subject = "google-subject-1",
            )

            assertTrue(linked is OnlineExternalIdentityLinkResult.Linked)
            firstStore.close()

            val restartedStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 2_000L },
                accountIdFactory = { "account-must-not-be-created" },
            )

            assertEquals(
                OnlineServerAccount(
                    accountId = "account-1",
                    playerId = "player-1",
                    createdAtEpochMillis = 1_000L,
                ),
                restartedStore.findAccountByExternalIdentity(
                    provider = OnlineExternalIdentityProvider.GOOGLE,
                    subject = "google-subject-1",
                ),
            )
            assertEquals(
                linked,
                restartedStore.linkExternalIdentity(
                    playerId = "player-1",
                    expectedAccountId = "account-1",
                    provider = OnlineExternalIdentityProvider.GOOGLE,
                    subject = "google-subject-1",
                ),
            )
            restartedStore.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun persistence_failure_rolls_back_account_and_external_identity_together() {
        val root = File(
            System.getProperty("java.io.tmpdir"),
            "online-external-identity-rollback-${UUID.randomUUID()}",
        )
        val stateFile = File(root, "authoritative-state.json")
        val persistence = SwitchableIdentityStatePersistence(
            delegate = FileOnlineServerStatePersistence(
                stateFile = stateFile,
            ),
        )

        try {
            val store = PersistentOnlineServerStore.open(
                statePersistence = persistence,
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-1" },
            )
            persistence.failWrites = true

            assertThrows(IllegalStateException::class.java) {
                store.linkExternalIdentity(
                    playerId = "player-1",
                    provider = OnlineExternalIdentityProvider.GOOGLE,
                    subject = "google-subject-1",
                )
            }
            assertNull(
                store.findAccountByExternalIdentity(
                    provider = OnlineExternalIdentityProvider.GOOGLE,
                    subject = "google-subject-1",
                ),
            )

            persistence.failWrites = false
            val retry = store.linkExternalIdentity(
                playerId = "player-1",
                provider = OnlineExternalIdentityProvider.GOOGLE,
                subject = "google-subject-1",
            )

            assertTrue(retry is OnlineExternalIdentityLinkResult.Linked)
            store.close()
        } finally {
            root.deleteRecursively()
        }
    }

    private class SwitchableIdentityStatePersistence(
        private val delegate: OnlineServerStatePersistence,
    ) : OnlineServerStatePersistence by delegate {
        var failWrites = false

        override fun write(
            state: OnlineServerStoreState,
        ) {
            if (failWrites) {
                throw IllegalStateException("persistence failure")
            }

            delegate.write(state)
        }
    }
}
