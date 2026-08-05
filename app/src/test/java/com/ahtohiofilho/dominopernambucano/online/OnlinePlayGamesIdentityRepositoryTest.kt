package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlinePlayGamesIdentityRepositoryTest {
    @Test
    fun connect_links_current_anonymous_player() = runBlocking {
        val anonymous = playGamesAnonymousCredential()
        val store = PlayGamesCredentialStore(anonymous)
        val client = PlayGamesIdentityApiClient()
        val repository = createRepository(store, client)

        var codeRequests = 0
        val connected = repository.connectPlayGamesIdentity(
            requestServerAuthCode = {
                codeRequests += 1
                "server-auth-code"
            },
        )

        assertEquals(OnlineSessionKind.ACCOUNT, connected.sessionKind)
        assertEquals(anonymous.playerId, connected.playerId)
        assertEquals(anonymous.accessToken, client.linkAccessToken)
        assertEquals("server-auth-code", client.linkedCode)
        assertEquals(0, client.recoverCalls)
        assertEquals(1, codeRequests)
    }

    @Test
    fun missing_account_creates_anonymous_session_then_links() =
        runBlocking {
            val store = PlayGamesCredentialStore()
            val client = PlayGamesIdentityApiClient(
                recoverFailure = OnlinePlayGamesIdentityException(
                    OnlinePlayGamesIdentityFailureReason
                        .ACCOUNT_NOT_FOUND,
                ),
            )
            val repository = createRepository(store, client)

            val codes = ArrayDeque(
                listOf(
                    "recovery-server-auth-code",
                    "link-server-auth-code",
                ),
            )
            var codeRequests = 0

            val connected = repository.connectPlayGamesIdentity(
                requestServerAuthCode = {
                    codeRequests += 1
                    codes.removeFirst()
                },
            )

            assertEquals(OnlineSessionKind.ACCOUNT, connected.sessionKind)
            assertEquals(1, client.recoverCalls)
            assertEquals(
                "recovery-server-auth-code",
                client.recoveredCode,
            )
            assertEquals(1, client.createAnonymousCalls)
            assertEquals("anonymous-token", client.linkAccessToken)
            assertEquals("link-server-auth-code", client.linkedCode)
            assertEquals(2, codeRequests)
            assertEquals(0, codes.size)
        }

    @Test
    fun conflict_preserves_existing_credential() {
        val anonymous = playGamesAnonymousCredential()
        val store = PlayGamesCredentialStore(anonymous)
        val repository = createRepository(
            store,
            PlayGamesIdentityApiClient(
                linkFailure = OnlinePlayGamesIdentityException(
                    OnlinePlayGamesIdentityFailureReason
                        .IDENTITY_CONFLICT,
                ),
            ),
        )

        val failure = assertThrows(
            OnlinePlayGamesIdentityException::class.java,
        ) {
            runBlocking {
                repository.linkPlayGamesIdentity("server-auth-code")
            }
        }

        assertEquals(
            OnlinePlayGamesIdentityFailureReason.IDENTITY_CONFLICT,
            failure.reason,
        )
        assertEquals(anonymous, store.credential)
        assertEquals(0, store.writeCalls)
    }

    private fun createRepository(
        store: PlayGamesCredentialStore,
        client: PlayGamesIdentityApiClient,
    ) = OnlinePlayGamesIdentityRepository(
        apiClient = client,
        sessionCredentialRepository =
            OnlineSessionCredentialRepository(
                store = store,
                nowEpochMillis = { 1_000L },
                accountRefreshWindowMillis = 100L,
            ),
    )
}

private fun playGamesAnonymousCredential() =
    OnlineSessionCredential(
        sessionKind = OnlineSessionKind.ANONYMOUS,
        playerId = "player-1",
        accessToken = "anonymous-token",
        expiresAtEpochMillis = 2_000L,
    )

private class PlayGamesCredentialStore(
    initial: OnlineSessionCredential? = null,
) : OnlineSessionCredentialStore {
    var credential = initial
        private set
    var writeCalls = 0
        private set

    override fun read(): OnlineSessionCredential? = credential

    override fun write(
        credential: OnlineSessionCredential,
    ): Boolean {
        writeCalls += 1
        this.credential = credential
        return true
    }

    override fun clear(): Boolean {
        credential = null
        return true
    }
}

private class PlayGamesIdentityApiClient(
    private val linkFailure: Throwable? = null,
    private val recoverFailure: Throwable? = null,
) : RemoteOnlineApiClient {
    var linkedCode: String? = null
        private set
    var linkAccessToken: String? = null
        private set
    var recoverCalls = 0
        private set
    var recoveredCode: String? = null
        private set
    var createAnonymousCalls = 0
        private set

    override suspend fun createAnonymousSession():
        OnlineAnonymousSessionDto {
        createAnonymousCalls += 1
        return OnlineAnonymousSessionDto(
            playerId = "player-1",
            accessToken = "anonymous-token",
            expiresAtEpochMillis = 2_000L,
        )
    }

    override suspend fun linkPlayGamesIdentity(
        request: OnlinePlayGamesIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        linkedCode = request.serverAuthCode
        linkAccessToken = accessToken
        linkFailure?.let { throw it }
        return accountSession()
    }

    override suspend fun recoverPlayGamesAccount(
        request: OnlinePlayGamesIdentityRequestDto,
    ): OnlineAccountSessionDto {
        recoverCalls += 1
        recoveredCode = request.serverAuthCode
        recoverFailure?.let { throw it }
        return accountSession()
    }

    private fun accountSession() = OnlineAccountSessionDto(
        accountId = "account-1",
        playerId = "player-1",
        accessToken = "account-token",
        expiresAtEpochMillis = 3_000L,
    )

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
