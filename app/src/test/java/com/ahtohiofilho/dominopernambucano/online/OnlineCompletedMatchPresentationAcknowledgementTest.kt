package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineCompletedMatchPresentationAcknowledgementTest {
    @Test
    fun finished_match_acknowledges_exact_binding_once() {
        val repository = RecordingOnlineRoomRepository()
        val coordinator = coordinator(
            repository = repository,
            phase = DominoMatchPhase.MatchFinished,
            localPlayerIndex = 2,
        )

        coordinator.acknowledgeCompletedMatchPresented()
        coordinator.acknowledgeCompletedMatchPresented()

        assertEquals(
            listOf(
                OnlineParticipationBinding(
                    roomId = "room-1",
                    matchId = "match-1",
                    playerId = "player-1",
                    localSeatIndex = 2,
                ),
            ),
            repository.acknowledgedBindings,
        )

        coordinator.dispose()
    }

    @Test
    fun active_match_cannot_acknowledge_terminal_presentation() {
        val repository = RecordingOnlineRoomRepository()
        val coordinator = coordinator(
            repository = repository,
            phase = DominoMatchPhase.WaitingForLocalMove,
            localPlayerIndex = 0,
        )

        coordinator.acknowledgeCompletedMatchPresented()

        assertEquals(
            emptyList<OnlineParticipationBinding>(),
            repository.acknowledgedBindings,
        )

        coordinator.dispose()
    }

    @Test
    fun disposing_unpresented_finished_match_does_not_acknowledge() {
        val repository = RecordingOnlineRoomRepository()
        val coordinator = coordinator(
            repository = repository,
            phase = DominoMatchPhase.MatchFinished,
            localPlayerIndex = 1,
        )

        coordinator.dispose()

        assertEquals(
            emptyList<OnlineParticipationBinding>(),
            repository.acknowledgedBindings,
        )
    }

    private fun coordinator(
        repository: RecordingOnlineRoomRepository,
        phase: DominoMatchPhase,
        localPlayerIndex: Int,
    ): OnlineDominoMatchCoordinator {
        return OnlineDominoMatchCoordinator(
            repository = repository,
            roomId = "room-1",
            matchId = "match-1",
            localPlayerId = "player-1",
            localPlayerIndex = localPlayerIndex,
            initialSnapshot =
                runtimeState(
                    phase = phase,
                    localPlayerIndex = localPlayerIndex,
                ).toOnlineSnapshotDto(
                    roomId = "room-1",
                    matchId = "match-1",
                    revision = 9L,
                ),
            coroutineDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun runtimeState(
        phase: DominoMatchPhase,
        localPlayerIndex: Int,
    ): DominoMatchRuntimeState {
        return DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = localPlayerIndex,
            phase = phase,
        )
    }

    private class RecordingOnlineRoomRepository :
        OnlineRoomRepository {
        private val mutableRoomSnapshot =
            MutableStateFlow<OnlineRoomSnapshotDto?>(null)
        private val mutableMatchSnapshot =
            MutableStateFlow<OnlineMatchSnapshotDto?>(null)

        val acknowledgedBindings =
            mutableListOf<OnlineParticipationBinding>()

        override val roomSnapshot:
            StateFlow<OnlineRoomSnapshotDto?> =
                mutableRoomSnapshot

        override val matchSnapshot:
            StateFlow<OnlineMatchSnapshotDto?> =
                mutableMatchSnapshot

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
                actionId = action.actionId,
            )
        }

        override fun acknowledgeCompletedMatchPresentedLocally(
            binding: OnlineParticipationBinding,
        ) {
            acknowledgedBindings += binding
        }

        override suspend fun leaveRoom() = Unit
    }
}