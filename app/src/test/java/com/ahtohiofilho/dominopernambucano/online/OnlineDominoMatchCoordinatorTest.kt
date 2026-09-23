package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.online.observability.InMemoryOnlineTraceBuffer
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineDominoMatchCoordinatorTest {
    @Test
    fun dispose_releases_local_repository_state_after_match_finished() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) { emptyList() },
            ).copy(
                phase = DominoMatchPhase.MatchFinished,
            )

            val repository = TestOnlineRoomRepository(
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
            )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            coordinator.dispatch(
                DominoMatchCommand.RoundIntroFinished,
            )

            assertEquals(
                DominoMatchPhase.MatchFinished,
                coordinator.currentState.phase,
            )

            coordinator.dispose()

            assertEquals(
                1,
                repository.completedMatchLocalReleaseCount,
            )
        }

    @Test
    fun dispose_does_not_release_local_repository_state_during_active_match() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) { emptyList() },
            )

            val repository = TestOnlineRoomRepository(
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
            )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            coordinator.dispatch(
                DominoMatchCommand.RoundIntroFinished,
            )

            assertEquals(
                DominoMatchPhase.WaitingForLocalMove,
                coordinator.currentState.phase,
            )

            coordinator.dispose()

            assertEquals(
                0,
                repository.completedMatchLocalReleaseCount,
            )
        }

    @Test
    fun matching_session_invalidation_exposes_exact_binding_and_clears_inflight_action() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) {
                    emptyList()
                },
            )
            val snapshot = runtimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = snapshot,
            )
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = snapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.StartNextRound,
                )
                coordinator.dispatch(
                    DominoMatchCommand.StartNextRound,
                )
                yield()

                assertEquals(
                    1,
                    repository.submittedActions.size,
                )

                repository.publishActiveMatchSessionInvalidation(
                    OnlineActiveMatchSessionInvalidation(
                        roomId = TEST_ROOM_ID,
                        matchId = TEST_MATCH_ID,
                        playerId = TEST_PLAYER_ID,
                    ),
                )
                yield()

                assertEquals(
                    OnlineParticipationBinding(
                        roomId = TEST_ROOM_ID,
                        matchId = TEST_MATCH_ID,
                        playerId = TEST_PLAYER_ID,
                        localSeatIndex = 0,
                    ),
                    coordinator.activeSessionInvalidation.value,
                )

                coordinator.dispatch(
                    DominoMatchCommand.StartNextRound,
                )
                yield()

                assertEquals(
                    2,
                    repository.submittedActions.size,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun different_match_session_invalidation_is_ignored() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) {
                    emptyList()
                },
            )
            val snapshot = runtimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = snapshot,
            )
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = snapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.StartNextRound,
                )
                yield()

                repository.publishActiveMatchSessionInvalidation(
                    OnlineActiveMatchSessionInvalidation(
                        roomId = TEST_ROOM_ID,
                        matchId = "different-match",
                        playerId = TEST_PLAYER_ID,
                    ),
                )
                yield()

                assertEquals(
                    null,
                    coordinator.activeSessionInvalidation.value,
                )

                coordinator.dispatch(
                    DominoMatchCommand.StartNextRound,
                )
                yield()

                assertEquals(
                    1,
                    repository.submittedActions.size,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun matching_authorization_loss_exposes_exact_binding_and_reason() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) {
                    emptyList()
                },
            )
            val snapshot = runtimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = snapshot,
            )
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = snapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                repository.publishActiveMatchParticipationAuthorizationLoss(
                    OnlineActiveMatchParticipationAuthorizationLoss(
                        roomId = TEST_ROOM_ID,
                        matchId = TEST_MATCH_ID,
                        playerId = TEST_PLAYER_ID,
                        reason =
                            OnlineActiveMatchParticipationAuthorizationLossReason
                            .MATCH_PARTICIPATION_FORBIDDEN,
                    ),
                )
                yield()

                assertEquals(
                    OnlineActiveMatchParticipationAuthorizationLossResolution(
                        binding = OnlineParticipationBinding(
                            roomId = TEST_ROOM_ID,
                            matchId = TEST_MATCH_ID,
                            playerId = TEST_PLAYER_ID,
                            localSeatIndex = 0,
                        ),
                        reason =
                            OnlineActiveMatchParticipationAuthorizationLossReason
                            .MATCH_PARTICIPATION_FORBIDDEN,
                    ),
                    coordinator.activeParticipationAuthorizationLoss.value,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun different_match_authorization_loss_is_ignored() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) {
                    emptyList()
                },
            )
            val snapshot = runtimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = snapshot,
            )
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = snapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                repository.publishActiveMatchParticipationAuthorizationLoss(
                    OnlineActiveMatchParticipationAuthorizationLoss(
                        roomId = TEST_ROOM_ID,
                        matchId = "different-match",
                        playerId = TEST_PLAYER_ID,
                        reason =
                            OnlineActiveMatchParticipationAuthorizationLossReason
                            .MATCH_PARTICIPATION_FORBIDDEN,
                    ),
                )
                yield()

                assertEquals(
                    null,
                    coordinator.activeParticipationAuthorizationLoss.value,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun matching_resource_loss_exposes_exact_binding_and_reason() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) {
                    emptyList()
                },
            )
            val snapshot = runtimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = snapshot,
            )
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = snapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                repository.publishActiveMatchResourceLoss(
                    OnlineActiveMatchResourceLoss(
                        roomId = TEST_ROOM_ID,
                        matchId = TEST_MATCH_ID,
                        playerId = TEST_PLAYER_ID,
                        reason =
                            OnlineActiveMatchResourceLossReason.MATCH_NOT_FOUND,
                    ),
                )
                yield()

                assertEquals(
                    OnlineActiveMatchResourceLossResolution(
                        binding = OnlineParticipationBinding(
                            roomId = TEST_ROOM_ID,
                            matchId = TEST_MATCH_ID,
                            playerId = TEST_PLAYER_ID,
                            localSeatIndex = 0,
                        ),
                        reason =
                            OnlineActiveMatchResourceLossReason.MATCH_NOT_FOUND,
                    ),
                    coordinator.activeResourceLoss.value,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun different_match_resource_loss_is_ignored() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) {
                    emptyList()
                },
            )
            val snapshot = runtimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = snapshot,
            )
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = snapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                repository.publishActiveMatchResourceLoss(
                    OnlineActiveMatchResourceLoss(
                        roomId = TEST_ROOM_ID,
                        matchId = "different-match",
                        playerId = TEST_PLAYER_ID,
                        reason =
                            OnlineActiveMatchResourceLossReason.MATCH_NOT_FOUND,
                    ),
                )
                yield()

                assertEquals(
                    null,
                    coordinator.activeResourceLoss.value,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun presentation_bridge_exposes_refilled_clock_from_remote_snapshot_during_move_animation() =
        runBlocking {
            val openingPiece = DominoPiece(
                left = 6,
                right = 6,
            )

            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(openingPiece),
                    listOf(DominoPiece(6, 5)),
                    listOf(DominoPiece(1, 1)),
                    listOf(DominoPiece(2, 2)),
                ),
            ).copy(
                clockPolicy =
                    DominoMatchClockPolicy.OnlinePerPlayerRound,
                playerClockMillis = listOf(
                    8_000L,
                    20_000L,
                    20_000L,
                    20_000L,
                ),
                playerClockReserveMillis = listOf(
                    20_000L,
                    20_000L,
                    20_000L,
                    20_000L,
                ),
            )

            val remoteRuntimeState = createRuntimeState(
                board = listOf(openingPiece),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                ),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    emptyList(),
                    listOf(DominoPiece(6, 5)),
                    listOf(DominoPiece(1, 1)),
                    listOf(DominoPiece(2, 2)),
                ),
            ).copy(
                clockPolicy =
                    DominoMatchClockPolicy.OnlinePerPlayerRound,
                playerClockMillis = listOf(
                    20_000L,
                    20_000L,
                    20_000L,
                    20_000L,
                ),
                playerClockReserveMillis = listOf(
                    8_000L,
                    20_000L,
                    20_000L,
                    20_000L,
                ),
            )

            val initialSnapshot = initialRuntimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialSnapshot,
            )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialSnapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
                monotonicNowMillis = { 1_000L },
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    remoteRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertTrue(
                    coordinator.currentState.phase
                        is DominoMatchPhase.PresentingMove,
                )

                /*
                 * The animation intentionally renders the previous game board,
                 * but the visible clock pair must already use the accepted
                 * authoritative snapshot values. Otherwise the player sees the
                 * spent 8s/20s pair during the move animation even though the
                 * server has already refilled it to 20s/8s.
                 */
                assertEquals(
                    20_000L,
                    coordinator.currentState.playerClockMillis[0],
                )
                assertEquals(
                    8_000L,
                    coordinator.currentState.playerClockReserveMillis[0],
                )
            } finally {
                coordinator.dispose()
            }
        }
    @Test
    fun round_intro_is_not_cut_short_by_gameplay_presentation_watchdog() =
        runBlocking {
            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) {
                    emptyList()
                },
            ).copy(
                roundNumber = 1,
            )

            val nextRoundRuntimeState = initialRuntimeState.copy(
                roundNumber = 2,
                phase = DominoMatchPhase.WaitingForLocalMove,
            )

            val initialSnapshot = initialRuntimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialSnapshot,
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialSnapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
                traceLogger = OnlineTraceLogger(
                    sink = traceBuffer,
                    nowEpochMillis = { 1_000L },
                ),
                presentationWatchdogMillis = 25L,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    nextRoundRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertEquals(
                    DominoMatchPhase.RoundIntro,
                    coordinator.currentState.phase,
                )

                delay(100L)

                assertEquals(
                    DominoMatchPhase.RoundIntro,
                    coordinator.currentState.phase,
                )

                assertTrue(
                    traceBuffer.snapshot().none { entry ->
                        entry.event.attributes["reason"] ==
                            "presentation_watchdog_timeout"
                    },
                )
            } finally {
                coordinator.dispose()
            }
        }
    @Test
    fun stalled_move_presentation_is_recovered_by_coordinator_watchdog() =
        runBlocking {
            val openingPiece = DominoPiece(
                left = 6,
                right = 6,
            )

            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(openingPiece),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )

            val remoteRuntimeState = createRuntimeState(
                board = listOf(openingPiece),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                ),
                currentPlayerIndex = 1,
                playerHands = List(4) {
                    emptyList()
                },
            )

            val initialSnapshot = initialRuntimeState.toSnapshot(
                revision = 1L,
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialSnapshot,
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialSnapshot,
                coroutineDispatcher = Dispatchers.Unconfined,
                traceLogger = OnlineTraceLogger(
                    sink = traceBuffer,
                    nowEpochMillis = { 1_000L },
                ),
                presentationWatchdogMillis = 25L,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    remoteRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertTrue(
                    coordinator.currentState.phase
                        is DominoMatchPhase.PresentingMove,
                )

                /*
                 * Deliberately do not dispatch PresentationFinished. This is
                 * the production failure reproduced in C45.A: the server keeps
                 * advancing while the client presentation callback disappears.
                 */
                delay(100L)

                assertEquals(
                    remoteRuntimeState,
                    coordinator.currentState,
                )

                val watchdogEvent = requireNotNull(
                    traceBuffer.snapshot().singleOrNull { entry ->
                        entry.event.type ==
                            OnlineTraceType.INVARIANT_VIOLATION &&
                            entry.event.attributes["reason"] ==
                                "presentation_watchdog_timeout"
                    },
                ).event

                assertEquals(
                    "25",
                    watchdogEvent.attributes["watchdogMillis"],
                )
            } finally {
                coordinator.dispose()
            }
        }
    @Test
    fun remote_revisions_arriving_during_move_presentation_are_presented_in_order() =
        runBlocking {
            val openingPiece = DominoPiece(
                left = 6,
                right = 6,
            )
            val nextPiece = DominoPiece(
                left = 6,
                right = 5,
            )

            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(openingPiece),
                    listOf(nextPiece),
                    emptyList(),
                    emptyList(),
                ),
            )

            val firstRemoteRuntimeState = createRuntimeState(
                board = listOf(openingPiece),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                ),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    emptyList(),
                    listOf(nextPiece),
                    emptyList(),
                    emptyList(),
                ),
            )

            val secondRemoteRuntimeState = createRuntimeState(
                board = listOf(
                    openingPiece,
                    nextPiece,
                ),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                    rightPieces = listOf(nextPiece),
                ),
                currentPlayerIndex = 2,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )

            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
            )

            val traceBuffer = InMemoryOnlineTraceBuffer()

            val traceLogger = OnlineTraceLogger(
                sink = traceBuffer,
                nowEpochMillis = { 1_000L },
            )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
                traceLogger = traceLogger,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    firstRemoteRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertPresentingMove(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                    expectedPiece = openingPiece,
                )

                repository.publishMatchSnapshot(
                    secondRemoteRuntimeState.toSnapshot(
                        revision = 3L,
                    ),
                )
                yield()

                assertPresentingMove(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                    expectedPiece = openingPiece,
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertPresentingMove(
                    coordinator = coordinator,
                    expectedPlayerIndex = 1,
                    expectedPiece = nextPiece,
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertEquals(
                    secondRemoteRuntimeState,
                    coordinator.currentState,
                )

                assertEquals(
                    listOf(
                        OnlineTraceType.SNAPSHOT_RECEIVED,
                        OnlineTraceType.SNAPSHOT_ENQUEUED,
                        OnlineTraceType.PRESENTATION_STARTED,

                        OnlineTraceType.SNAPSHOT_RECEIVED,
                        OnlineTraceType.SNAPSHOT_ENQUEUED,

                        OnlineTraceType.PRESENTATION_FINISHED,
                        OnlineTraceType.STABLE_STATE_PROMOTED,
                        OnlineTraceType.PRESENTATION_STARTED,

                        OnlineTraceType.PRESENTATION_FINISHED,
                        OnlineTraceType.STABLE_STATE_PROMOTED,
                    ),
                    traceBuffer.snapshot().map { entry ->
                        entry.event.type
                    },
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun gameplay_backlog_compacts_to_latest_move_without_cancelling_active_presentation() =
        runBlocking {
            val openingPiece = DominoPiece(
                left = 6,
                right = 6,
            )
            val secondPiece = DominoPiece(
                left = 6,
                right = 5,
            )
            val thirdPiece = DominoPiece(
                left = 5,
                right = 4,
            )
            val latestPiece = DominoPiece(
                left = 4,
                right = 3,
            )

            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(openingPiece),
                    listOf(secondPiece),
                    listOf(thirdPiece),
                    listOf(latestPiece),
                ),
            )
            val firstRemoteRuntimeState = createRuntimeState(
                board = listOf(openingPiece),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                ),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    emptyList(),
                    listOf(secondPiece),
                    listOf(thirdPiece),
                    listOf(latestPiece),
                ),
            )
            val secondRemoteRuntimeState = createRuntimeState(
                board = listOf(
                    openingPiece,
                    secondPiece,
                ),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                    rightPieces = listOf(secondPiece),
                ),
                currentPlayerIndex = 2,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    listOf(thirdPiece),
                    listOf(latestPiece),
                ),
            )
            val thirdRemoteRuntimeState = createRuntimeState(
                board = listOf(
                    openingPiece,
                    secondPiece,
                    thirdPiece,
                ),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                    rightPieces = listOf(
                        secondPiece,
                        thirdPiece,
                    ),
                ),
                currentPlayerIndex = 3,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    listOf(latestPiece),
                ),
            )
            val latestRemoteRuntimeState = createRuntimeState(
                board = listOf(
                    openingPiece,
                    secondPiece,
                    thirdPiece,
                    latestPiece,
                ),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                    rightPieces = listOf(
                        secondPiece,
                        thirdPiece,
                        latestPiece,
                    ),
                ),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
                traceLogger = OnlineTraceLogger(
                    sink = traceBuffer,
                    nowEpochMillis = { 1_000L },
                ),
                catchUpPolicy = OnlinePresentationCatchUpPolicy(
                    maxQueuedGameplayPresentations = 2,
                    retainedGameplayPresentations = 1,
                ),
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    firstRemoteRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertPresentingMove(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                    expectedPiece = openingPiece,
                )

                repository.publishMatchSnapshot(
                    secondRemoteRuntimeState.toSnapshot(
                        revision = 3L,
                    ),
                )
                yield()

                repository.publishMatchSnapshot(
                    thirdRemoteRuntimeState.toSnapshot(
                        revision = 4L,
                    ),
                )
                yield()

                repository.publishMatchSnapshot(
                    latestRemoteRuntimeState.toSnapshot(
                        revision = 5L,
                    ),
                )
                yield()

                assertPresentingMove(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                    expectedPiece = openingPiece,
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertPresentingMove(
                    coordinator = coordinator,
                    expectedPlayerIndex = 3,
                    expectedPiece = latestPiece,
                )

                val compactedEvent = requireNotNull(
                    traceBuffer.snapshot().singleOrNull { entry ->
                        entry.event.type ==
                                OnlineTraceType.PRESENTATION_BACKLOG_COMPACTED
                    },
                ).event

                assertEquals(
                    "2",
                    compactedEvent.attributes["previousStableRevision"],
                )
                assertEquals(
                    "4",
                    compactedEvent.attributes["baselineRevision"],
                )
                assertEquals(
                    "1",
                    compactedEvent.attributes["retainedGameplayPresentations"],
                )
                assertTrue(
                    traceBuffer.snapshot().none { entry ->
                        entry.event.type ==
                                OnlineTraceType.PRESENTATION_HARD_RESYNC
                    },
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertEquals(
                    latestRemoteRuntimeState,
                    coordinator.currentState,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun gameplay_backlog_compacts_to_latest_pass_without_cancelling_active_presentation() =
        runBlocking {
            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) { emptyList() },
            )
            val firstRemoteRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 1,
                playerHands = List(4) { emptyList() },
            )
            val secondRemoteRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 2,
                playerHands = List(4) { emptyList() },
            )
            val thirdRemoteRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 3,
                playerHands = List(4) { emptyList() },
            )
            val latestRemoteRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = List(4) { emptyList() },
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
                traceLogger = OnlineTraceLogger(
                    sink = traceBuffer,
                    nowEpochMillis = { 1_000L },
                ),
                catchUpPolicy = OnlinePresentationCatchUpPolicy(
                    maxQueuedGameplayPresentations = 2,
                    retainedGameplayPresentations = 1,
                ),
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    firstRemoteRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                )

                repository.publishMatchSnapshot(
                    secondRemoteRuntimeState.toSnapshot(
                        revision = 3L,
                    ),
                )
                yield()

                repository.publishMatchSnapshot(
                    thirdRemoteRuntimeState.toSnapshot(
                        revision = 4L,
                    ),
                )
                yield()

                repository.publishMatchSnapshot(
                    latestRemoteRuntimeState.toSnapshot(
                        revision = 5L,
                    ),
                )
                yield()

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 3,
                )

                val compactedEvent = requireNotNull(
                    traceBuffer.snapshot().singleOrNull { entry ->
                        entry.event.type ==
                                OnlineTraceType.PRESENTATION_BACKLOG_COMPACTED
                    },
                ).event

                assertEquals(
                    "4",
                    compactedEvent.attributes["baselineRevision"],
                )
                assertEquals(
                    "1",
                    compactedEvent.attributes["retainedGameplayPresentations"],
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertEquals(
                    latestRemoteRuntimeState,
                    coordinator.currentState,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun missing_revision_history_hard_resyncs_active_presentation_and_pending_queue_to_latest_snapshot() =
        runBlocking {
            val openingPiece = DominoPiece(
                left = 6,
                right = 6,
            )
            val secondPiece = DominoPiece(
                left = 6,
                right = 5,
            )
            val latestPiece = DominoPiece(
                left = 5,
                right = 4,
            )

            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(openingPiece),
                    listOf(secondPiece),
                    listOf(latestPiece),
                    emptyList(),
                ),
            )

            val firstRemoteRuntimeState = createRuntimeState(
                board = listOf(openingPiece),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                ),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    emptyList(),
                    listOf(secondPiece),
                    listOf(latestPiece),
                    emptyList(),
                ),
            )

            val secondRemoteRuntimeState = createRuntimeState(
                board = listOf(
                    openingPiece,
                    secondPiece,
                ),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                    rightPieces = listOf(secondPiece),
                ),
                currentPlayerIndex = 2,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    listOf(latestPiece),
                    emptyList(),
                ),
            )

            val latestRemoteRuntimeState = createRuntimeState(
                board = listOf(
                    openingPiece,
                    secondPiece,
                    latestPiece,
                ),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                    rightPieces = listOf(
                        secondPiece,
                        latestPiece,
                    ),
                ),
                currentPlayerIndex = 3,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )

            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
            )

            val traceBuffer = InMemoryOnlineTraceBuffer()

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
                traceLogger = OnlineTraceLogger(
                    sink = traceBuffer,
                    nowEpochMillis = { 1_000L },
                ),
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    firstRemoteRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertPresentingMove(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                    expectedPiece = openingPiece,
                )

                repository.publishMatchSnapshot(
                    secondRemoteRuntimeState.toSnapshot(
                        revision = 3L,
                    ),
                )
                yield()

                assertPresentingMove(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                    expectedPiece = openingPiece,
                )

                repository.publishMatchSnapshot(
                    latestRemoteRuntimeState.toSnapshot(
                        revision = 6L,
                    ),
                )
                yield()

                assertEquals(
                    latestRemoteRuntimeState,
                    coordinator.currentState,
                )

                val traceTypesBeforeLateCompletion = traceBuffer.snapshot().map { entry ->
                    entry.event.type
                }

                assertEquals(
                    listOf(
                        OnlineTraceType.SNAPSHOT_RECEIVED,
                        OnlineTraceType.SNAPSHOT_ENQUEUED,
                        OnlineTraceType.PRESENTATION_STARTED,

                        OnlineTraceType.SNAPSHOT_RECEIVED,
                        OnlineTraceType.SNAPSHOT_ENQUEUED,

                        OnlineTraceType.SNAPSHOT_RECEIVED,
                        OnlineTraceType.PRESENTATION_HARD_RESYNC,
                        OnlineTraceType.STABLE_STATE_PROMOTED,
                    ),
                    traceTypesBeforeLateCompletion,
                )

                val fastForwardEvent = requireNotNull(
                    traceBuffer.snapshot().singleOrNull { entry ->
                        entry.event.type ==
                                OnlineTraceType.PRESENTATION_HARD_RESYNC
                    },
                ).event

                assertEquals(
                    "1",
                    fastForwardEvent.attributes["previousStableRevision"],
                )
                assertEquals(
                    "2",
                    fastForwardEvent.attributes["discardedQueueDepth"],
                )
                assertEquals(
                    "2",
                    fastForwardEvent.attributes["cancelledPresentationRevision"],
                )
                assertEquals(
                    "missing_revision_history",
                    fastForwardEvent.attributes["reason"],
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertEquals(
                    latestRemoteRuntimeState,
                    coordinator.currentState,
                )

                assertEquals(
                    traceTypesBeforeLateCompletion,
                    traceBuffer.snapshot().map { entry ->
                        entry.event.type
                    },
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun remote_revisions_arriving_during_pass_presentations_are_presented_in_order() =
        runBlocking {
            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )
            val firstRemoteRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )
            val secondRemoteRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 2,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
            )
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    firstRemoteRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                )

                repository.publishMatchSnapshot(
                    secondRemoteRuntimeState.toSnapshot(
                        revision = 3L,
                    ),
                )
                yield()

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 1,
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertEquals(
                    secondRemoteRuntimeState,
                    coordinator.currentState,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun confirmed_consecutive_passes_are_presented_once_each_in_fifo_order() =
        runBlocking {
            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )

            val firstPendingPassRuntimeState = initialRuntimeState.copy(
                phase = DominoMatchPhase.PresentingPass(
                    playerIndex = 0,
                ),
            )

            val secondPendingPassRuntimeState = firstPendingPassRuntimeState.copy(
                gameState = firstPendingPassRuntimeState.gameState.copy(
                    currentPlayerIndex = 1,
                ),
                phase = DominoMatchPhase.PresentingPass(
                    playerIndex = 1,
                ),
            )

            val resolvedRuntimeState = secondPendingPassRuntimeState.copy(
                gameState = secondPendingPassRuntimeState.gameState.copy(
                    currentPlayerIndex = 2,
                ),
                phase = DominoMatchPhase.WaitingForLocalMove,
            )

            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
            )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                repository.publishMatchSnapshot(
                    firstPendingPassRuntimeState.toSnapshot(
                        revision = 2L,
                    ),
                )
                yield()

                assertEquals(
                    DominoMatchPhase.WaitingForLocalMove,
                    coordinator.currentState.phase,
                )

                repository.publishMatchSnapshot(
                    secondPendingPassRuntimeState.toSnapshot(
                        revision = 3L,
                    ),
                )
                yield()

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                )

                repository.publishMatchSnapshot(
                    resolvedRuntimeState.toSnapshot(
                        revision = 4L,
                    ),
                )
                yield()

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 0,
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertPresentingPass(
                    coordinator = coordinator,
                    expectedPlayerIndex = 1,
                )

                coordinator.dispatch(
                    DominoMatchCommand.PresentationFinished,
                )

                assertEquals(
                    resolvedRuntimeState,
                    coordinator.currentState,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun client_driven_mandatory_pass_submits_snapshot_request_immediately() =
        runBlocking {
            val openingPiece = DominoPiece(
                left = 6,
                right = 6,
            )

            val runtimeState = createRuntimeState(
                board = listOf(openingPiece),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                ),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    listOf(DominoPiece(6, 5)),
                    listOf(DominoPiece(0, 0)),
                    listOf(DominoPiece(6, 4)),
                    listOf(DominoPiece(6, 3)),
                ),
            ).copy(
                phase = DominoMatchPhase.PresentingPass(
                    playerIndex = 1,
                ),
            )

            val repository =
                ClientDrivenTestOnlineRoomRepository(
                    initialSnapshot = runtimeState.toSnapshot(
                        revision = 1L,
                    ),
                )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher =
                    Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                yield()

                assertEquals(
                    1,
                    repository.submittedActions.size,
                )

                assertEquals(
                    OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT,
                    repository.submittedActions.single().type,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun client_driven_already_expired_automatic_local_turn_submits_one_snapshot_request() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(DominoPiece(6, 6)),
                    listOf(DominoPiece(1, 1)),
                    listOf(DominoPiece(2, 2)),
                    listOf(DominoPiece(3, 3)),
                ),
            ).copy(
                clockPolicy =
                    DominoMatchClockPolicy.OnlinePerPlayerRound,
                playerClockMillis = listOf(
                    0L,
                    20_000L,
                    20_000L,
                    20_000L,
                ),
                playerClockReserveMillis =
                    List(4) {
                        20_000L
                    },
            )

            val initialSnapshot =
                runtimeState.toSnapshot(
                    revision = 1L,
                ).copy(
                    automaticPlayerIndexes =
                        listOf(0),
                )

            val repository =
                ClientDrivenTestOnlineRoomRepository(
                    initialSnapshot = initialSnapshot,
                )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialSnapshot,
                coroutineDispatcher =
                    Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                coordinator.dispatch(
                    DominoMatchCommand.TurnClockTick(
                        elapsedMillis = 250L,
                    ),
                )

                coordinator.dispatch(
                    DominoMatchCommand.TurnClockTick(
                        elapsedMillis = 250L,
                    ),
                )

                yield()

                assertEquals(
                    1,
                    repository.submittedActions.size,
                )

                val action =
                    repository.submittedActions.single()

                assertEquals(
                    OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT,
                    action.type,
                )

                assertEquals(
                    1L,
                    action.revision,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun client_driven_timeout_submits_snapshot_request_at_zero() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(DominoPiece(6, 6)),
                    listOf(DominoPiece(1, 1)),
                    listOf(DominoPiece(2, 2)),
                    listOf(DominoPiece(3, 3)),
                ),
            ).copy(
                clockPolicy =
                    DominoMatchClockPolicy.OnlinePerPlayerRound,
                playerClockMillis = listOf(
                    250L,
                    20_000L,
                    20_000L,
                    20_000L,
                ),
                playerClockReserveMillis =
                    List(4) {
                        20_000L
                    },
            )

            val repository =
                ClientDrivenTestOnlineRoomRepository(
                    initialSnapshot = runtimeState.toSnapshot(
                        revision = 1L,
                    ),
                )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher =
                    Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                coordinator.dispatch(
                    DominoMatchCommand.TurnClockTick(
                        elapsedMillis = 250L,
                    ),
                )

                yield()

                assertEquals(
                    1,
                    repository.submittedActions.size,
                )

                assertEquals(
                    OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT,
                    repository.submittedActions.single().type,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun client_driven_application_turn_submits_snapshot_request() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    listOf(DominoPiece(0, 0)),
                    listOf(DominoPiece(6, 6)),
                    listOf(DominoPiece(1, 1)),
                    listOf(DominoPiece(2, 2)),
                ),
                participantTypes = listOf(
                    DominoParticipantType.HUMAN,
                    DominoParticipantType.APPLICATION,
                    DominoParticipantType.HUMAN,
                    DominoParticipantType.HUMAN,
                ),
            )

            val repository =
                ClientDrivenTestOnlineRoomRepository(
                    initialSnapshot = runtimeState.toSnapshot(
                        revision = 1L,
                    ),
                )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher =
                    Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                coordinator.dispatch(
                    DominoMatchCommand.BotDecisionReady,
                )

                yield()

                assertEquals(
                    1,
                    repository.submittedActions.size,
                )

                val action =
                    repository.submittedActions.single()

                assertEquals(
                    OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT,
                    action.type,
                )

                assertEquals(
                    1L,
                    action.revision,
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun client_driven_human_turn_does_not_submit_snapshot_request() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    listOf(DominoPiece(0, 0)),
                    listOf(DominoPiece(6, 6)),
                    listOf(DominoPiece(1, 1)),
                    listOf(DominoPiece(2, 2)),
                ),
                participantTypes =
                    List(4) {
                        DominoParticipantType.HUMAN
                    },
            )

            val repository =
                ClientDrivenTestOnlineRoomRepository(
                    initialSnapshot = runtimeState.toSnapshot(
                        revision = 1L,
                    ),
                )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher =
                    Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                coordinator.dispatch(
                    DominoMatchCommand.BotDecisionReady,
                )

                yield()

                assertTrue(
                    repository.submittedActions.isEmpty(),
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun server_authoritative_repository_ignores_bot_decision_ready() =
        runBlocking {
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 1,
                playerHands = listOf(
                    listOf(DominoPiece(0, 0)),
                    listOf(DominoPiece(6, 6)),
                    listOf(DominoPiece(1, 1)),
                    listOf(DominoPiece(2, 2)),
                ),
                participantTypes = listOf(
                    DominoParticipantType.HUMAN,
                    DominoParticipantType.APPLICATION,
                    DominoParticipantType.HUMAN,
                    DominoParticipantType.HUMAN,
                ),
            )

            val repository = TestOnlineRoomRepository(
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
            )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher =
                    Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                coordinator.dispatch(
                    DominoMatchCommand.BotDecisionReady,
                )

                yield()

                assertTrue(
                    repository.submittedActions.isEmpty(),
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun local_move_is_submitted_once_until_authoritative_revision_confirms_it() =
        runBlocking {
            val openingPiece = DominoPiece(
                left = 6,
                right = 6,
            )

            val initialRuntimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(openingPiece),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )

            val repository = TestOnlineRoomRepository(
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
            )

            val traceBuffer = InMemoryOnlineTraceBuffer()

            val traceLogger = OnlineTraceLogger(
                sink = traceBuffer,
                nowEpochMillis = { 1_000L },
            )

            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 1L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
                traceLogger = traceLogger,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )

                val move = PlayableMove(
                    piece = openingPiece,
                    side = BoardSide.RIGHT,
                    flipped = false,
                )

                coordinator.dispatch(
                    DominoMatchCommand.LocalMoveSelected(
                        move = move,
                    ),
                )

                coordinator.dispatch(
                    DominoMatchCommand.LocalMoveSelected(
                        move = move,
                    ),
                )

                yield()

                assertEquals(
                    1,
                    repository.submittedActions.size,
                )

                assertEquals(
                    OnlinePlayerActionTypeDto.PLAY_MOVE,
                    repository.submittedActions.single().type,
                )

                coordinator.dispatch(
                    DominoMatchCommand.BotDecisionReady,
                )
                yield()

                assertEquals(
                    1,
                    repository.submittedActions.size,
                )

                val traceEntries = traceBuffer.snapshot()
                val traceTypes = traceEntries.map { entry ->
                    entry.event.type
                }

                assertTrue(
                    "A ação local deveria ser preparada.",
                    traceTypes.contains(OnlineTraceType.ACTION_PREPARED),
                )

                assertTrue(
                    "A ação local deveria ser submetida.",
                    traceTypes.contains(OnlineTraceType.ACTION_SUBMITTED),
                )

                assertTrue(
                    "A segunda ação deveria ser suprimida enquanto a primeira está em voo.",
                    traceTypes.contains(OnlineTraceType.ACTION_SUPPRESSED),
                )

                assertTrue(
                    "A primeira ação deveria ser aceita pelo repositório de teste.",
                    traceTypes.contains(OnlineTraceType.ACTION_ACCEPTED),
                )

                val suppressedAction = traceEntries.firstOrNull { entry ->
                    entry.event.type == OnlineTraceType.ACTION_SUPPRESSED
                }

                assertEquals(
                    "in_flight_action",
                    requireNotNull(suppressedAction).event.attributes["reason"],
                )
            } finally {
                coordinator.dispose()
            }
        }

    @Test
    fun ui_trace_is_correlated_with_the_current_snapshot_and_state_fingerprint() =
        runBlocking {
            val openingPiece = DominoPiece(
                left = 6,
                right = 6,
            )
            val runtimeState = createRuntimeState(
                board = emptyList(),
                boardChain = DominoBoardChain(),
                currentPlayerIndex = 0,
                playerHands = listOf(
                    listOf(openingPiece),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )
            val repository = TestOnlineRoomRepository(
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 7L,
                ),
            )
            val traceBuffer = InMemoryOnlineTraceBuffer()
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = runtimeState.toSnapshot(
                    revision = 7L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
                traceLogger = OnlineTraceLogger(
                    sink = traceBuffer,
                ),
            )

            try {
                coordinator.traceUiEvent(
                    type = OnlineTraceType.ANIMATION_TARGET_PENDING,
                    traceContext = coordinator.currentUiTraceContext(),
                    attributes = mapOf(
                        "animationKind" to "move",
                    ),
                )

                val event = traceBuffer.snapshot().single().event

                assertEquals(
                    OnlineTraceSource.CLIENT_UI,
                    event.source,
                )
                assertEquals(
                    OnlineTraceType.ANIMATION_TARGET_PENDING,
                    event.type,
                )
                assertEquals(
                    7L,
                    event.context.snapshotRevision,
                )
                assertTrue(
                    event.state?.stateFingerprint?.isNotBlank() == true,
                )
            } finally {
                coordinator.dispose()
            }
        }

    private fun assertPresentingPass(
        coordinator: OnlineDominoMatchCoordinator,
        expectedPlayerIndex: Int,
    ) {
        val phase = coordinator.currentState.phase

        assertTrue(
            "A revisão remota deveria estar em apresentação de toque.",
            phase is DominoMatchPhase.PresentingPass,
        )

        phase as DominoMatchPhase.PresentingPass

        assertEquals(
            expectedPlayerIndex,
            phase.playerIndex,
        )
    }

    private fun assertPresentingMove(
        coordinator: OnlineDominoMatchCoordinator,
        expectedPlayerIndex: Int,
        expectedPiece: DominoPiece,
    ) {
        val phase = coordinator.currentState.phase

        assertTrue(
            "A revisão remota deveria estar em apresentação de jogada.",
            phase is DominoMatchPhase.PresentingMove,
        )

        phase as DominoMatchPhase.PresentingMove

        assertEquals(
            expectedPlayerIndex,
            phase.playerIndex,
        )

        assertEquals(
            expectedPiece,
            phase.move.piece,
        )
    }

    private fun createRuntimeState(
        board: List<DominoPiece>,
        boardChain: DominoBoardChain,
        currentPlayerIndex: Int,
        playerHands: List<List<DominoPiece>>,
        participantTypes: List<DominoParticipantType> =
            List(playerHands.size) {
                DominoParticipantType.HUMAN
            },
    ): DominoMatchRuntimeState {
        return DominoMatchRuntimeState(
            gameState = DominoGameState(
                board = board,
                boardChain = boardChain,
                players = playerHands.mapIndexed { index, hand ->
                    DominoPlayer(
                        id = index,
                        name = "Jogador ${index + 1}",
                        hand = hand,
                        participantType =
                            participantTypes[index],
                    )
                },
                sleepingPieces = emptyList(),
                currentPlayerIndex = currentPlayerIndex,
                lastRoundWinnerIndex = null,
                openingPiece = null,
                teamScores = listOf(0, 0),
                lastMove = null,
                roundWinnerPlayerIndex = null,
                roundWinnerTeamIndex = null,
                roundWinKind = null,
                gameWinnerTeamIndex = null,
            ),
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.WaitingForLocalMove,
        )
    }

    private fun DominoMatchRuntimeState.toSnapshot(
        revision: Long,
    ): OnlineMatchSnapshotDto {
        return toOnlineSnapshotDto(
            roomId = TEST_ROOM_ID,
            matchId = TEST_MATCH_ID,
            revision = revision,
        )
    }

    private open class TestOnlineRoomRepository(
        initialSnapshot: OnlineMatchSnapshotDto,
    ) : OnlineRoomRepository {
        private val mutableRoomSnapshot =
            MutableStateFlow<OnlineRoomSnapshotDto?>(null)

        private val mutableMatchSnapshot =
            MutableStateFlow<OnlineMatchSnapshotDto?>(initialSnapshot)

        private val mutableActiveMatchSessionInvalidationEvents =
            MutableSharedFlow<OnlineActiveMatchSessionInvalidation>(
                extraBufferCapacity = 8,
            )

        private val mutableActiveMatchResourceLossEvents =
            MutableSharedFlow<OnlineActiveMatchResourceLoss>(
                extraBufferCapacity = 8,
            )

        private val mutableActiveMatchParticipationAuthorizationLossEvents =
            MutableSharedFlow<OnlineActiveMatchParticipationAuthorizationLoss>(
                extraBufferCapacity = 8,
            )

        val submittedActions = mutableListOf<OnlinePlayerActionDto>()

        var completedMatchLocalReleaseCount = 0
            private set

        override val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?> =
            mutableRoomSnapshot.asStateFlow()

        override val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?> =
            mutableMatchSnapshot.asStateFlow()

        override val activeMatchSessionInvalidationEvents:
            Flow<OnlineActiveMatchSessionInvalidation> =
            mutableActiveMatchSessionInvalidationEvents.asSharedFlow()

        override val activeMatchResourceLossEvents:
            Flow<OnlineActiveMatchResourceLoss> =
            mutableActiveMatchResourceLossEvents.asSharedFlow()

        override val activeMatchParticipationAuthorizationLossEvents:
            Flow<OnlineActiveMatchParticipationAuthorizationLoss> =
            mutableActiveMatchParticipationAuthorizationLossEvents.asSharedFlow()

        fun publishMatchSnapshot(
            snapshot: OnlineMatchSnapshotDto,
        ) {
            mutableMatchSnapshot.value = snapshot
        }

        fun publishActiveMatchSessionInvalidation(
            invalidation: OnlineActiveMatchSessionInvalidation,
        ) {
            check(
                mutableActiveMatchSessionInvalidationEvents
                    .tryEmit(invalidation),
            )
        }

        fun publishActiveMatchResourceLoss(
            resourceLoss: OnlineActiveMatchResourceLoss,
        ) {
            check(
                mutableActiveMatchResourceLossEvents
                    .tryEmit(resourceLoss),
            )
        }

        fun publishActiveMatchParticipationAuthorizationLoss(
            authorizationLoss:
                OnlineActiveMatchParticipationAuthorizationLoss,
        ) {
            check(
                mutableActiveMatchParticipationAuthorizationLossEvents
                    .tryEmit(authorizationLoss),
            )
        }

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
            submittedActions += action

            return OnlineActionResultDto(
                accepted = true,
                revision = mutableMatchSnapshot.value?.revision,
            )
        }

        override fun releaseCompletedMatchLocally() {
            completedMatchLocalReleaseCount += 1
        }

        override suspend fun leaveRoom() = Unit
    }

    private class ClientDrivenTestOnlineRoomRepository(
        initialSnapshot: OnlineMatchSnapshotDto,
    ) : TestOnlineRoomRepository(
        initialSnapshot = initialSnapshot,
    ),
        OnlineClientDrivenFakeProgression

    private companion object {
        const val TEST_ROOM_ID = "room-1"
        const val TEST_MATCH_ID = "match-1"
        const val TEST_PLAYER_ID = "player-1"
    }
}