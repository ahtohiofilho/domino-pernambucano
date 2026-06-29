package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineAnonymousSessionRepositoryTest {
    @Test
    fun reuses_persisted_session_and_configures_api_client() =
        runBlocking {
            val storedSession = OnlineAnonymousSessionDto(
                playerId = "anonymous-persisted",
                accessToken = "persisted-token",
                expiresAtEpochMillis = 200_000L,
            )
            val apiClient = RecordingRemoteOnlineApiClient(
                issuedSession = OnlineAnonymousSessionDto(
                    playerId = "anonymous-issued",
                    accessToken = "issued-token",
                    expiresAtEpochMillis = 300_000L,
                ),
            )
            val repository = OnlineAnonymousSessionRepository(
                apiClient = apiClient,
                store = InMemoryOnlineAnonymousSessionStore(
                    initialSession = storedSession,
                ),
                nowEpochMillis = {
                    100_000L
                },
            )

            val resolvedSession = repository.getOrCreate()

            assertEquals(
                storedSession,
                resolvedSession,
            )
            assertEquals(
                0,
                apiClient.createSessionCallCount,
            )
            assertEquals(
                storedSession,
                apiClient.lastConfiguredSession,
            )
        }

    @Test
    fun replaces_expiring_session_and_persists_the_new_credential() =
        runBlocking {
            val issuedSession = OnlineAnonymousSessionDto(
                playerId = "anonymous-issued",
                accessToken = "issued-token",
                expiresAtEpochMillis = 300_000L,
            )
            val store = InMemoryOnlineAnonymousSessionStore(
                initialSession = OnlineAnonymousSessionDto(
                    playerId = "anonymous-expiring",
                    accessToken = "expiring-token",
                    expiresAtEpochMillis = 150_000L,
                ),
            )
            val apiClient = RecordingRemoteOnlineApiClient(
                issuedSession = issuedSession,
            )
            val repository = OnlineAnonymousSessionRepository(
                apiClient = apiClient,
                store = store,
                nowEpochMillis = {
                    100_000L
                },
            )

            val resolvedSession = repository.getOrCreate()

            assertEquals(
                issuedSession,
                resolvedSession,
            )
            assertEquals(
                1,
                apiClient.createSessionCallCount,
            )
            assertEquals(
                issuedSession,
                store.read(),
            )
            assertEquals(
                issuedSession,
                apiClient.lastConfiguredSession,
            )
        }

    private class RecordingRemoteOnlineApiClient(
        private val issuedSession: OnlineAnonymousSessionDto,
    ) : RemoteOnlineApiClient {
        var createSessionCallCount = 0
            private set

        var lastConfiguredSession: OnlineAnonymousSessionDto? = null
            private set

        override fun setAnonymousSession(
            session: OnlineAnonymousSessionDto?,
        ) {
            lastConfiguredSession = session
        }

        override suspend fun createAnonymousSession(): OnlineAnonymousSessionDto {
            createSessionCallCount += 1
            return issuedSession
        }

        override suspend fun createRoom(
            request: CreateOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto {
            error("Não utilizado neste teste.")
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
}
