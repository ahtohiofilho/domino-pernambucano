package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineGoogleAccountManagerTest {
    @Test
    fun status_distinguishes_visitor_connected_and_expired_account() {
        val store = ManagerCredentialStore(
            credential = anonymousCredential(),
        )
        val credentialRepository = credentialRepository(store)
        val manager = createManager(
            credentialRepository = credentialRepository,
        )

        assertEquals(
            OnlineGoogleAccountStatus.VISITOR,
            manager.currentStatus(),
        )

        store.credential = accountCredential(
            expiresAtEpochMillis = 2_000L,
        )
        assertEquals(
            OnlineGoogleAccountStatus.CONNECTED,
            manager.currentStatus(),
        )

        store.credential = accountCredential(
            expiresAtEpochMillis = 900L,
        )
        assertEquals(
            OnlineGoogleAccountStatus.RECOVERY_REQUIRED,
            manager.currentStatus(),
        )
    }

    @Test
    fun valid_account_remains_connected_when_google_provider_is_unavailable() {
        val store = ManagerCredentialStore(
            credential = accountCredential(
                expiresAtEpochMillis = 2_000L,
            ),
        )
        val credentialRepository = credentialRepository(store)
        val manager = OnlineGoogleAccountManager(
            available = false,
            googleIdTokenProvider = null,
            googleIdentityRepository = null,
            sessionCredentialRepository = credentialRepository,
            nowEpochMillis = { 1_000L },
        )

        assertEquals(
            OnlineGoogleAccountStatus.CONNECTED,
            manager.currentStatus(),
        )
    }

    @Test
    fun cancelled_google_selection_does_not_change_visitor() = runBlocking {
        val original = anonymousCredential()
        val store = ManagerCredentialStore(original)
        val credentialRepository = credentialRepository(store)
        val manager = createManager(
            credentialRepository = credentialRepository,
            tokenProvider = ManagerTokenProvider(
                failure = GoogleCredentialSelectionCancelledException(),
            ),
        )

        val result = manager.connect()

        assertEquals(OnlineGoogleAccountActionResult.Cancelled, result)
        assertEquals(original, store.credential)
    }

    @Test
    fun google_connection_timeout_returns_failure_without_changing_credential() =
        runBlocking {
            val original = anonymousCredential()
            val store = ManagerCredentialStore(original)
            val credentialRepository = credentialRepository(store)
            val manager = createManager(
                credentialRepository = credentialRepository,
                tokenProvider = ManagerDelayedTokenProvider(
                    delayMillis = 5_000L,
                ),
                connectionTimeoutMillis = 50L,
            )

            val result = manager.connect()

            assertEquals(
                OnlineGoogleAccountActionResult.Failure(
                    message =
                        "Não foi possível conectar sua conta agora. Tente novamente.",
                ),
                result,
            )
            assertEquals(original, store.credential)
        }

    @Test
    fun successful_connection_refreshes_status_without_exposing_token() =
        runBlocking {
            val store = ManagerCredentialStore(
                credential = anonymousCredential(),
            )
            val credentialRepository = credentialRepository(store)
            val apiClient = ManagerGoogleApiClient()
            val manager = createManager(
                credentialRepository = credentialRepository,
                tokenProvider = ManagerTokenProvider(
                    token = "private-google-id-token",
                ),
                apiClient = apiClient,
            )

            val result = manager.connect()

            assertEquals(
                OnlineGoogleAccountActionResult.Success(
                    OnlineGoogleAccountStatus.CONNECTED,
                ),
                result,
            )
            assertEquals(
                OnlineGoogleAccountStatus.CONNECTED,
                manager.currentStatus(),
            )
            assertEquals(
                "private-google-id-token",
                apiClient.receivedIdToken,
            )
            assertEquals(OnlineSessionKind.ACCOUNT, store.credential?.sessionKind)
        }

    private fun createManager(
        credentialRepository: OnlineSessionCredentialRepository,
        tokenProvider: GoogleIdTokenProvider = ManagerTokenProvider(),
        apiClient: ManagerGoogleApiClient = ManagerGoogleApiClient(),
        connectionTimeoutMillis: Long = 30_000L,
    ): OnlineGoogleAccountManager {
        return OnlineGoogleAccountManager(
            available = true,
            googleIdTokenProvider = tokenProvider,
            googleIdentityRepository = OnlineGoogleIdentityRepository(
                apiClient = apiClient,
                sessionCredentialRepository = credentialRepository,
            ),
            sessionCredentialRepository = credentialRepository,
            nowEpochMillis = { 1_000L },
            connectionTimeoutMillis = connectionTimeoutMillis,
        )
    }

    private fun credentialRepository(
        store: ManagerCredentialStore,
    ): OnlineSessionCredentialRepository {
        return OnlineSessionCredentialRepository(
            store = store,
            nowEpochMillis = { 1_000L },
            accountRefreshWindowMillis = 100L,
        )
    }

    private fun anonymousCredential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ANONYMOUS,
            playerId = "player-1",
            accessToken = "anonymous-token",
            expiresAtEpochMillis = 2_000L,
        )
    }

    private fun accountCredential(
        expiresAtEpochMillis: Long,
    ): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            accountId = "account-1",
            playerId = "player-1",
            accessToken = "account-token",
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }
}

private class ManagerCredentialStore(
    var credential: OnlineSessionCredential?,
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

private class ManagerTokenProvider(
    private val token: String = "google-id-token",
    private val failure: Throwable? = null,
) : GoogleIdTokenProvider {
    override suspend fun requestIdToken(): String {
        failure?.let { throw it }
        return token
    }
}

private class ManagerDelayedTokenProvider(
    private val delayMillis: Long,
) : GoogleIdTokenProvider {
    override suspend fun requestIdToken(): String {
        kotlinx.coroutines.delay(delayMillis)
        return "google-id-token"
    }
}

private class ManagerGoogleApiClient : RemoteOnlineApiClient {
    var receivedIdToken: String? = null
        private set

    override suspend fun linkGoogleIdentity(
        request: OnlineGoogleIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        receivedIdToken = request.idToken
        return OnlineAccountSessionDto(
            accountId = "account-1",
            playerId = "player-1",
            accessToken = "linked-account-token",
            expiresAtEpochMillis = 3_000L,
        )
    }

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto = error("Not used by this test.")

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto = error("Not used by this test.")

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto = error("Not used by this test.")

    override suspend fun fetchRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto = error("Not used by this test.")

    override suspend fun fetchMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto = error("Not used by this test.")
}
