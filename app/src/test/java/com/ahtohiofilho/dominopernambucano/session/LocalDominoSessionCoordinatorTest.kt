package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionStore
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingStore
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteBlockReason
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInvalidReason
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDominoSessionCoordinatorTest {
    @Test
    fun initial_state_exposes_ready_pending_online_participation_when_local_identity_matches() {
        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "anonymous-player-1",
            localSeatIndex = 2,
        )

        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = TestOnlineParticipationBindingStore(
                        initialBinding = binding,
                    ),
                ),
            onlineAnonymousSessionRepository =
                createAnonymousSessionRepository(
                    playerId = binding.playerId,
                ),
        )

        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .ReadyForRemoteReconciliation(
                            binding = binding,
                        ),
            ),
            coordinator.currentState,
        )

        assertTrue(
            (coordinator.currentState as DominoSessionState.MainMenu)
                .hasPendingOnlineParticipation,
        )
    }

    @Test
    fun initial_state_has_no_pending_online_participation_when_binding_is_absent() {
        val store = TestOnlineParticipationBindingStore()

        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = store,
                ),
        )

        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
            ),
            coordinator.currentState,
        )

        assertFalse(
            (coordinator.currentState as DominoSessionState.MainMenu)
                .hasPendingOnlineParticipation,
        )

        assertEquals(
            0,
            store.clearCallCount,
        )
    }

    @Test
    fun initial_state_has_no_pending_online_participation_when_binding_is_invalid() {
        val store = TestOnlineParticipationBindingStore(
            initialBinding = OnlineParticipationBinding(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "anonymous-player-1",
                localSeatIndex = 4,
            ),
        )

        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = store,
                ),
        )

        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
            ),
            coordinator.currentState,
        )

        assertFalse(
            (coordinator.currentState as DominoSessionState.MainMenu)
                .hasPendingOnlineParticipation,
        )

        assertNull(
            store.storedBinding,
        )

        assertEquals(
            1,
            store.clearCallCount,
        )
    }


    @Test
    fun inspect_pending_online_participation_preserves_binding_when_remote_result_is_recoverable() {
        val binding = createBinding()
        val store = TestOnlineParticipationBindingStore(
            initialBinding = binding,
        )
        val inspection = OnlinePendingParticipationRemoteInspection
            .Recoverable(
                roomSnapshot = createRoomSnapshot(
                    binding = binding,
                ),
            )
        val onlineRoomRepository =
            TestPendingParticipationOnlineRoomRepository(
                inspectionResult = inspection,
            )
        val coordinator = createReadyCoordinator(
            binding = binding,
            store = store,
            onlineRoomRepository = onlineRoomRepository,
        )

        runBlocking {
            coordinator.inspectPendingOnlineParticipation()
        }

        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .ReadyForRemoteReconciliation(
                            binding = binding,
                        ),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState
                        .Completed(
                            result = inspection,
                        ),
            ),
            coordinator.currentState,
        )
        assertEquals(
            binding,
            store.storedBinding,
        )
        assertEquals(
            0,
            store.clearCallCount,
        )
        assertEquals(
            listOf(binding),
            onlineRoomRepository.inspectedBindings,
        )
    }

    @Test
    fun inspect_pending_online_participation_clears_matching_binding_when_remote_result_is_no_longer_recoverable() {
        val binding = createBinding()
        val store = TestOnlineParticipationBindingStore(
            initialBinding = binding,
        )
        val inspection = OnlinePendingParticipationRemoteInspection
            .NoLongerRecoverable(
                reason = OnlinePendingParticipationRemoteInvalidReason
                    .ROOM_CLOSED,
            )
        val coordinator = createReadyCoordinator(
            binding = binding,
            store = store,
            onlineRoomRepository =
                TestPendingParticipationOnlineRoomRepository(
                    inspectionResult = inspection,
                ),
        )

        runBlocking {
            coordinator.inspectPendingOnlineParticipation()
        }

        val expectedMainMenu = DominoSessionState.MainMenu(
            pendingOnlineParticipation =
                OnlinePendingParticipationLocalResolution
                    .NoPendingParticipation,
            pendingOnlineParticipationInspection =
                OnlinePendingParticipationInspectionState
                    .Completed(
                        result = inspection,
                    ),
        )

        assertEquals(
            expectedMainMenu,
            coordinator.currentState,
        )
        assertNull(
            store.storedBinding,
        )
        assertEquals(
            1,
            store.clearCallCount,
        )

        coordinator.dispatch(
            DominoSessionCommand.OpenPlayModeSelection,
        )
        coordinator.dispatch(
            DominoSessionCommand.BackToMainMenu,
        )

        assertEquals(
            expectedMainMenu,
            coordinator.currentState,
        )
    }

    @Test
    fun inspect_pending_online_participation_preserves_replaced_binding_when_remote_result_is_no_longer_recoverable() {
        val inspectedBinding = createBinding()
        val replacementBinding = OnlineParticipationBinding(
            roomId = "room-2",
            matchId = "match-2",
            playerId = inspectedBinding.playerId,
            localSeatIndex = 3,
        )
        val store = TestOnlineParticipationBindingStore(
            initialBinding = inspectedBinding,
        )
        val inspection = OnlinePendingParticipationRemoteInspection
            .NoLongerRecoverable(
                reason = OnlinePendingParticipationRemoteInvalidReason
                    .ROOM_CLOSED,
            )
        val onlineRoomRepository =
            TestPendingParticipationOnlineRoomRepository(
                inspectionResult = inspection,
                onInspect = {
                    store.write(
                        binding = replacementBinding,
                    )
                },
            )
        val coordinator = createReadyCoordinator(
            binding = inspectedBinding,
            store = store,
            onlineRoomRepository = onlineRoomRepository,
        )

        runBlocking {
            coordinator.inspectPendingOnlineParticipation()
        }

        assertEquals(
            replacementBinding,
            store.storedBinding,
        )
        assertEquals(
            0,
            store.clearCallCount,
        )
        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .ReadyForRemoteReconciliation(
                            binding = replacementBinding,
                        ),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState
                        .Completed(
                            result = inspection,
                        ),
            ),
            coordinator.currentState,
        )
    }

    @Test
    fun inspect_pending_online_participation_preserves_binding_when_remote_attempt_is_blocked() {
        val binding = createBinding()
        val store = TestOnlineParticipationBindingStore(
            initialBinding = binding,
        )
        val inspection = OnlinePendingParticipationRemoteInspection
            .NotAttempted(
                reason = OnlinePendingParticipationRemoteBlockReason
                    .MISSING_VALID_ANONYMOUS_SESSION,
            )
        val coordinator = createReadyCoordinator(
            binding = binding,
            store = store,
            onlineRoomRepository =
                TestPendingParticipationOnlineRoomRepository(
                    inspectionResult = inspection,
                ),
        )

        runBlocking {
            coordinator.inspectPendingOnlineParticipation()
        }

        assertEquals(
            binding,
            store.storedBinding,
        )
        assertEquals(
            0,
            store.clearCallCount,
        )
        assertEquals(
            OnlinePendingParticipationInspectionState.Completed(
                result = inspection,
            ),
            (coordinator.currentState as DominoSessionState.MainMenu)
                .pendingOnlineParticipationInspection,
        )
    }

    @Test
    fun inspect_pending_online_participation_preserves_binding_when_remote_is_temporarily_unavailable() {
        val binding = createBinding()
        val store = TestOnlineParticipationBindingStore(
            initialBinding = binding,
        )
        val inspection = OnlinePendingParticipationRemoteInspection
            .TemporarilyUnavailable(
                reason = "Falha transitória.",
            )
        val coordinator = createReadyCoordinator(
            binding = binding,
            store = store,
            onlineRoomRepository =
                TestPendingParticipationOnlineRoomRepository(
                    inspectionResult = inspection,
                ),
        )

        runBlocking {
            coordinator.inspectPendingOnlineParticipation()
        }

        assertEquals(
            binding,
            store.storedBinding,
        )
        assertEquals(
            0,
            store.clearCallCount,
        )
        assertEquals(
            OnlinePendingParticipationInspectionState.Completed(
                result = inspection,
            ),
            (coordinator.currentState as DominoSessionState.MainMenu)
                .pendingOnlineParticipationInspection,
        )
    }

    @Test
    fun inspect_pending_online_participation_does_not_call_remote_repository_when_local_resolution_is_blocked() {
        val binding = createBinding()
        val store = TestOnlineParticipationBindingStore(
            initialBinding = binding,
        )
        val onlineRoomRepository =
            TestPendingParticipationOnlineRoomRepository(
                inspectionResult =
                    OnlinePendingParticipationRemoteInspection
                        .TemporarilyUnavailable(
                            reason = "Não deve ser chamado.",
                        ),
            )
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = store,
                ),
            onlineRoomRepository = onlineRoomRepository,
        )

        runBlocking {
            coordinator.inspectPendingOnlineParticipation()
        }

        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .BlockedByMissingValidAnonymousSession(
                            binding = binding,
                        ),
            ),
            coordinator.currentState,
        )
        assertTrue(
            onlineRoomRepository.inspectedBindings.isEmpty(),
        )
        assertEquals(
            binding,
            store.storedBinding,
        )
    }

    private fun createReadyCoordinator(
        binding: OnlineParticipationBinding,
        store: TestOnlineParticipationBindingStore,
        onlineRoomRepository: OnlineRoomRepository,
    ): LocalDominoSessionCoordinator {
        return LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = store,
                ),
            onlineAnonymousSessionRepository =
                createAnonymousSessionRepository(
                    playerId = binding.playerId,
                ),
            onlineRoomRepository = onlineRoomRepository,
        )
    }

    private fun createBinding(): OnlineParticipationBinding {
        return OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "anonymous-player-1",
            localSeatIndex = 2,
        )
    }

    private fun createRoomSnapshot(
        binding: OnlineParticipationBinding,
    ): OnlineRoomSnapshotDto {
        return OnlineRoomSnapshotDto(
            roomId = binding.roomId,
            roomCode = "1234",
            hostPlayerId = binding.playerId,
            status = OnlineRoomStatusDto.IN_MATCH,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = binding.playerId,
                    name = "Jogador",
                    seatIndex = binding.localSeatIndex,
                    connected = true,
                ),
            ),
            matchId = binding.matchId,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
    }

    private fun createAnonymousSessionRepository(
        playerId: String,
    ): OnlineAnonymousSessionRepository {
        return OnlineAnonymousSessionRepository(
            store = TestOnlineAnonymousSessionStore(
                initialSession = OnlineAnonymousSessionDto(
                    playerId = playerId,
                    accessToken = "test-access-token",
                    expiresAtEpochMillis = Long.MAX_VALUE,
                ),
            ),
        )
    }
}

