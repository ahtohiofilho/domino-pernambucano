package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlinePasswordAccountManagerTest {
    @Test
    fun failed_sign_in_preserves_anonymous_credential() = runBlocking {
        val original = anonymousCredential()
        val store = PasswordManagerCredentialStore(original)
        val apiClient = PasswordManagerApiClient(
            loginFailure = IllegalStateException("remote login failed"),
        )
        val manager = createManager(store, apiClient)

        val result = manager.signIn(
            rawEmail = "player@example.com",
            rawPassword = "SenhaSegura123!",
        )

        assertEquals(
            OnlinePasswordAccountActionResult.Failure(
                OnlinePasswordAccountFailureReason.UNKNOWN,
            ),
            result,
        )
        assertEquals(original, store.credential)
        assertEquals(1, apiClient.loginCallCount)
    }

    @Test
    fun successful_sign_in_replaces_anonymous_only_after_remote_success() =
        runBlocking {
            val store = PasswordManagerCredentialStore(
                anonymousCredential(),
            )
            val apiClient = PasswordManagerApiClient()
            val manager = createManager(store, apiClient)

            val result = manager.signIn(
                rawEmail = "player@example.com",
                rawPassword = "SenhaSegura123!",
            )

            assertEquals(
                OnlinePasswordAccountActionResult.Success,
                result,
            )
            assertEquals(1, apiClient.loginCallCount)
            assertEquals(
                OnlineSessionKind.ACCOUNT,
                store.credential?.sessionKind,
            )
            assertEquals(
                "account-login",
                store.credential?.accountId,
            )
            assertEquals(
                "player-account",
                store.credential?.playerId,
            )
        }

    @Test
    fun failed_password_reset_preserves_anonymous_credential() =
        runBlocking {
            val original = anonymousCredential()
            val store = PasswordManagerCredentialStore(original)
            val apiClient = PasswordManagerApiClient(
                resetFailure = IllegalStateException("remote reset failed"),
            )
            val manager = createManager(store, apiClient)

            val result = manager.resetPassword(
                rawEmail = "player@example.com",
                rawPassword = "SenhaSegura123!",
                rawCode = "123456",
            )

            assertEquals(
                OnlinePasswordAccountActionResult.Failure(
                    OnlinePasswordAccountFailureReason.UNKNOWN,
                ),
                result,
            )
            assertEquals(original, store.credential)
            assertEquals(1, apiClient.resetCallCount)
        }

    @Test
    fun create_account_promotes_existing_anonymous_credential() =
        runBlocking {
            val store = PasswordManagerCredentialStore(
                anonymousCredential(),
            )
            val apiClient = PasswordManagerApiClient()
            val manager = createManager(store, apiClient)

            val result = manager.createAccount(
                rawEmail = "player@example.com",
                rawPassword = "SenhaSegura123!",
                rawCode = "123456",
            )

            assertEquals(
                OnlinePasswordAccountActionResult.Success,
                result,
            )
            assertEquals(0, apiClient.createAnonymousCallCount)
            assertEquals(1, apiClient.registerCallCount)
            assertEquals(
                "anonymous-token",
                apiClient.lastRegisterAccessToken,
            )
            assertEquals(
                OnlineSessionKind.ACCOUNT,
                store.credential?.sessionKind,
            )
            assertEquals(
                "account-created",
                store.credential?.accountId,
            )
            assertEquals(
                "player-anonymous",
                store.credential?.playerId,
            )
        }

    private fun createManager(
        store: PasswordManagerCredentialStore,
        apiClient: PasswordManagerApiClient,
    ): OnlinePasswordAccountManager {
        val repository = OnlineSessionCredentialRepository(
            store = store,
            nowEpochMillis = { 1_000L },
            accountRefreshWindowMillis = 100L,
        )
        return OnlinePasswordAccountManager(
            available = true,
            apiClient = apiClient,
            sessionCredentialRepository = repository,
        )
    }

    private fun anonymousCredential() =
        OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ANONYMOUS,
            accountId = null,
            playerId = "player-anonymous",
            accessToken = "anonymous-token",
            expiresAtEpochMillis = 5_000L,
        )
}

private class PasswordManagerCredentialStore(
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

private class PasswordManagerApiClient(
    private val loginFailure: Throwable? = null,
    private val resetFailure: Throwable? = null,
) : RemoteOnlineApiClient {
    var createAnonymousCallCount = 0
        private set
    var loginCallCount = 0
        private set
    var resetCallCount = 0
        private set
    var registerCallCount = 0
        private set
    var lastRegisterAccessToken: String? = null
        private set

    override suspend fun createAnonymousSession():
        OnlineAnonymousSessionDto {
        createAnonymousCallCount += 1
        return OnlineAnonymousSessionDto(
            playerId = "player-anonymous",
            accessToken = "anonymous-token",
            expiresAtEpochMillis = 5_000L,
        )
    }

    override suspend fun loginEmailPassword(
        request: OnlinePasswordLoginRequestDto,
    ): OnlineAccountSessionDto {
        loginCallCount += 1
        loginFailure?.let { throw it }
        return OnlineAccountSessionDto(
            accountId = "account-login",
            playerId = "player-account",
            accessToken = "account-login-token",
            expiresAtEpochMillis = 5_000L,
        )
    }

    override suspend fun resetEmailPassword(
        request: OnlinePasswordResetRequestDto,
    ): OnlineAccountSessionDto {
        resetCallCount += 1
        resetFailure?.let { throw it }
        return OnlineAccountSessionDto(
            accountId = "account-reset",
            playerId = "player-account",
            accessToken = "account-reset-token",
            expiresAtEpochMillis = 5_000L,
        )
    }

    override suspend fun registerEmailPassword(
        request: OnlinePasswordRegisterRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        registerCallCount += 1
        lastRegisterAccessToken = accessToken
        return OnlineAccountSessionDto(
            accountId = "account-created",
            playerId = "player-anonymous",
            accessToken = "account-created-token",
            expiresAtEpochMillis = 5_000L,
        )
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
}
