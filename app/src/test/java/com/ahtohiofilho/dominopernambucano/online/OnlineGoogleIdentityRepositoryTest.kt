package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineGoogleIdentityRepositoryTest {
    @Test
    fun link_preserves_player_and_pending_participation() = runBlocking {
        val anonymous = anonymousCredential()
        val credentialStore = FakeGoogleCredentialStore(anonymous)
        val apiClient = FakeGoogleIdentityApiClient()
        val repository = createRepository(
            store = credentialStore,
            apiClient = apiClient,
        )
        val bindingStore = FakeGoogleParticipationBindingStore()
        val bindingRepository = OnlineParticipationBindingRepository(
            store = bindingStore,
        )
        val pendingBinding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = anonymous.playerId,
            localSeatIndex = 2,
        )
        bindingRepository.save(pendingBinding)

        val linked = repository.linkGoogleIdentity(
            idToken = " google-id-token ",
        )

        assertEquals(OnlineSessionKind.ACCOUNT, linked.sessionKind)
        assertEquals(anonymous.playerId, linked.playerId)
        assertEquals("account-1", linked.accountId)
        assertEquals(linked, credentialStore.credential)
        assertEquals("google-id-token", apiClient.linkedIdToken)
        assertEquals(anonymous.accessToken, apiClient.linkAccessToken)
        assertEquals(
            pendingBinding,
            bindingRepository.getValidBindingOrNull(),
        )
    }

    @Test
    fun conflict_keeps_credential_and_pending_participation() {
        val anonymous = anonymousCredential()
        val credentialStore = FakeGoogleCredentialStore(anonymous)
        val apiClient = FakeGoogleIdentityApiClient(
            linkFailure = OnlineGoogleIdentityException(
                OnlineGoogleIdentityFailureReason.IDENTITY_CONFLICT,
            ),
        )
        val repository = createRepository(
            store = credentialStore,
            apiClient = apiClient,
        )
        val bindingStore = FakeGoogleParticipationBindingStore(
            OnlineParticipationBinding(
                roomId = "room-1",
                playerId = anonymous.playerId,
                localSeatIndex = 0,
            ),
        )

        val failure = assertThrows(
            OnlineGoogleIdentityException::class.java,
        ) {
            runBlocking {
                repository.linkGoogleIdentity("google-id-token")
            }
        }

        assertEquals(
            OnlineGoogleIdentityFailureReason.IDENTITY_CONFLICT,
            failure.reason,
        )
        assertEquals(anonymous, credentialStore.credential)
        assertEquals(0, credentialStore.writeCallCount)
        assertEquals(
            "room-1",
            bindingStore.binding?.roomId,
        )
    }

    @Test
    fun expired_account_recovery_is_atomic_and_preserves_identity() =
        runBlocking {
            val expired = accountCredential(
                accessToken = "expired-account-token",
                expiresAtEpochMillis = 900L,
            )
            val credentialStore = FakeGoogleCredentialStore(expired)
            val apiClient = FakeGoogleIdentityApiClient(
                recoveredSession = accountSession(
                    accessToken = "recovered-account-token",
                    expiresAtEpochMillis = 3_000L,
                ),
            )
            val repository = createRepository(
                store = credentialStore,
                apiClient = apiClient,
            )

            val recovered = repository.recoverGoogleAccount(
                idToken = "google-id-token",
            )

            assertEquals(expired.playerId, recovered.playerId)
            assertEquals(expired.accountId, recovered.accountId)
            assertEquals(
                "recovered-account-token",
                recovered.accessToken,
            )
            assertEquals(1, credentialStore.writeCallCount)
            assertEquals(recovered, credentialStore.credential)
        }

    @Test
    fun recovery_identity_mismatch_keeps_expired_account() {
        val expired = accountCredential(
            expiresAtEpochMillis = 900L,
        )
        val credentialStore = FakeGoogleCredentialStore(expired)
        val apiClient = FakeGoogleIdentityApiClient(
            recoveredSession = accountSession(
                playerId = "other-player",
                expiresAtEpochMillis = 3_000L,
            ),
        )
        val repository = createRepository(
            store = credentialStore,
            apiClient = apiClient,
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.recoverGoogleAccount("google-id-token")
            }
        }

        assertEquals(expired, credentialStore.credential)
        assertEquals(0, credentialStore.writeCallCount)
    }

    @Test
    fun recovery_persistence_failure_keeps_expired_account() {
        val expired = accountCredential(
            expiresAtEpochMillis = 900L,
        )
        val credentialStore = FakeGoogleCredentialStore(
            initialCredential = expired,
            writeSucceeds = false,
        )
        val repository = createRepository(
            store = credentialStore,
            apiClient = FakeGoogleIdentityApiClient(
                recoveredSession = accountSession(
                    accessToken = "recovered-account-token",
                    expiresAtEpochMillis = 3_000L,
                ),
            ),
        )

        assertThrows(
            OnlineSessionCredentialPersistenceException::class.java,
        ) {
            runBlocking {
                repository.recoverGoogleAccount("google-id-token")
            }
        }

        assertEquals(expired, credentialStore.credential)
        assertEquals(1, credentialStore.writeCallCount)
    }

    @Test
    fun recovery_never_replaces_anonymous_session() {
        val anonymous = anonymousCredential()
        val credentialStore = FakeGoogleCredentialStore(anonymous)
        val apiClient = FakeGoogleIdentityApiClient()
        val repository = createRepository(
            store = credentialStore,
            apiClient = apiClient,
        )

        assertThrows(
            OnlineAccountRecoveryBlockedByAnonymousSessionException::class.java,
        ) {
            runBlocking {
                repository.recoverGoogleAccount("google-id-token")
            }
        }

        assertEquals(anonymous, credentialStore.credential)
        assertEquals(0, credentialStore.writeCallCount)
        assertEquals(0, apiClient.recoverCallCount)
    }

    private fun createRepository(
        store: OnlineSessionCredentialStore,
        apiClient: RemoteOnlineApiClient,
    ): OnlineGoogleIdentityRepository {
        return OnlineGoogleIdentityRepository(
            apiClient = apiClient,
            sessionCredentialRepository =
                OnlineSessionCredentialRepository(
                    store = store,
                    nowEpochMillis = { 1_000L },
                    accountRefreshWindowMillis = 100L,
                ),
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
        accessToken: String = "account-token",
        expiresAtEpochMillis: Long = 2_000L,
    ): OnlineSessionCredential {
        return accountSession(
            accessToken = accessToken,
            expiresAtEpochMillis = expiresAtEpochMillis,
        ).toOnlineSessionCredential()
    }

    private fun accountSession(
        playerId: String = "player-1",
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

private class FakeGoogleCredentialStore(
    initialCredential: OnlineSessionCredential? = null,
    private val writeSucceeds: Boolean = true,
) : OnlineSessionCredentialStore {
    var credential: OnlineSessionCredential? = initialCredential
        private set
    var writeCallCount: Int = 0
        private set

    override fun read(): OnlineSessionCredential? = credential

    override fun write(
        credential: OnlineSessionCredential,
    ): Boolean {
        writeCallCount += 1
        if (writeSucceeds) {
            this.credential = credential
        }
        return writeSucceeds
    }

    override fun clear(): Boolean {
        credential = null
        return true
    }
}

private class FakeGoogleParticipationBindingStore(
    initialBinding: OnlineParticipationBinding? = null,
) : OnlineParticipationBindingStore {
    var binding: OnlineParticipationBinding? = initialBinding
        private set

    override fun read(): OnlineParticipationBinding? = binding

    override fun write(
        binding: OnlineParticipationBinding,
    ) {
        this.binding = binding
    }

    override fun clear() {
        binding = null
    }
}

private class FakeGoogleIdentityApiClient(
    private val linkedSession: OnlineAccountSessionDto =
        OnlineAccountSessionDto(
            accountId = "account-1",
            playerId = "player-1",
            accessToken = "linked-account-token",
            expiresAtEpochMillis = 3_000L,
        ),
    private val recoveredSession: OnlineAccountSessionDto = linkedSession,
    private val linkFailure: Throwable? = null,
) : RemoteOnlineApiClient {
    var linkedIdToken: String? = null
        private set
    var linkAccessToken: String? = null
        private set
    var recoverCallCount: Int = 0
        private set

    override suspend fun linkGoogleIdentity(
        request: OnlineGoogleIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        linkedIdToken = request.idToken
        linkAccessToken = accessToken
        linkFailure?.let { failure ->
            throw failure
        }
        return linkedSession
    }

    override suspend fun recoverGoogleAccount(
        request: OnlineGoogleIdentityRequestDto,
    ): OnlineAccountSessionDto {
        recoverCallCount += 1
        return recoveredSession
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