private class TestOnlineParticipationBindingStore(
    initialBinding: OnlineParticipationBinding? = null,
) : OnlineParticipationBindingStore {
    var storedBinding: OnlineParticipationBinding? = initialBinding
        private set

    var clearCallCount: Int = 0
        private set

    override fun read(): OnlineParticipationBinding? {
        return storedBinding
    }

    override fun write(
        binding: OnlineParticipationBinding,
    ) {
        storedBinding = binding
    }

    override fun clear() {
        clearCallCount += 1
        storedBinding = null
    }
}

private class TestOnlineAnonymousSessionStore(
    initialSession: OnlineAnonymousSessionDto? = null,
) : OnlineAnonymousSessionStore {
    private var storedSession: OnlineAnonymousSessionDto? = initialSession

    override fun read(): OnlineAnonymousSessionDto? {
        return storedSession
    }

    override fun write(
        session: OnlineAnonymousSessionDto,
    ) {
        storedSession = session
    }

    override fun clear() {
        storedSession = null
    }
}

private class TestPendingParticipationOnlineRoomRepository(
    private val inspectionResult:
        OnlinePendingParticipationRemoteInspection,
    private val onInspect: ((OnlineParticipationBinding) -> Unit)? = null,
) : OnlineRoomRepository {
    private val mutableRoomSnapshot =
        MutableStateFlow<OnlineRoomSnapshotDto?>(null)

    private val mutableMatchSnapshot =
        MutableStateFlow<OnlineMatchSnapshotDto?>(null)

    val inspectedBindings = mutableListOf<OnlineParticipationBinding>()

    override val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?> =
        mutableRoomSnapshot.asStateFlow()

    override val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?> =
        mutableMatchSnapshot.asStateFlow()

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return OnlineRoomOperationResultDto(
            accepted = false,
        )
    }

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return OnlineRoomOperationResultDto(
            accepted = false,
        )
    }

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        return OnlineActionResultDto(
            accepted = false,
        )
    }

    override suspend fun inspectPendingParticipation(
        binding: OnlineParticipationBinding,
    ): OnlinePendingParticipationRemoteInspection {
        inspectedBindings += binding
        onInspect?.invoke(binding)

        return inspectionResult
    }

    override suspend fun leaveRoom() = Unit
}