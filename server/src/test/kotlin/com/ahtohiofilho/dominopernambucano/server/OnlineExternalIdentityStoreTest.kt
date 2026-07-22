package com.ahtohiofilho.dominopernambucano.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineExternalIdentityStoreTest {
    @Test
    fun google_identity_link_is_idempotent_and_recovers_canonical_account() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "account-1" },
        )

        val first = store.linkExternalIdentity(
            playerId = "player-1",
            provider = OnlineExternalIdentityProvider.GOOGLE,
            subject = "google-subject-1",
        )
        val repeated = store.linkExternalIdentity(
            playerId = "player-1",
            expectedAccountId = "account-1",
            provider = OnlineExternalIdentityProvider.GOOGLE,
            subject = "google-subject-1",
        )

        assertEquals(first, repeated)
        assertEquals(
            OnlineServerAccount(
                accountId = "account-1",
                playerId = "player-1",
                createdAtEpochMillis = 1_000L,
            ),
            store.findAccountByExternalIdentity(
                provider = OnlineExternalIdentityProvider.GOOGLE,
                subject = "google-subject-1",
            ),
        )
        assertEquals(1, store.snapshotPersistentState().accounts.size)
        assertEquals(
            1,
            store.snapshotPersistentState().externalIdentities.size,
        )
    }

    @Test
    fun google_identity_never_merges_accounts_or_changes_canonical_player() {
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = {
                accountSequence += 1
                "account-$accountSequence"
            },
        )

        store.linkExternalIdentity(
            playerId = "player-1",
            provider = OnlineExternalIdentityProvider.GOOGLE,
            subject = "google-subject-1",
        )
        store.promoteAccount(
            playerId = "player-2",
        )

        assertSame(
            OnlineExternalIdentityLinkResult.Conflict,
            store.linkExternalIdentity(
                playerId = "player-2",
                expectedAccountId = "account-2",
                provider = OnlineExternalIdentityProvider.GOOGLE,
                subject = "google-subject-1",
            ),
        )
        assertEquals(2, store.snapshotPersistentState().accounts.size)
        assertEquals(
            "player-1",
            store.findAccountByExternalIdentity(
                provider = OnlineExternalIdentityProvider.GOOGLE,
                subject = "google-subject-1",
            )?.playerId,
        )
        assertNull(
            store.findAccountByExternalIdentity(
                provider = OnlineExternalIdentityProvider.GOOGLE,
                subject = "unknown-subject",
            ),
        )
    }

    @Test
    fun one_google_identity_per_account_is_enforced() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "account-1" },
        )
        val first = store.linkExternalIdentity(
            playerId = "player-1",
            provider = OnlineExternalIdentityProvider.GOOGLE,
            subject = "google-subject-1",
        )

        assertTrue(first is OnlineExternalIdentityLinkResult.Linked)
        assertSame(
            OnlineExternalIdentityLinkResult.Conflict,
            store.linkExternalIdentity(
                playerId = "player-1",
                expectedAccountId = "account-1",
                provider = OnlineExternalIdentityProvider.GOOGLE,
                subject = "google-subject-2",
            ),
        )
        assertEquals(
            1,
            store.snapshotPersistentState().externalIdentities.size,
        )
    }

    @Test
    fun restore_rejects_orphan_and_duplicate_external_identities() {
        val account = OnlineServerAccount(
            accountId = "account-1",
            playerId = "player-1",
            createdAtEpochMillis = 1_000L,
        )
        val identity = OnlineServerExternalIdentity(
            provider = OnlineExternalIdentityProvider.GOOGLE,
            subject = "google-subject-1",
            accountId = account.accountId,
            linkedAtEpochMillis = 1_000L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            InMemoryOnlineServerStore().restorePersistentState(
                OnlineServerStoreState(
                    accounts = listOf(account),
                    externalIdentities = listOf(identity, identity),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            InMemoryOnlineServerStore().restorePersistentState(
                OnlineServerStoreState(
                    accounts = listOf(account),
                    externalIdentities = listOf(
                        identity.copy(accountId = "account-missing"),
                    ),
                ),
            )
        }
    }
}
