package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineEmailIdentityRepositoryTest {
    @Test
    fun request_normalizes_email_and_keeps_generic_acceptance() =
        runBlocking {
            val store = EmailCredentialStore()
            val apiClient = EmailRepositoryApiClient()
            val repository = createRepository(store, apiClient)

            val normalized = repository.requestVerificationCode(
                "  Player@Example.COM ",
            )

            assertEquals("player@example.com", normalized)
            assertEquals(
                "player@example.com",
                apiClient.requestedEmail,
            )
        }

    @Test
    fun link_uses_current_credential_and_preserves_player() =
        runBlocking {
            val anonymous = anonymousCredential()
            val store = EmailCredentialStore(anonymous)
            val apiClient = EmailRepositoryApiClient()
            val repository = createRepository(store, apiClient)

            val linked = repository.linkEmailIdentity(
                rawEmail = "Player@Example.com",
                rawCode = " 123456 ",
            )

            assertEquals(OnlineSessionKind.ACCOUNT, linked.sessionKind)
            assertEquals(anonymous.playerId, linked.playerId)
            assertEquals(
                anonymous.accessToken,
                apiClient.linkAccessToken,
            )
            assertEquals(
                OnlineEmailIdentityRequestDto(
                    email = "player@example.com",
                    code = "123456",
                ),
                apiClient.linkRequest,
            )
        }

    @Test
    fun recovery_uses_code_once_and_persists_existing_account() =
        runBlocking {
            val store = EmailCredentialStore()
            val apiClient = EmailRepositoryApiClient()
            val repository = createRepository(store, apiClient)

            val recovered = repository.recoverEmailAccount(
                rawEmail = "player@example.com",
                rawCode = "654321",
            )

            assertEquals(OnlineSessionKind.ACCOUNT, recovered.sessionKind)
            assertEquals(
                OnlineEmailIdentityRequestDto(
                    email = "player@example.com",
                    code = "654321",
                ),
                apiClient.recoverRequest,
            )
            assertEquals(recovered, store.credential)
        }

    @Test
    fun invalid_email_or_code_is_rejected_before_network() {
        val store = EmailCredentialStore(
            anonymousCredential(),
        )
        val apiClient = EmailRepositoryApiClient()
        val repository = createRepository(store, apiClient)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.requestVerificationCode("invalid")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.linkEmailIdentity(
                    rawEmail = "player@example.com",
                    rawCode = "12",
                )
            }
        }

        assertEquals(0, apiClient.requestCodeCallCount)
        assertEquals(0, apiClient.linkCallCount)
    }

    private fun createRepository(
        store: OnlineSessionCredentialStore,
        apiClient: RemoteOnlineApiClient,
    ): OnlineEmailIdentityRepository {
        return OnlineEmailIdentityRepository(
            apiClient = apiClient,
            sessionCredentialRepository =
                OnlineSessionCredentialRepository(
                    store = store,
                    nowEpochMillis = { 1_000L },
                    accountRefreshWindowMillis = 100L,
                ),
        )
    }

    private fun anonymousCredential() =
        OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ANONYMOUS,
            playerId = "player-1",
            accessToken = "anonymous-token",
            expiresAtEpochMillis = 2_000L,
        )
}

private class EmailCredentialStore(
    initialCredential: OnlineSessionCredential? = null,
) : OnlineSessionCredentialStore {
    var credential = initialCredential
        private set

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

private class EmailRepositoryApiClient : RemoteOnlineApiClient {
    var requestedEmail: String? = null
        private set
    var requestCodeCallCount = 0
        private set
    var linkCallCount = 0
        private set
    var linkRequest: OnlineEmailIdentityRequestDto? = null
        private set
    var linkAccessToken: String? = null
        private set
    var recoverRequest: OnlineEmailIdentityRequestDto? = null
        private set

    override suspend fun requestEmailCode(
        request: OnlineEmailCodeRequestDto,
    ): OnlineEmailCodeRequestResponseDto {
        requestCodeCallCount += 1
        requestedEmail = request.email
        return OnlineEmailCodeRequestResponseDto()
    }

    override suspend fun linkEmailIdentity(
        request: OnlineEmailIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        linkCallCount += 1
        linkRequest = request
        linkAccessToken = accessToken
        return accountSession()
    }

    override suspend fun recoverEmailAccount(
        request: OnlineEmailIdentityRequestDto,
    ): OnlineAccountSessionDto {
        recoverRequest = request
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
