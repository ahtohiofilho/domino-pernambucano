package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.online.observability.InMemoryOnlineTraceBuffer
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineDominoMatchCoordinatorTest {
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

    @Test
    fun lifecycle_reconciliation_suppresses_actions_until_target_revision_is_stable() =
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
                    revision = 7L,
                ),
            )
            val coordinator = OnlineDominoMatchCoordinator(
                repository = repository,
                roomId = TEST_ROOM_ID,
                matchId = TEST_MATCH_ID,
                localPlayerId = TEST_PLAYER_ID,
                localPlayerIndex = 0,
                initialSnapshot = initialRuntimeState.toSnapshot(
                    revision = 7L,
                ),
                coroutineDispatcher = Dispatchers.Unconfined,
            )

            try {
                coordinator.dispatch(
                    DominoMatchCommand.RoundIntroFinished,
                )
                coordinator.beginLifecycleReconciliation()
                coordinator.completeLifecycleReconciliation(
                    requiredRevision = 8L,
                )

                coordinator.dispatch(
                    DominoMatchCommand.LocalMoveSelected(
                        move = PlayableMove(
                            piece = openingPiece,
                            side = BoardSide.RIGHT,
                            flipped = false,
                        ),
                    ),
                )

                assertTrue(
                    coordinator.lifecycleReconciliationInProgress.value,
                )
                assertEquals(
                    emptyList<OnlinePlayerActionDto>(),
                    repository.submittedActions,
                )

                repository.publishMatchSnapshot(
                    initialRuntimeState.toSnapshot(
                        revision = 8L,
                    ),
                )
                yield()

                assertTrue(
                    !coordinator.lifecycleReconciliationInProgress.value,
                )

                coordinator.dispatch(
                    DominoMatchCommand.LocalMoveSelected(
                        move = PlayableMove(
                            piece = openingPiece,
                            side = BoardSide.RIGHT,
                            flipped = false,
                        ),
                    ),
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

    private class TestOnlineRoomRepository(
        initialSnapshot: OnlineMatchSnapshotDto,
    ) : OnlineRoomRepository {
        private val mutableRoomSnapshot =
            MutableStateFlow<OnlineRoomSnapshotDto?>(null)

        private val mutableMatchSnapshot =
            MutableStateFlow<OnlineMatchSnapshotDto?>(initialSnapshot)

        val submittedActions = mutableListOf<OnlinePlayerActionDto>()

        override val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?> =
            mutableRoomSnapshot.asStateFlow()

        override val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?> =
            mutableMatchSnapshot.asStateFlow()

        fun publishMatchSnapshot(
            snapshot: OnlineMatchSnapshotDto,
        ) {
            mutableMatchSnapshot.value = snapshot
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

        override suspend fun leaveRoom() = Unit
    }

    private companion object {
        const val TEST_ROOM_ID = "room-1"
        const val TEST_MATCH_ID = "match-1"
        const val TEST_PLAYER_ID = "player-1"
    }
}