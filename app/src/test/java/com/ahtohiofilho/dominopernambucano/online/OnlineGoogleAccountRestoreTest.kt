package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineGoogleAccountRestoreTest {
    @Test
    fun expired_saved_account_is_restored_with_authorized_google_account() =
        runBlocking {
            val store = RestoreCredentialStore(
                credential = expiredAccountCredential(),
            )
            val repository = OnlineSessionCredentialRepository(
                store = store,
                nowEpochMillis = { 1_000L },
            )
            val apiClient = RestoreGoogleApiClient()
            val manager = OnlineGoogleAccountManager(
                available = true,
                googleIdTokenProvider = RestoreTokenProvider(
                    authorizedToken = "authorized-id-token",
                ),
                googleIdentityRepository = OnlineGoogleIdentityRepository(
                    apiClient = apiClient,
                    sessionCredentialRepository = repository,
                ),
                sessionCredentialRepository = repository,
                nowEpochMillis = { 1_000L },
            )

            assertTrue(manager.restoreAuthorizedAccount())
            assertEquals(
                OnlineGoogleAccountStatus.CONNECTED,
                manager.currentStatus(),
            )
            assertEquals(
                "authorized-id-token",
                apiClient.receivedIdToken,
            )
        }

    @Test
    fun unavailable_authorized_google_account_falls_back_without_losing_saved_account() =
        runBlocking {
            val original = expiredAccountCredential()
            val store = RestoreCredentialStore(original)
            val repository = OnlineSessionCredentialRepository(
                store = store,
                nowEpochMillis = { 1_000L },
            )
            val manager = OnlineGoogleAccountManager(
                available = true,
                googleIdTokenProvider = RestoreTokenProvider(
                    authorizedToken = null,
                ),
                googleIdentityRepository = OnlineGoogleIdentityRepository(
                    apiClient = RestoreGoogleApiClient(),
                    sessionCredentialRepository = repository,
                ),
                sessionCredentialRepository = repository,
                nowEpochMillis = { 1_000L },
            )

            assertFalse(manager.restoreAuthorizedAccount())
            assertEquals(original, store.credential)
            assertEquals(
                OnlineGoogleAccountStatus.RECOVERY_REQUIRED,
                manager.currentStatus(),
            )
        }

    private fun expiredAccountCredential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            accountId = "account-1",
            playerId = "player-1",
            accessToken = "expired-account-token",
            expiresAtEpochMillis = 900L,
        )
    }
}

private class RestoreCredentialStore(
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

private class RestoreTokenProvider(
    private val authorizedToken: String?,
) : GoogleIdTokenProvider {
    override suspend fun requestIdToken(): String {
        error("Explicit Google selection is not used by this test.")
    }

    override suspend fun requestAuthorizedIdTokenOrNull(): String? {
        return authorizedToken
    }
}

private class RestoreGoogleApiClient : RemoteOnlineApiClient {
    var receivedIdToken: String? = null
        private set

    override suspend fun recoverGoogleAccount(
        request: OnlineGoogleIdentityRequestDto,
    ): OnlineAccountSessionDto {
        receivedIdToken = request.idToken
        return OnlineAccountSessionDto(
            accountId = "account-1",
            playerId = "player-1",
            accessToken = "restored-account-token",
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
