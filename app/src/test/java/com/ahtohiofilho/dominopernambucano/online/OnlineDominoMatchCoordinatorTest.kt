package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
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

                val move = com.ahtohiofilho.dominopernambucano.domain.PlayableMove(
                    piece = openingPiece,
                    side = com.ahtohiofilho.dominopernambucano.domain.BoardSide.RIGHT,
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

                assertEquals(1, repository.submittedActions.size)
                assertEquals(
                    OnlinePlayerActionTypeDto.PLAY_MOVE,
                    repository.submittedActions.single().type,
                )

                coordinator.dispatch(
                    DominoMatchCommand.BotDecisionReady,
                )
                yield()

                assertEquals(1, repository.submittedActions.size)
            } finally {
                coordinator.dispose()
            }
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
