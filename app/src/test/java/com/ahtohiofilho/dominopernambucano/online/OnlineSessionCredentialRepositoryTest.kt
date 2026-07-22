package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineSessionCredentialRepositoryTest {
    @Test
    fun promotion_replaces_anonymous_credential_and_preserves_player_id() =
        runBlocking {
            val anonymous = anonymousCredential()
            val store = FakeOnlineSessionCredentialStore(anonymous)
            val repository = createRepository(store)

            val promoted = repository.promoteCurrentCredential { token ->
                assertEquals(anonymous.accessToken, token)
                accountSession()
            }

            assertEquals(OnlineSessionKind.ACCOUNT, promoted.sessionKind)
            assertEquals(anonymous.playerId, promoted.playerId)
            assertEquals("account-1", promoted.accountId)
            assertEquals(promoted, store.storedCredential)
            assertEquals(1, store.writeCallCount)
        }

    @Test
    fun player_id_mismatch_keeps_previous_credential() = runBlocking {
        val anonymous = anonymousCredential()
        val store = FakeOnlineSessionCredentialStore(anonymous)
        val repository = createRepository(store)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.promoteCurrentCredential {
                    accountSession(playerId = "anonymous-player-other")
                }
            }
        }

        assertEquals(anonymous, store.storedCredential)
        assertEquals(0, store.writeCallCount)
    }

    @Test
    fun persistence_failure_keeps_previous_credential() = runBlocking {
        val anonymous = anonymousCredential()
        val store = FakeOnlineSessionCredentialStore(
            initialCredential = anonymous,
            writeSucceeds = false,
        )
        val repository = createRepository(store)

        assertThrows(
            OnlineSessionCredentialPersistenceException::class.java,
        ) {
            runBlocking {
                repository.promoteCurrentCredential {
                    accountSession()
                }
            }
        }

        assertEquals(anonymous, store.storedCredential)
    }

    @Test
    fun account_inside_refresh_window_is_renewed_idempotently() =
        runBlocking {
            val account = accountCredential(
                expiresAtEpochMillis = 1_200L,
            )
            val store = FakeOnlineSessionCredentialStore(account)
            val repository = OnlineSessionCredentialRepository(
                store = store,
                nowEpochMillis = { 1_000L },
                accountRefreshWindowMillis = 300L,
            )
            var createAnonymousCallCount = 0

            val refreshed = repository.getOrCreateUsableCredential(
                createAnonymousSession = {
                    createAnonymousCallCount += 1
                    anonymousSession()
                },
                refreshAccountSession = { token ->
                    assertEquals(account.accessToken, token)
                    accountSession(
                        accessToken = "renewed-account-token",
                        expiresAtEpochMillis = 3_000L,
                    )
                },
            )

            assertEquals("renewed-account-token", refreshed.accessToken)
            assertEquals(account.accountId, refreshed.accountId)
            assertEquals(0, createAnonymousCallCount)
            assertEquals(refreshed, store.storedCredential)
        }

    @Test
    fun expired_account_is_preserved_and_never_replaced_by_anonymous() =
        runBlocking {
            val account = accountCredential(
                expiresAtEpochMillis = 1_000L,
            )
            val store = FakeOnlineSessionCredentialStore(account)
            val repository = createRepository(store)
            var createAnonymousCallCount = 0

            assertNull(repository.getValidCredentialOrNull())

            assertThrows(OnlineAccountSessionExpiredException::class.java) {
                runBlocking {
                    repository.getOrCreateUsableCredential(
                        createAnonymousSession = {
                            createAnonymousCallCount += 1
                            anonymousSession()
                        },
                        refreshAccountSession = {
                            error("Conta expirada não pode ser renovada aqui.")
                        },
                    )
                }
            }

            assertEquals(0, createAnonymousCallCount)
            assertEquals(account, store.storedCredential)
            assertEquals(0, store.clearCallCount)
        }

    @Test
    fun expired_anonymous_session_is_cleared_and_replaced() = runBlocking {
        val store = FakeOnlineSessionCredentialStore(
            anonymousCredential(expiresAtEpochMillis = 1_000L),
        )
        val repository = createRepository(store)

        val replacement = repository.getOrCreateUsableCredential(
            createAnonymousSession = {
                anonymousSession(
                    playerId = "anonymous-player-2",
                    accessToken = "replacement-token",
                )
            },
            refreshAccountSession = {
                error("Sessão anônima não renova conta.")
            },
        )

        assertEquals("anonymous-player-2", replacement.playerId)
        assertEquals(1, store.clearCallCount)
        assertEquals(replacement, store.storedCredential)
    }

    private fun createRepository(
        store: OnlineSessionCredentialStore,
    ): OnlineSessionCredentialRepository {
        return OnlineSessionCredentialRepository(
            store = store,
            nowEpochMillis = { 1_000L },
            accountRefreshWindowMillis = 100L,
        )
    }

    private fun anonymousCredential(
        expiresAtEpochMillis: Long = 2_000L,
    ): OnlineSessionCredential {
        return anonymousSession(
            expiresAtEpochMillis = expiresAtEpochMillis,
        ).toOnlineSessionCredential()
    }

    private fun anonymousSession(
        playerId: String = "anonymous-player-1",
        accessToken: String = "anonymous-token",
        expiresAtEpochMillis: Long = 2_000L,
    ): OnlineAnonymousSessionDto {
        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = accessToken,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }

    private fun accountCredential(
        expiresAtEpochMillis: Long = 2_000L,
    ): OnlineSessionCredential {
        return accountSession(
            expiresAtEpochMillis = expiresAtEpochMillis,
        ).toOnlineSessionCredential()
    }

    private fun accountSession(
        playerId: String = "anonymous-player-1",
        accessToken: String = "account-token",
        expiresAtEpochMillis: Long = 2_000L,
    ): OnlineAccountSessionDto {
        return OnlineAccountSessionDto(
            accountId = "account-1",
            playerId = playerId,
            accessToken = accessToken,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }
}

private class FakeOnlineSessionCredentialStore(
    initialCredential: OnlineSessionCredential? = null,
    private val writeSucceeds: Boolean = true,
) : OnlineSessionCredentialStore {
    var storedCredential: OnlineSessionCredential? = initialCredential
        private set
    var writeCallCount: Int = 0
        private set
    var clearCallCount: Int = 0
        private set

    override fun read(): OnlineSessionCredential? = storedCredential

    override fun write(
        credential: OnlineSessionCredential,
    ): Boolean {
        writeCallCount += 1
        if (writeSucceeds) {
            storedCredential = credential
        }
        return writeSucceeds
    }

    override fun clear(): Boolean {
        storedCredential = null
        clearCallCount += 1
        return true
    }
}
