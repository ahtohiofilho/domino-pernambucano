package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineEmailAccountManagerTest {
    @Test
    fun no_local_credential_link_creates_anonymous_then_links_without_recovery() =
        runBlocking {
            val store = EmailManagerCredentialStore()
            val apiClient = EmailManagerApiClient()
            val manager = createManager(store, apiClient)

            val request = manager.requestCode(
                rawEmail = "Player@Example.com",
                intent = OnlineEmailAccountIntent.LINK,
            )
            val submit = manager.submitCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
                intent = OnlineEmailAccountIntent.LINK,
            )

            assertEquals(
                OnlineEmailAccountActionResult.CodeRequested(
                    email = "player@example.com",
                    intent = OnlineEmailAccountIntent.LINK,
                ),
                request,
            )
            assertTrue(
                submit is OnlineEmailAccountActionResult.Success,
            )
            assertEquals(1, apiClient.createAnonymousCallCount)
            assertEquals(1, apiClient.linkCallCount)
            assertEquals(0, apiClient.recoverCallCount)
            assertEquals(
                OnlineSessionKind.ACCOUNT,
                store.credential?.sessionKind,
            )
        }

    @Test
    fun no_local_credential_recovery_never_creates_or_links() =
        runBlocking {
            val store = EmailManagerCredentialStore()
            val apiClient = EmailManagerApiClient()
            val manager = createManager(store, apiClient)

            manager.requestCode(
                rawEmail = "player@example.com",
                intent = OnlineEmailAccountIntent.RECOVER,
            )
            val result = manager.submitCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
                intent = OnlineEmailAccountIntent.RECOVER,
            )

            assertTrue(
                result is OnlineEmailAccountActionResult.Success,
            )
            assertEquals(0, apiClient.createAnonymousCallCount)
            assertEquals(0, apiClient.linkCallCount)
            assertEquals(1, apiClient.recoverCallCount)
        }

    @Test
    fun account_not_found_does_not_fallback_to_link_with_consumed_code() =
        runBlocking {
            val store = EmailManagerCredentialStore()
            val apiClient = EmailManagerApiClient(
                recoverFailure = OnlineEmailIdentityException(
                    OnlineEmailIdentityFailureReason.ACCOUNT_NOT_FOUND,
                ),
            )
            val manager = createManager(store, apiClient)

            val result = manager.submitCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
                intent = OnlineEmailAccountIntent.RECOVER,
            )

            assertEquals(
                OnlineEmailAccountActionResult.Failure(
                    reason =
                        OnlineEmailAccountFailureReason.ACCOUNT_NOT_FOUND,
                    requiresNewCode = true,
                ),
                result,
            )
            assertEquals(1, apiClient.recoverCallCount)
            assertEquals(0, apiClient.createAnonymousCallCount)
            assertEquals(0, apiClient.linkCallCount)
        }

    @Test
    fun invalid_code_keeps_current_challenge_retriable() = runBlocking {
        val store = EmailManagerCredentialStore()
        val apiClient = EmailManagerApiClient(
            recoverFailure = OnlineEmailIdentityException(
                OnlineEmailIdentityFailureReason.INVALID_CODE,
            ),
        )
        val manager = createManager(store, apiClient)

        val result = manager.submitCode(
            rawEmail = "player@example.com",
            rawCode = "123456",
            intent = OnlineEmailAccountIntent.RECOVER,
        )

        assertEquals(
            OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.INVALID_CODE,
                requiresNewCode = false,
            ),
            result,
        )
    }

    @Test
    fun availability_and_status_are_provider_agnostic() {
        val store = EmailManagerCredentialStore()
        val apiClient = EmailManagerApiClient()
        val manager = createManager(store, apiClient)

        assertTrue(manager.isAvailable)
        assertEquals(
            OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
            manager.currentStatus(),
        )

        store.credential = OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            accountId = "account-1",
            playerId = "player-1",
            accessToken = "account-token",
            expiresAtEpochMillis = 2_000L,
        )

        assertEquals(
            OnlineGoogleAccountStatus.CONNECTED,
            manager.currentStatus(),
        )

        val unavailable = createManager(
            store = EmailManagerCredentialStore(),
            apiClient = apiClient,
            available = false,
        )
        assertFalse(unavailable.isAvailable)
        assertEquals(
            OnlineGoogleAccountStatus.UNAVAILABLE,
            unavailable.currentStatus(),
        )
    }

    private fun createManager(
        store: EmailManagerCredentialStore,
        apiClient: EmailManagerApiClient,
        available: Boolean = true,
    ): OnlineEmailAccountManager {
        val credentialRepository =
            OnlineSessionCredentialRepository(
                store = store,
                nowEpochMillis = { 1_000L },
                accountRefreshWindowMillis = 100L,
            )
        return OnlineEmailAccountManager(
            available = available,
            apiClient = apiClient,
            emailIdentityRepository =
                OnlineEmailIdentityRepository(
                    apiClient = apiClient,
                    sessionCredentialRepository =
                        credentialRepository,
                ),
            sessionCredentialRepository = credentialRepository,
            nowEpochMillis = { 1_000L },
        )
    }
}

private class EmailManagerCredentialStore(
    var credential: OnlineSessionCredential? = null,
) : OnlineSessionCredentialStore {
    override fun read(): OnlineSessionCredential? = credential

    override fun write(
        credential: OnlineSessionCredential,
    ): Boolean {
        this.credential = credential
        return true
    }

    override fun clear(): Boolean {
        credential = null
        return true
    }
}

private class EmailManagerApiClient(
    private val recoverFailure: Throwable? = null,
) : RemoteOnlineApiClient {
    var createAnonymousCallCount = 0
        private set
    var linkCallCount = 0
        private set
    var recoverCallCount = 0
        private set

    override suspend fun requestEmailCode(
        request: OnlineEmailCodeRequestDto,
    ) = OnlineEmailCodeRequestResponseDto()

    override suspend fun createAnonymousSession():
        OnlineAnonymousSessionDto {
        createAnonymousCallCount += 1
        return OnlineAnonymousSessionDto(
            playerId = "player-1",
            accessToken = "anonymous-token",
            expiresAtEpochMillis = 2_000L,
        )
    }

    override suspend fun linkEmailIdentity(
        request: OnlineEmailIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        linkCallCount += 1
        return accountSession()
    }

    override suspend fun recoverEmailAccount(
        request: OnlineEmailIdentityRequestDto,
    ): OnlineAccountSessionDto {
        recoverCallCount += 1
        recoverFailure?.let { throw it }
        return accountSession()
    }

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto = error("Not used.")

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto = error("Not used.")

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto = error("Not used.")

    override suspend fun fetchRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto = error("Not used.")

    override suspend fun fetchMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto = error("Not used.")

    private fun accountSession() =
        OnlineAccountSessionDto(
            accountId = "account-1",
            playerId = "player-1",
            accessToken = "account-token",
            expiresAtEpochMillis = 3_000L,
        )
}
