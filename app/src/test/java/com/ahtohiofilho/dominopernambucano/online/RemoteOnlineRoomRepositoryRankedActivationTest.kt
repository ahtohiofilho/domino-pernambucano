package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteOnlineRoomRepositoryRankedActivationTest {
    @Test
    fun account_match_is_validated_activated_and_persisted_without_code_input() =
        runBlocking {
            val match = createMatchSnapshot()
            val room = createRoomSnapshot(
                roomId = match.roomId,
                matchId = match.matchId,
                playerId = "player-1",
                localSeatIndex = 2,
            )
            val api = FakeRemoteApi(
                room = room,
                match = match,
            )
            val credentialStore = InMemoryCredentialStore(
                credential = accountCredential(),
            )
            val bindingStore = InMemoryBindingStore()
            val repository = RemoteOnlineRoomRepository(
                config = OnlineBackendConfig.remote(
                    baseUrl = "http://localhost:8080",
                ),
                apiClient = api,
                pollingPolicy = OnlineRemotePollingPolicy.Disabled,
                coroutineDispatcher = Dispatchers.Unconfined,
                sessionCredentialRepository =
                    OnlineSessionCredentialRepository(
                        store = credentialStore,
                        nowEpochMillis = { 1_000L },
                    ),
                onlineParticipationBindingRepository =
                    OnlineParticipationBindingRepository(
                        store = bindingStore,
                    ),
            )

            val result = repository.activatePublicRankedMatch(
                matchId = match.matchId,
                localSeatIndex = 2,
            )

            assertTrue(
                result is OnlinePublicRankedMatchActivation.Ready,
            )
            assertEquals(match, repository.matchSnapshot.value)
            assertEquals(room, repository.roomSnapshot.value)
            assertEquals(listOf(match.matchId), api.matchRequests)
            assertEquals(listOf(match.roomId), api.roomRequests)
            assertEquals(
                OnlineParticipationBinding(
                    roomId = match.roomId,
                    matchId = match.matchId,
                    playerId = "player-1",
                    localSeatIndex = 2,
                ),
                bindingStore.binding,
            )
            assertEquals("account-token", api.bearerToken)
            assertEquals(null, api.observedDevelopmentPlayerId)
        }

    @Test
    fun visitor_is_rejected_before_ranked_snapshot_fetch() = runBlocking {
        val api = FakeRemoteApi(
            room = createRoomSnapshot(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "visitor-1",
                localSeatIndex = 0,
            ),
            match = createMatchSnapshot(),
        )
        val repository = RemoteOnlineRoomRepository(
            config = OnlineBackendConfig.remote(
                baseUrl = "http://localhost:8080",
            ),
            apiClient = api,
            pollingPolicy = OnlineRemotePollingPolicy.Disabled,
            coroutineDispatcher = Dispatchers.Unconfined,
            sessionCredentialRepository =
                OnlineSessionCredentialRepository(
                    store = InMemoryCredentialStore(
                        credential = OnlineSessionCredential(
                            sessionKind = OnlineSessionKind.ANONYMOUS,
                            playerId = "visitor-1",
                            accessToken = "visitor-token",
                            expiresAtEpochMillis = 10_000L,
                        ),
                    ),
                    nowEpochMillis = { 1_000L },
                ),
        )

        val result = repository.activatePublicRankedMatch(
            matchId = "match-1",
            localSeatIndex = 0,
        )

        assertEquals(
            OnlinePublicRankedMatchActivation.Failure(
                kind =
                    OnlinePublicRankedMatchActivationFailureKind
                        .ACCOUNT_REQUIRED,
            ),
            result,
        )
        assertEquals(emptyList<String>(), api.matchRequests)
        assertEquals(emptyList<String>(), api.roomRequests)
    }

    private fun createMatchSnapshot(): OnlineMatchSnapshotDto {
        val runtimeState = DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = 2,
            phase = DominoMatchPhase.RoundIntro,
        )

        return runtimeState.toOnlineSnapshotDto(
            roomId = "ranked-room-1",
            matchId = "ranked-match-1",
            revision = 1L,
        )
    }

    private fun createRoomSnapshot(
        roomId: String,
        matchId: String,
        playerId: String,
        localSeatIndex: Int,
    ): OnlineRoomSnapshotDto {
        return OnlineRoomSnapshotDto(
            roomId = roomId,
            roomCode = "internal-code-not-used-by-ui",
            hostPlayerId = "server",
            status = OnlineRoomStatusDto.IN_MATCH,
            players = (0..3).map { seatIndex ->
                OnlineRoomPlayerDto(
                    playerId = if (seatIndex == localSeatIndex) {
                        playerId
                    } else {
                        "other-player-$seatIndex"
                    },
                    name = "Jogador ${seatIndex + 1}",
                    seatIndex = seatIndex,
                    connected = true,
                )
            },
            matchId = matchId,
        )
    }

    private fun accountCredential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            playerId = "player-1",
            accountId = "account-1",
            accessToken = "account-token",
            expiresAtEpochMillis = 10_000L,
        )
    }

    private class InMemoryCredentialStore(
        private var credential: OnlineSessionCredential?,
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

    private class InMemoryBindingStore :
        OnlineParticipationBindingStore {
        var binding: OnlineParticipationBinding? = null

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

    private class FakeRemoteApi(
        private val room: OnlineRoomSnapshotDto,
        private val match: OnlineMatchSnapshotDto,
    ) : RemoteOnlineApiClient {
        val roomRequests = mutableListOf<String>()
        val matchRequests = mutableListOf<String>()
        var bearerToken: String? = null
        var observedDevelopmentPlayerId: String? = "legacy"

        override fun setBearerAccessToken(
            accessToken: String?,
        ) {
            bearerToken = accessToken
        }

        override fun setDevelopmentPlayerId(
            playerId: String?,
        ) {
            observedDevelopmentPlayerId = playerId
        }

        override suspend fun fetchRoomSnapshot(
            roomId: String,
        ): OnlineRoomSnapshotDto {
            roomRequests += roomId
            return room
        }

        override suspend fun fetchMatchSnapshot(
            matchId: String,
        ): OnlineMatchSnapshotDto {
            matchRequests += matchId
            return match
        }

        override suspend fun createRoom(
            request: CreateOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto {
            error("Not used.")
        }

        override suspend fun joinRoom(
            request: JoinOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto {
            error("Not used.")
        }

        override suspend fun submitAction(
            action: OnlinePlayerActionDto,
        ): OnlineActionResultDto {
            error("Not used.")
        }

        override suspend fun submitTraceBatch(
            batch: OnlineTraceBatchDto,
        ): OnlineTraceBatchResultDto {
            return OnlineTraceBatchResultDto(
                accepted = true,
                storedEntryCount = batch.entries.size,
            )
        }
    }
}
