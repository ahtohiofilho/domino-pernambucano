package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionStore
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingStore
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.toOnlineSnapshotDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDominoSessionCoordinatorRemoteConfirmationTest {
    @Test
    fun invalidating_remote_confirmation_reclassifies_same_binding_after_session_disappears() {
        val binding = onlineParticipationBinding(
            roomId = "room-1",
            playerId = "player-1",
        )
        val bindingStore = MutableOnlineParticipationBindingStore(
            binding = binding,
        )
        val anonymousSessionStore = MutableOnlineAnonymousSessionStore(
            session = validAnonymousSession(
                playerId = binding.playerId,
            ),
        )
        val bindingRepository = OnlineParticipationBindingRepository(
            store = bindingStore,
        )
        val anonymousSessionRepository =
            OnlineAnonymousSessionRepository(
                store = anonymousSessionStore,
                nowEpochMillis = { 0L },
            )
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                bindingRepository,
            onlineAnonymousSessionRepository =
                anonymousSessionRepository,
        )

        anonymousSessionStore.clear()

        coordinator.invalidatePendingOnlineParticipationRemoteConfirmation(
            binding = binding,
        )

        val mainMenuState = coordinator.currentState
            as DominoSessionState.MainMenu

        val pendingParticipation = mainMenuState
            .pendingOnlineParticipation

        assertTrue(
            pendingParticipation is
                    OnlinePendingParticipationLocalResolution
                    .BlockedByMissingValidAnonymousSession,
        )
        assertEquals(
            binding,
            (
                pendingParticipation as
                        OnlinePendingParticipationLocalResolution
                        .BlockedByMissingValidAnonymousSession
            ).binding,
        )
        assertEquals(
            OnlinePendingParticipationInspectionState.NotRequested,
            mainMenuState.pendingOnlineParticipationInspection,
        )
        assertEquals(
            OnlinePendingParticipationSessionRejection.NotRejected,
            mainMenuState.pendingOnlineParticipationSessionRejection,
        )
        assertEquals(
            binding,
            bindingRepository.getValidBindingOrNull(),
        )
        assertEquals(
            null,
            anonymousSessionRepository.getValidSessionOrNull(),
        )
    }

    @Test
    fun invalidating_remote_confirmation_does_not_touch_a_newer_persisted_binding() {
        val originalBinding = onlineParticipationBinding(
            roomId = "room-1",
            playerId = "player-1",
        )
        val replacementBinding = onlineParticipationBinding(
            roomId = "room-2",
            playerId = "player-2",
        )
        val bindingStore = MutableOnlineParticipationBindingStore(
            binding = originalBinding,
        )
        val anonymousSessionStore = MutableOnlineAnonymousSessionStore(
            session = validAnonymousSession(
                playerId = originalBinding.playerId,
            ),
        )
        val bindingRepository = OnlineParticipationBindingRepository(
            store = bindingStore,
        )
        val anonymousSessionRepository =
            OnlineAnonymousSessionRepository(
                store = anonymousSessionStore,
                nowEpochMillis = { 0L },
            )
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                bindingRepository,
            onlineAnonymousSessionRepository =
                anonymousSessionRepository,
        )

        bindingRepository.save(
            binding = replacementBinding,
        )

        coordinator.invalidatePendingOnlineParticipationRemoteConfirmation(
            binding = originalBinding,
        )

        val mainMenuState = coordinator.currentState
            as DominoSessionState.MainMenu

        assertEquals(
            replacementBinding,
            bindingRepository.getValidBindingOrNull(),
        )
        assertEquals(
            OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation(
                    binding = originalBinding,
                ),
            mainMenuState.pendingOnlineParticipation,
        )
        assertEquals(
            OnlinePendingParticipationInspectionState.NotRequested,
            mainMenuState.pendingOnlineParticipationInspection,
        )
        assertEquals(
            OnlinePendingParticipationSessionRejection.NotRejected,
            mainMenuState.pendingOnlineParticipationSessionRejection,
        )
    }

    @Test
    fun invalidating_remote_confirmation_reclassifies_same_binding_after_session_identity_diverges() {
        val binding = onlineParticipationBinding(
            roomId = "room-1",
            playerId = "player-1",
        )
        val bindingStore = MutableOnlineParticipationBindingStore(
            binding = binding,
        )
        val anonymousSessionStore = MutableOnlineAnonymousSessionStore(
            session = validAnonymousSession(
                playerId = binding.playerId,
            ),
        )
        val bindingRepository = OnlineParticipationBindingRepository(
            store = bindingStore,
        )
        val anonymousSessionRepository =
            OnlineAnonymousSessionRepository(
                store = anonymousSessionStore,
                nowEpochMillis = { 0L },
            )
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                bindingRepository,
            onlineAnonymousSessionRepository =
                anonymousSessionRepository,
        )

        anonymousSessionStore.write(
            session = validAnonymousSession(
                playerId = "player-2",
            ),
        )

        coordinator.invalidatePendingOnlineParticipationRemoteConfirmation(
            binding = binding,
        )

        val mainMenuState = coordinator.currentState
            as DominoSessionState.MainMenu

        val pendingParticipation = mainMenuState
            .pendingOnlineParticipation

        assertTrue(
            pendingParticipation is
                    OnlinePendingParticipationLocalResolution
                    .BlockedByAnonymousSessionIdentityMismatch,
        )
        assertEquals(
            binding,
            (
                pendingParticipation as
                        OnlinePendingParticipationLocalResolution
                        .BlockedByAnonymousSessionIdentityMismatch
            ).binding,
        )
        assertEquals(
            OnlinePendingParticipationInspectionState.NotRequested,
            mainMenuState.pendingOnlineParticipationInspection,
        )
        assertEquals(
            OnlinePendingParticipationSessionRejection.NotRejected,
            mainMenuState.pendingOnlineParticipationSessionRejection,
        )
        assertEquals(
            binding,
            bindingRepository.getValidBindingOrNull(),
        )
        assertEquals(
            "player-2",
            anonymousSessionRepository
                .getValidSessionOrNull()
                ?.playerId,
        )
    }

    @Test
    fun discarding_no_longer_recoverable_pending_participation_clears_matching_binding() {
        val binding = onlineParticipationBinding(
            roomId = "room-1",
            playerId = "player-1",
        )
        val bindingStore = MutableOnlineParticipationBindingStore(
            binding = binding,
        )
        val anonymousSessionStore = MutableOnlineAnonymousSessionStore(
            session = validAnonymousSession(
                playerId = binding.playerId,
            ),
        )
        val bindingRepository = OnlineParticipationBindingRepository(
            store = bindingStore,
        )
        val anonymousSessionRepository =
            OnlineAnonymousSessionRepository(
                store = anonymousSessionStore,
                nowEpochMillis = { 0L },
            )
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                bindingRepository,
            onlineAnonymousSessionRepository =
                anonymousSessionRepository,
        )

        coordinator.discardNoLongerRecoverablePendingOnlineParticipation(
            binding = binding,
        )

        val mainMenuState = coordinator.currentState
            as DominoSessionState.MainMenu

        assertEquals(
            null,
            bindingRepository.getValidBindingOrNull(),
        )
        assertEquals(
            OnlinePendingParticipationLocalResolution
                .NoPendingParticipation,
            mainMenuState.pendingOnlineParticipation,
        )
        assertEquals(
            OnlinePendingParticipationInspectionState.NotRequested,
            mainMenuState.pendingOnlineParticipationInspection,
        )
        assertEquals(
            OnlinePendingParticipationSessionRejection.NotRejected,
            mainMenuState.pendingOnlineParticipationSessionRejection,
        )
    }

    @Test
    fun discarding_no_longer_recoverable_pending_participation_does_not_clear_newer_binding() {
        val originalBinding = onlineParticipationBinding(
            roomId = "room-1",
            playerId = "player-1",
        )
        val replacementBinding = onlineParticipationBinding(
            roomId = "room-2",
            playerId = "player-2",
        )
        val bindingStore = MutableOnlineParticipationBindingStore(
            binding = originalBinding,
        )
        val anonymousSessionStore = MutableOnlineAnonymousSessionStore(
            session = validAnonymousSession(
                playerId = originalBinding.playerId,
            ),
        )
        val bindingRepository = OnlineParticipationBindingRepository(
            store = bindingStore,
        )
        val anonymousSessionRepository =
            OnlineAnonymousSessionRepository(
                store = anonymousSessionStore,
                nowEpochMillis = { 0L },
            )
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                bindingRepository,
            onlineAnonymousSessionRepository =
                anonymousSessionRepository,
        )

        bindingRepository.save(
            binding = replacementBinding,
        )

        coordinator.discardNoLongerRecoverablePendingOnlineParticipation(
            binding = originalBinding,
        )

        val mainMenuState = coordinator.currentState
            as DominoSessionState.MainMenu

        assertEquals(
            replacementBinding,
            bindingRepository.getValidBindingOrNull(),
        )
        assertEquals(
            OnlinePendingParticipationLocalResolution
                .BlockedByAnonymousSessionIdentityMismatch(
                    binding = replacementBinding,
                ),
            mainMenuState.pendingOnlineParticipation,
        )
        assertEquals(
            OnlinePendingParticipationInspectionState.NotRequested,
            mainMenuState.pendingOnlineParticipationInspection,
        )
        assertEquals(
            OnlinePendingParticipationSessionRejection.NotRejected,
            mainMenuState.pendingOnlineParticipationSessionRejection,
        )
    }

    @Test
    fun active_match_session_rejection_re_resolves_matching_binding_and_returns_main_menu_rejected() {
        val binding = onlineParticipationBinding(
            roomId = "room-1",
            playerId = "player-1",
        )
        val bindingStore = MutableOnlineParticipationBindingStore(
            binding = null,
        )
        val anonymousSessionStore =
            MutableOnlineAnonymousSessionStore(
                session = validAnonymousSession(
                    playerId = binding.playerId,
                ),
            )
        val bindingRepository =
            OnlineParticipationBindingRepository(
                store = bindingStore,
            )
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                bindingRepository,
            onlineAnonymousSessionRepository =
                OnlineAnonymousSessionRepository(
                    store = anonymousSessionStore,
                    nowEpochMillis = { 0L },
                ),
        )
        val matchCoordinator =
            createOnlineMatchCoordinator(
                binding = binding,
            )

        coordinator.dispatch(
            DominoSessionCommand.StartOnlineMatch(
                matchCoordinator = matchCoordinator,
            ),
        )

        bindingRepository.save(
            binding = binding,
        )

        assertEquals(
            true,
            coordinator
                .returnActiveOnlineMatchToMainMenuAfterRemoteSessionRejected(
                    binding = binding,
                ),
        )

        val mainMenuState = coordinator.currentState
            as DominoSessionState.MainMenu

        assertEquals(
            OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation(
                    binding = binding,
                ),
            mainMenuState.pendingOnlineParticipation,
        )
        assertEquals(
            OnlinePendingParticipationInspectionState.NotRequested,
            mainMenuState.pendingOnlineParticipationInspection,
        )
        assertEquals(
            OnlinePendingParticipationSessionRejection
                .RemoteSessionRejected(
                    binding = binding,
                ),
            mainMenuState.pendingOnlineParticipationSessionRejection,
        )
        assertEquals(
            binding,
            bindingRepository.getValidBindingOrNull(),
        )
    }

    @Test
    fun active_match_session_rejection_does_not_mark_newer_replacement_binding_rejected() {
        val invalidatedBinding = onlineParticipationBinding(
            roomId = "room-1",
            playerId = "player-1",
        )
        val replacementBinding = onlineParticipationBinding(
            roomId = "room-2",
            playerId = "player-2",
        )
        val bindingStore = MutableOnlineParticipationBindingStore(
            binding = null,
        )
        val anonymousSessionStore =
            MutableOnlineAnonymousSessionStore(
                session = validAnonymousSession(
                    playerId = invalidatedBinding.playerId,
                ),
            )
        val bindingRepository =
            OnlineParticipationBindingRepository(
                store = bindingStore,
            )
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                bindingRepository,
            onlineAnonymousSessionRepository =
                OnlineAnonymousSessionRepository(
                    store = anonymousSessionStore,
                    nowEpochMillis = { 0L },
                ),
        )
        val matchCoordinator =
            createOnlineMatchCoordinator(
                binding = invalidatedBinding,
            )

        coordinator.dispatch(
            DominoSessionCommand.StartOnlineMatch(
                matchCoordinator = matchCoordinator,
            ),
        )

        bindingRepository.save(
            binding = replacementBinding,
        )
        anonymousSessionStore.write(
            session = validAnonymousSession(
                playerId = replacementBinding.playerId,
            ),
        )

        assertEquals(
            false,
            coordinator
                .returnActiveOnlineMatchToMainMenuAfterRemoteSessionRejected(
                    binding = invalidatedBinding,
                ),
        )

        val mainMenuState = coordinator.currentState
            as DominoSessionState.MainMenu

        assertEquals(
            OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation(
                    binding = replacementBinding,
                ),
            mainMenuState.pendingOnlineParticipation,
        )
        assertEquals(
            OnlinePendingParticipationSessionRejection.NotRejected,
            mainMenuState.pendingOnlineParticipationSessionRejection,
        )
        assertEquals(
            replacementBinding,
            bindingRepository.getValidBindingOrNull(),
        )
    }

    private fun createOnlineMatchCoordinator(
        binding: OnlineParticipationBinding,
    ): OnlineDominoMatchCoordinator {
        val runtimeState = DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = binding.localSeatIndex,
            phase = DominoMatchPhase.WaitingForLocalMove,
        )
        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = binding.roomId,
            matchId = requireNotNull(binding.matchId),
            revision = 1L,
        )

        return OnlineDominoMatchCoordinator(
            repository =
                ActiveMatchSessionTestOnlineRoomRepository(
                    initialSnapshot = snapshot,
                ),
            roomId = binding.roomId,
            matchId = requireNotNull(binding.matchId),
            localPlayerId = binding.playerId,
            localPlayerIndex = binding.localSeatIndex,
            initialSnapshot = snapshot,
            coroutineDispatcher = Dispatchers.Unconfined,
        )
    }

    private class ActiveMatchSessionTestOnlineRoomRepository(
        initialSnapshot: OnlineMatchSnapshotDto,
    ) : OnlineRoomRepository {
        private val mutableRoomSnapshot =
            MutableStateFlow<OnlineRoomSnapshotDto?>(null)

        private val mutableMatchSnapshot =
            MutableStateFlow<OnlineMatchSnapshotDto?>(
                initialSnapshot,
            )

        override val roomSnapshot:
            StateFlow<OnlineRoomSnapshotDto?> =
            mutableRoomSnapshot.asStateFlow()

        override val matchSnapshot:
            StateFlow<OnlineMatchSnapshotDto?> =
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
                accepted = true,
                revision = mutableMatchSnapshot.value?.revision,
            )
        }

        override suspend fun leaveRoom() = Unit
    }

    private fun onlineParticipationBinding(
        roomId: String,
        playerId: String,
    ): OnlineParticipationBinding {
        return OnlineParticipationBinding(
            roomId = roomId,
            matchId = "match-$roomId",
            playerId = playerId,
            localSeatIndex = 0,
        )
    }

    private fun validAnonymousSession(
        playerId: String,
    ): OnlineAnonymousSessionDto {
        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = "access-token-$playerId",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
    }
}

private class MutableOnlineParticipationBindingStore(
    private var binding: OnlineParticipationBinding?,
) : OnlineParticipationBindingStore {
    override fun read(): OnlineParticipationBinding? {
        return binding
    }

    override fun write(
        binding: OnlineParticipationBinding,
    ) {
        this.binding = binding
    }

    override fun clear() {
        binding = null
    }
}

private class MutableOnlineAnonymousSessionStore(
    private var session: OnlineAnonymousSessionDto?,
) : OnlineAnonymousSessionStore {
    override fun read(): OnlineAnonymousSessionDto? {
        return session
    }

    override fun write(
        session: OnlineAnonymousSessionDto,
    ) {
        this.session = session
    }

    override fun clear() {
        session = null
    }
}
