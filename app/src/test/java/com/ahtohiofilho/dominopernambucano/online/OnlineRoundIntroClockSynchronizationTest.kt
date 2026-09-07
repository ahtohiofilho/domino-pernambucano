package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRoundIntroClockSynchronizationTest {
    @Test
    fun clock_starts_only_after_authoritative_round_intro_finishes() =
        runBlocking {
            var nowEpochMillis = 1_000L
            var monotonicMillis = 10_000L

            val repository = FakeOnlineRoomRepository(
                nowEpochMillis = { nowEpochMillis },
            )

            val room = requireNotNull(
                repository.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "player-1",
                        playerName = "Jogador 1",
                    ),
                ).roomSnapshot,
            )

            (2..4).forEach { number ->
                repository.joinRoom(
                    JoinOnlineRoomRequestDto(
                        roomCode = room.roomCode,
                        localPlayerId = "player-$number",
                        playerName = "Jogador $number",
                    ),
                )
            }

            val activeRoom = requireNotNull(repository.roomSnapshot.value)
            val initialSnapshot =
                requireNotNull(repository.matchSnapshot.value)
            val initialRuntime =
                initialSnapshot.toRuntimeState(localPlayerIndex = 0)
            val currentPlayerIndex =
                initialRuntime.gameState.currentPlayerIndex
            val currentPlayerId = requireNotNull(
                activeRoom.players.firstOrNull { player ->
                    player.seatIndex == currentPlayerIndex
                },
            ).playerId

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = activeRoom.roomId,
                matchId = requireNotNull(activeRoom.matchId),
                localPlayerId = currentPlayerId,
                localPlayerIndex = currentPlayerIndex,
                initialSnapshot = initialSnapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
                monotonicNowMillis = { monotonicMillis },
            )

            try {
                assertEquals(
                    DominoMatchPhase.RoundIntro,
                    coordinator.currentState.phase,
                )
                assertEquals(
                    20_000L,
                    coordinator.currentState
                        .playerClockMillis[currentPlayerIndex],
                )

                nowEpochMillis += 5_000L
                monotonicMillis += 5_000L

                coordinator.dispatch(
                    DominoMatchCommand.TurnClockTick(
                        elapsedMillis = 250L,
                    ),
                )

                assertEquals(
                    20_000L,
                    coordinator.currentState
                        .playerClockMillis[currentPlayerIndex],
                )

                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )
                yield()

                assertEquals(
                    DominoMatchPhase.WaitingForLocalMove,
                    coordinator.currentState.phase,
                )
                assertEquals(
                    20_000L,
                    coordinator.currentState
                        .playerClockMillis[currentPlayerIndex],
                )

                monotonicMillis += 1_000L

                coordinator.dispatch(
                    DominoMatchCommand.TurnClockTick(
                        elapsedMillis = 250L,
                    ),
                )

                assertEquals(
                    19_000L,
                    coordinator.currentState
                        .playerClockMillis[currentPlayerIndex],
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun active_match_resume_does_not_fabricate_round_intro() {
        val waitingState =
            com.ahtohiofilho.dominopernambucano.match
                .DominoMatchRuntimeState(
                    gameState =
                        com.ahtohiofilho.dominopernambucano.domain
                            .createInitialDominoGameState(),
                    roundNumber = 1,
                    localPlayerIndex = 0,
                    phase = DominoMatchPhase.WaitingForLocalMove,
                    clockPolicy =
                        com.ahtohiofilho.dominopernambucano.match
                            .DominoMatchClockPolicy.OnlinePerPlayerRound,
                    playerClockMillis = List(4) { 20_000L },
                    playerClockReserveMillis = List(4) { 20_000L },
                )

        assertEquals(
            waitingState,
            initialOnlineMatchPresentationState(waitingState),
        )
    }

    @Test
    fun fake_repository_terminal_transition_is_not_wrapped_in_round_intro() {
        val runtime =
            com.ahtohiofilho.dominopernambucano.match
                .DominoMatchRuntimeState(
                    gameState =
                        com.ahtohiofilho.dominopernambucano.domain
                            .createInitialDominoGameState(),
                    roundNumber = 1,
                    localPlayerIndex = 0,
                    phase = DominoMatchPhase.MatchFinished,
                    clockPolicy =
                        com.ahtohiofilho.dominopernambucano.match
                            .DominoMatchClockPolicy.OnlinePerPlayerRound,
                    playerClockMillis = List(4) { 20_000L },
                    playerClockReserveMillis = List(4) { 20_000L },
                )

        assertEquals(
            DominoMatchPhase.MatchFinished,
            applyFakeRoundIntroAfterNextRound(runtime).phase,
        )
        assertEquals(
            DominoMatchPhase.RoundIntro,
            applyFakeRoundIntroAfterNextRound(
                runtime.copy(
                    phase = DominoMatchPhase.WaitingForLocalMove,
                ),
            ).phase,
        )
    }
}