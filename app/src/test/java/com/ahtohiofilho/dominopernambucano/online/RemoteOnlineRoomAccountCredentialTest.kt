package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteOnlineRoomAccountCredentialTest {
    @Test
    fun valid_account_is_used_for_room_request_without_new_anonymous_session() =
        runBlocking {
            val account = accountCredential(expiresAtEpochMillis = 3_000L)
            val store = AccountTestCredentialStore(account)
            val apiClient = AccountTestRemoteApiClient()
            val repository = createRepository(store, apiClient)

            val result = repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "local-installation-player",
                    playerName = "Jogador",
                ),
            )

            assertTrue(result.accepted)
            assertEquals(0, apiClient.createAnonymousCallCount)
            assertEquals(1, apiClient.createRoomRequests.size)
            assertEquals(
                account.playerId,
                apiClient.createRoomRequests.single().localPlayerId,
            )
            assertEquals(
                account.accessToken,
                apiClient.bearerAccessTokenUpdates.last(),
            )
        }

    @Test
    fun expired_account_blocks_room_request_without_creating_new_player() =
        runBlocking {
            val expiredAccount = accountCredential(
                expiresAtEpochMillis = 1_000L,
            )
            val store = AccountTestCredentialStore(expiredAccount)
            val apiClient = AccountTestRemoteApiClient()
            val repository = createRepository(store, apiClient)

            val result = repository.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "local-installation-player",
                    playerName = "Jogador",
                ),
            )

            assertFalse(result.accepted)
            assertEquals(0, apiClient.createAnonymousCallCount)
            assertEquals(0, apiClient.createRoomRequests.size)
            assertEquals(expiredAccount, store.storedCredential)
        }

    private fun createRepository(
        store: AccountTestCredentialStore,
        apiClient: AccountTestRemoteApiClient,
    ): RemoteOnlineRoomRepository {
        return RemoteOnlineRoomRepository(
            config = OnlineBackendConfig.remote(
                baseUrl = "http://localhost:8080",
            ),
            apiClient = apiClient,
            pollingPolicy = OnlineRemotePollingPolicy.Disabled,
            coroutineDispatcher = Dispatchers.Unconfined,
            nowEpochMillis = { 1_000L },
            sessionCredentialRepository =
                OnlineSessionCredentialRepository(
                    store = store,
                    nowEpochMillis = { 1_000L },
                    accountRefreshWindowMillis = 0L,
                ),
        )
    }

    private fun accountCredential(
        expiresAtEpochMillis: Long,
    ): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            accountId = "account-1",
            playerId = "anonymous-player-1",
            accessToken = "account-token",
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }
}

private class AccountTestCredentialStore(
    initialCredential: OnlineSessionCredential,
) : OnlineSessionCredentialStore {
    var storedCredential: OnlineSessionCredential? = initialCredential
        private set

    override fun read(): OnlineSessionCredential? = storedCredential

    override fun write(
        credential: OnlineSessionCredential,
    ): Boolean {
        storedCredential = credential
        return true
    }

    override fun clear(): Boolean {
        storedCredential = null
        return true
    }
}

private class AccountTestRemoteApiClient : RemoteOnlineApiClient {
    val createRoomRequests = mutableListOf<CreateOnlineRoomRequestDto>()
    val bearerAccessTokenUpdates = mutableListOf<String?>()
    var createAnonymousCallCount = 0
        private set

    override fun setBearerAccessToken(accessToken: String?) {
        bearerAccessTokenUpdates += accessToken
    }

    override suspend fun createAnonymousSession():
        OnlineAnonymousSessionDto {
        createAnonymousCallCount += 1
        error("Uma conta persistente não deve criar sessão anônima.")
    }

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        createRoomRequests += request
        return OnlineRoomOperationResultDto(accepted = true)
    }

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        error("Não utilizado neste teste.")
    }

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        error("Não utilizado neste teste.")
    }

    override suspend fun fetchRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto {
        error("Não utilizado neste teste.")
    }

    override suspend fun fetchMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto {
        error("Não utilizado neste teste.")
    }
}
