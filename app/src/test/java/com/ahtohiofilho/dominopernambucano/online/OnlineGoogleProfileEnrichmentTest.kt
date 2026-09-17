package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineGoogleProfileEnrichmentTest {
    @Test
    fun matching_account_resolves_persisted_google_photo() {
        val enrichment = OnlineProfileEnrichment(
            accountId = "account-1",
            playerId = "player-1",
            googleProfilePhotoUri = " https://example.test/photo.jpg ",
        )

        assertEquals(
            "https://example.test/photo.jpg",
            enrichment.googleProfilePhotoUriFor(
                accountCredential(
                    accountId = "account-1",
                    playerId = "player-1",
                ),
            ),
        )
    }

    @Test
    fun different_account_never_receives_stale_google_photo() {
        val enrichment = OnlineProfileEnrichment(
            accountId = "account-1",
            playerId = "player-1",
            googleProfilePhotoUri = "https://example.test/photo.jpg",
        )

        assertNull(
            enrichment.googleProfilePhotoUriFor(
                accountCredential(
                    accountId = "account-2",
                    playerId = "player-2",
                ),
            ),
        )
    }

    @Test
    fun successful_google_connection_persists_photo_without_changing_auth_payload() =
        runBlocking {
            val credentialStore = EnrichmentCredentialStore(
                anonymousCredential(),
            )
            val credentialRepository =
                OnlineSessionCredentialRepository(
                    store = credentialStore,
                    nowEpochMillis = { 1_000L },
                )
            val apiClient = EnrichmentGoogleApiClient()
            val profileStore = RecordingProfileEnrichmentStore()
            val provider = EnrichmentGoogleTokenProvider(
                credential = GoogleIdentityCredential(
                    idToken = "private-google-id-token",
                    profilePictureUri =
                        "https://example.test/google-photo.jpg",
                ),
            )

            val manager = OnlineGoogleAccountManager(
                available = true,
                googleIdTokenProvider = provider,
                googleIdentityRepository =
                    OnlineGoogleIdentityRepository(
                        apiClient = apiClient,
                        sessionCredentialRepository =
                            credentialRepository,
                    ),
                sessionCredentialRepository =
                    credentialRepository,
                profileEnrichmentStore = profileStore,
                nowEpochMillis = { 1_000L },
            )

            assertEquals(
                OnlineGoogleAccountActionResult.Success(
                    OnlineGoogleAccountStatus.CONNECTED,
                ),
                manager.connect(),
            )

            assertEquals(
                "private-google-id-token",
                apiClient.receivedIdToken,
            )
            assertEquals(
                OnlineProfileEnrichment(
                    accountId = "account-1",
                    playerId = "player-1",
                    googleProfilePhotoUri =
                        "https://example.test/google-photo.jpg",
                ),
                profileStore.value,
            )
        }

    @Test
    fun local_disconnect_hides_cached_google_photo_without_deleting_enrichment() {
        val credentialStore = EnrichmentCredentialStore(
            accountCredential(
                accountId = "account-1",
                playerId = "player-1",
            ),
        )
        val credentialRepository =
            OnlineSessionCredentialRepository(
                store = credentialStore,
                nowEpochMillis = { 1_000L },
            )
        val enrichment = OnlineProfileEnrichment(
            accountId = "account-1",
            playerId = "player-1",
            googleProfilePhotoUri =
                "https://example.test/google-photo.jpg",
        )

        assertEquals(true, credentialRepository.clear())
        assertNull(credentialRepository.getStoredCredentialOrNull())
        assertNull(
            enrichment.googleProfilePhotoUriFor(
                credentialRepository.getStoredCredentialOrNull(),
            ),
        )
        assertEquals(
            "https://example.test/google-photo.jpg",
            enrichment.googleProfilePhotoUri,
        )
    }

    @Test
    fun profile_store_failure_does_not_downgrade_successful_authentication() =
        runBlocking {
            val credentialStore = EnrichmentCredentialStore(
                anonymousCredential(),
            )
            val credentialRepository =
                OnlineSessionCredentialRepository(
                    store = credentialStore,
                    nowEpochMillis = { 1_000L },
                )
            val apiClient = EnrichmentGoogleApiClient()

            val manager = OnlineGoogleAccountManager(
                available = true,
                googleIdTokenProvider =
                    EnrichmentGoogleTokenProvider(
                        credential = GoogleIdentityCredential(
                            idToken = "google-id-token",
                            profilePictureUri =
                                "https://example.test/photo.jpg",
                        ),
                    ),
                googleIdentityRepository =
                    OnlineGoogleIdentityRepository(
                        apiClient = apiClient,
                        sessionCredentialRepository =
                            credentialRepository,
                    ),
                sessionCredentialRepository =
                    credentialRepository,
                profileEnrichmentStore =
                    ThrowingProfileEnrichmentStore(),
                nowEpochMillis = { 1_000L },
            )

            assertEquals(
                OnlineGoogleAccountActionResult.Success(
                    OnlineGoogleAccountStatus.CONNECTED,
                ),
                manager.connect(),
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
        accountId: String,
        playerId: String,
    ): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            accountId = accountId,
            playerId = playerId,
            accessToken = "account-token",
            expiresAtEpochMillis = 3_000L,
        )
    }
}

private class EnrichmentCredentialStore(
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

private class EnrichmentGoogleTokenProvider(
    private val credential: GoogleIdentityCredential,
) : GoogleIdTokenProvider {
    override suspend fun requestIdToken(): String {
        return credential.idToken
    }

    override suspend fun requestIdentityCredential():
        GoogleIdentityCredential {
        return credential
    }
}

private class RecordingProfileEnrichmentStore :
    OnlineProfileEnrichmentStore {
    var value: OnlineProfileEnrichment =
        OnlineProfileEnrichment.Empty
        private set

    override fun read(): OnlineProfileEnrichment = value

    override fun writeGoogleProfile(
        accountId: String,
        playerId: String,
        profilePhotoUri: String?,
    ): OnlineProfileEnrichment {
        return OnlineProfileEnrichment(
            accountId = accountId,
            playerId = playerId,
            googleProfilePhotoUri =
                normalizeOnlineProfilePhotoUri(profilePhotoUri),
        ).also { stored ->
            value = stored
        }
    }
}

private class ThrowingProfileEnrichmentStore :
    OnlineProfileEnrichmentStore {
    override fun read(): OnlineProfileEnrichment {
        return OnlineProfileEnrichment.Empty
    }

    override fun writeGoogleProfile(
        accountId: String,
        playerId: String,
        profilePhotoUri: String?,
    ): OnlineProfileEnrichment {
        error("Simulated local profile-enrichment failure.")
    }
}

private class EnrichmentGoogleApiClient : RemoteOnlineApiClient {
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
    ): OnlineRoomOperationResultDto =
        error("Not used by this test.")

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto =
        error("Not used by this test.")

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto =
        error("Not used by this test.")

    override suspend fun fetchRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto =
        error("Not used by this test.")

    override suspend fun fetchMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto =
        error("Not used by this test.")
}
