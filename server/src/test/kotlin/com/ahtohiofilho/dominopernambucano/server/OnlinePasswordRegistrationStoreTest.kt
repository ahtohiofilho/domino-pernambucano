package com.ahtohiofilho.dominopernambucano.server

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePasswordRegistrationStoreTest {
    @Test
    fun in_memory_registration_links_identity_and_password_together() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "account-1" },
        )
        val credential = passwordCredential()

        val result = store.registerEmailPasswordIdentity(
            playerId = "player-1",
            subject = "email-subject-1",
            credential = credential,
        )

        assertTrue(result is OnlineExternalIdentityLinkResult.Linked)
        val account = store.findAccountByExternalIdentity(
            provider = OnlineExternalIdentityProvider.EMAIL,
            subject = "email-subject-1",
        )
        assertNotNull(account)
        assertEquals(
            credential,
            store.getAccountPasswordCredential(
                requireNotNull(account).accountId,
            ),
        )

        val state = store.snapshotPersistentState()
        assertEquals(1, state.accounts.size)
        assertEquals(1, state.externalIdentities.size)
    }

    @Test
    fun failed_registration_rolls_back_identity_link() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "unused-account" },
        )
        val synthetic = requireNotNull(
            store.promoteSyntheticAccount(
                playerId = "synthetic-player",
            ),
        )
        val stateBefore = store.snapshotPersistentState()

        val result = store.registerEmailPasswordIdentity(
            playerId = synthetic.playerId,
            expectedAccountId = synthetic.accountId,
            subject = "email-subject-synthetic",
            credential = passwordCredential(),
        )

        assertEquals(
            OnlineExternalIdentityLinkResult.Conflict,
            result,
        )
        assertEquals(
            stateBefore,
            store.snapshotPersistentState(),
        )
        assertNull(
            store.findAccountByExternalIdentity(
                provider = OnlineExternalIdentityProvider.EMAIL,
                subject = "email-subject-synthetic",
            ),
        )
    }

    @Test
    fun persistent_registration_survives_restart_as_one_account() {
        val root = Files.createTempDirectory(
            "domino-password-registration-",
        ).toFile()
        val stateFile = File(root, "state.json")
        val credential = passwordCredential()

        try {
            val firstStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 1_000L },
            )
            val result = firstStore.registerEmailPasswordIdentity(
                playerId = "player-1",
                subject = "email-subject-1",
                credential = credential,
            )

            assertTrue(
                result is OnlineExternalIdentityLinkResult.Linked,
            )
            firstStore.close()

            val restartedStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 1_000L },
            )

            val account = restartedStore.findAccountByExternalIdentity(
                provider = OnlineExternalIdentityProvider.EMAIL,
                subject = "email-subject-1",
            )
            assertNotNull(account)
            assertEquals(
                credential,
                restartedStore.getAccountPasswordCredential(
                    requireNotNull(account).accountId,
                ),
            )
            restartedStore.close()
        } finally {
            root.deleteRecursively()
        }
    }

    private fun passwordCredential(): OnlineServerPasswordCredential {
        return OnlinePasswordCredentialService(
            iterations = 10_000,
            nowEpochMillis = { 1_000L },
        ).createCredential("SenhaSegura123!")
    }
}
