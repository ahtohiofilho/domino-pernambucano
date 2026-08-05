package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlinePlayGamesAccountManagerTest {
    @Test
    fun status_merges_provider_availability_without_losing_account_state() {
        assertEquals(
            OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
            mergeOnlineAccountStatuses(
                OnlineGoogleAccountStatus.UNAVAILABLE,
                OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
            ),
        )
        assertEquals(
            OnlineGoogleAccountStatus.CONNECTED,
            mergeOnlineAccountStatuses(
                OnlineGoogleAccountStatus.RECOVERY_REQUIRED,
                OnlineGoogleAccountStatus.CONNECTED,
            ),
        )
    }

    @Test
    fun cancelled_play_games_sign_in_keeps_visitor_session() = runBlocking {
        val original = managerAnonymousCredential()
        val store = PlayGamesManagerCredentialStore(original)
        val manager = createManager(
            store = store,
            provider = PlayGamesServerAuthCodeProvider {
                throw PlayGamesSignInCancelledException()
            },
        )

        assertEquals(
            OnlinePlayGamesAccountActionResult.Cancelled,
            manager.connect(),
        )
        assertEquals(original, store.credential)
    }

    @Test
    fun successful_connection_uses_server_code_and_updates_status() =
        runBlocking {
            val store = PlayGamesManagerCredentialStore(
                managerAnonymousCredential(),
            )
            val apiClient = PlayGamesManagerApiClient()
            val manager = createManager(
                store = store,
                provider = PlayGamesServerAuthCodeProvider {
                    "private-server-auth-code"
                },
                apiClient = apiClient,
            )

            assertEquals(
                OnlinePlayGamesAccountActionResult.Success(
                    OnlineGoogleAccountStatus.CONNECTED,
                ),
                manager.connect(),
            )
            assertEquals(
                "private-server-auth-code",
                apiClient.receivedServerAuthCode,
            )
            assertEquals(
                OnlineSessionKind.ACCOUNT,
                store.credential?.sessionKind,
            )
        }

    private fun createManager(
        store: PlayGamesManagerCredentialStore,
        provider: PlayGamesServerAuthCodeProvider,
        apiClient: PlayGamesManagerApiClient =
            PlayGamesManagerApiClient(),
    ): OnlinePlayGamesAccountManager {
        val credentialRepository =
            OnlineSessionCredentialRepository(
                store = store,
                nowEpochMillis = { 1_000L },
                accountRefreshWindowMillis = 100L,
            )

        return OnlinePlayGamesAccountManager(
            available = true,
            serverAuthCodeProvider = provider,
            playGamesIdentityRepository =
                OnlinePlayGamesIdentityRepository(
                    apiClient = apiClient,
                    sessionCredentialRepository =
                        credentialRepository,
                ),
            sessionCredentialRepository = credentialRepository,
            nowEpochMillis = { 1_000L },
        )
    }
}

private fun managerAnonymousCredential() = OnlineSessionCredential(
    sessionKind = OnlineSessionKind.ANONYMOUS,
    playerId = "player-1",
    accessToken = "anonymous-token",
    expiresAtEpochMillis = 2_000L,
)

private class PlayGamesManagerCredentialStore(
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

private class PlayGamesManagerApiClient : RemoteOnlineApiClient {
    var receivedServerAuthCode: String? = null
        private set

    override suspend fun linkPlayGamesIdentity(
        request: OnlinePlayGamesIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        receivedServerAuthCode = request.serverAuthCode
        return OnlineAccountSessionDto(
            accountId = "account-1",
            playerId = "player-1",
            accessToken = "linked-account-token",
            expiresAtEpochMillis = 3_000L,
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
