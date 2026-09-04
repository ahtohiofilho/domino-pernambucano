package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.domain.getNextCounterClockwisePlayerIndex
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.domain.playMoveForCurrentPlayer
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOnlineClientDrivenProgressionTest {
    @Test
    fun snapshot_request_advances_application_turn_without_timeout() =
        runBlocking {
            val repository = FakeOnlineRoomRepository(
                nowEpochMillis = {
                    1_000L
                },
            )

            val createdRoom = requireNotNull(
                repository.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "human-host",
                        playerName = "Humano",
                    ),
                ).roomSnapshot,
            )

            val completion:
                OnlineDevelopmentParticipantCompletion =
                repository

            repeat(3) { applicationIndex ->
                val result =
                    completion.addApplicationParticipant(
                        OnlineDevelopmentParticipantRequest(
                            roomCode = createdRoom.roomCode,
                            playerId =
                                "opaque-application-${applicationIndex + 1}",
                            playerName =
                                "Aplicativo ${applicationIndex + 1}",
                        ),
                    )

                assertTrue(result.accepted)
            }

            var snapshot = requireNotNull(
                repository.matchSnapshot.value,
            )

            var runtimeState = snapshot.toRuntimeState(
                localPlayerIndex = 0,
            )

            var room = requireNotNull(
                repository.roomSnapshot.value,
            )

            var currentParticipant =
                findCurrentParticipant(
                    room = room,
                    gameState = runtimeState.gameState,
                )

            if (
                currentParticipant.participantType ==
                OnlineParticipantTypeDto.HUMAN
            ) {
                val humanAction =
                    createCurrentTurnAction(
                        snapshot = snapshot,
                        gameState = runtimeState.gameState,
                        playerId =
                            currentParticipant.playerId,
                        actionId =
                            "prepare-application-turn",
                    )

                val humanResult =
                    repository.submitAction(humanAction)

                assertTrue(
                    "Falha ao preparar o turno APPLICATION: ${humanResult.reason}",
                    humanResult.accepted,
                )

                snapshot = requireNotNull(
                    repository.matchSnapshot.value,
                )

                runtimeState = snapshot.toRuntimeState(
                    localPlayerIndex = 0,
                )

                room = requireNotNull(
                    repository.roomSnapshot.value,
                )

                currentParticipant =
                    findCurrentParticipant(
                        room = room,
                        gameState =
                            runtimeState.gameState,
                    )
            }

            assertEquals(
                OnlineParticipantTypeDto.APPLICATION,
                currentParticipant.participantType,
            )

            val previousRuntimeState = runtimeState
            val previousRevision = snapshot.revision

            val expectedMove = findBasicBotMove(
                state = previousRuntimeState.gameState,
            )

            val result = repository.submitAction(
                createOnlineSnapshotRequestAction(
                    roomId = snapshot.roomId,
                    matchId = snapshot.matchId,
                    playerId = "human-host",
                    revision = snapshot.revision,
                    actionId =
                        "client-driven-application-turn",
                ),
            )

            assertTrue(
                "A progressao APPLICATION foi recusada: ${result.reason}",
                result.accepted,
            )

            val updatedSnapshot = requireNotNull(
                repository.matchSnapshot.value,
            )

            val updatedRuntimeState =
                updatedSnapshot.toRuntimeState(
                    localPlayerIndex = 0,
                )

            assertEquals(
                previousRevision + 1L,
                updatedSnapshot.revision,
            )

            if (expectedMove != null) {
                val expectedGameState =
                    playMoveForCurrentPlayer(
                        state = previousRuntimeState.gameState,
                        playableMove = expectedMove,
                    )

                assertEquals(
                    expectedGameState,
                    updatedRuntimeState.gameState,
                )
            } else {
                assertEquals(
                    getNextCounterClockwisePlayerIndex(
                        currentPlayerIndex =
                            previousRuntimeState
                                .gameState
                                .currentPlayerIndex,
                        playerCount =
                            previousRuntimeState
                                .gameState
                                .players
                                .size,
                    ),
                    updatedRuntimeState
                        .gameState
                        .currentPlayerIndex,
                )
            }

            assertTrue(
                updatedSnapshot
                    .automaticPlayerIndexes
                    .isEmpty(),
            )
        }

    @Test
    fun application_turn_reloads_main_clock_from_reserve() =
        runBlocking {
            var nowEpochMillis = 1_000L

            val repository = FakeOnlineRoomRepository(
                nowEpochMillis = {
                    nowEpochMillis
                },
            )

            val createdRoom = requireNotNull(
                repository.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "human-host",
                        playerName = "Humano",
                    ),
                ).roomSnapshot,
            )

            val completion:
                OnlineDevelopmentParticipantCompletion =
                repository

            repeat(3) { applicationIndex ->
                val result =
                    completion.addApplicationParticipant(
                        OnlineDevelopmentParticipantRequest(
                            roomCode = createdRoom.roomCode,
                            playerId =
                                "clock-application-${applicationIndex + 1}",
                            playerName =
                                "Aplicativo ${applicationIndex + 1}",
                        ),
                    )

                assertTrue(result.accepted)
            }

            var snapshot = requireNotNull(
                repository.matchSnapshot.value,
            )

            var runtimeState = snapshot.toRuntimeState(
                localPlayerIndex = 0,
            )

            var room = requireNotNull(
                repository.roomSnapshot.value,
            )

            var currentParticipant =
                findCurrentParticipant(
                    room = room,
                    gameState = runtimeState.gameState,
                )

            if (
                currentParticipant.participantType ==
                OnlineParticipantTypeDto.HUMAN
            ) {
                val humanAction =
                    createCurrentTurnAction(
                        snapshot = snapshot,
                        gameState = runtimeState.gameState,
                        playerId =
                            currentParticipant.playerId,
                        actionId =
                            "prepare-clock-application-turn",
                    )

                val humanResult =
                    repository.submitAction(humanAction)

                assertTrue(
                    "Falha ao preparar o turno APPLICATION: ${humanResult.reason}",
                    humanResult.accepted,
                )

                snapshot = requireNotNull(
                    repository.matchSnapshot.value,
                )

                runtimeState = snapshot.toRuntimeState(
                    localPlayerIndex = 0,
                )

                room = requireNotNull(
                    repository.roomSnapshot.value,
                )

                currentParticipant =
                    findCurrentParticipant(
                        room = room,
                        gameState =
                            runtimeState.gameState,
                    )
            }

            /*
             * This test verifies reserve reload after an APPLICATION actually
             * plays a legal move. The shuffled initial deal can legitimately
             * leave an APPLICATION without a move, in which case PASS semantics
             * preserve the clock/reserve instead of reloading it.
             *
             * Advance zero-time turns until the current APPLICATION has a
             * playable move, keeping this test deterministic without changing
             * production behavior.
             */
            var preparationTurns = 0
            while (
                currentParticipant.participantType !=
                    OnlineParticipantTypeDto.APPLICATION ||
                findFirstPlayableMoveOrNull(
                    gameState = runtimeState.gameState,
                ) == null
            ) {
                preparationTurns += 1

                assertTrue(
                    "Não foi possível preparar um turno APPLICATION jogável.",
                    preparationTurns <= 8,
                )

                val preparationAction =
                    createCurrentTurnAction(
                        snapshot = snapshot,
                        gameState = runtimeState.gameState,
                        playerId = currentParticipant.playerId,
                        actionId =
                            "prepare-playable-application-$preparationTurns",
                    )

                val preparationResult =
                    repository.submitAction(preparationAction)

                assertTrue(
                    "Falha ao preparar APPLICATION jogável: ${preparationResult.reason}",
                    preparationResult.accepted,
                )

                snapshot = requireNotNull(
                    repository.matchSnapshot.value,
                )

                runtimeState = snapshot.toRuntimeState(
                    localPlayerIndex = 0,
                )

                room = requireNotNull(
                    repository.roomSnapshot.value,
                )

                currentParticipant =
                    findCurrentParticipant(
                        room = room,
                        gameState = runtimeState.gameState,
                    )
            }

            assertEquals(
                OnlineParticipantTypeDto.APPLICATION,
                currentParticipant.participantType,
            )

            assertTrue(
                findFirstPlayableMoveOrNull(
                    gameState = runtimeState.gameState,
                ) != null,
            )

            val applicationPlayerIndex =
                requireNotNull(
                    currentParticipant.seatIndex,
                )

            val reserveBeforeTurn =
                snapshot.playerClockReserveMillis[
                    applicationPlayerIndex
                ]

            assertEquals(
                20_000L,
                snapshot.playerClockMillis[
                    applicationPlayerIndex
                ],
            )

            nowEpochMillis += 2_000L

            val result = repository.submitAction(
                createOnlineSnapshotRequestAction(
                    roomId = snapshot.roomId,
                    matchId = snapshot.matchId,
                    playerId = "human-host",
                    revision = snapshot.revision,
                    actionId =
                        "application-clock-parity",
                ),
            )

            assertTrue(
                "O turno APPLICATION foi recusado: ${result.reason}",
                result.accepted,
            )

            val updatedSnapshot = requireNotNull(
                repository.matchSnapshot.value,
            )

            assertEquals(
                20_000L,
                updatedSnapshot.playerClockMillis[
                    applicationPlayerIndex
                ],
            )

            assertEquals(
                reserveBeforeTurn - 2_000L,
                updatedSnapshot.playerClockReserveMillis[
                    applicationPlayerIndex
                ],
            )

            assertTrue(
                updatedSnapshot
                    .automaticPlayerIndexes
                    .isEmpty(),
            )
        }

    @Test
    fun pass_turn_preserves_main_clock_and_reserve() {
        val openingPiece = DominoPiece(
            left = 6,
            right = 6,
        )

        val runtimeState = DominoMatchRuntimeState(
            gameState = DominoGameState(
                board = listOf(openingPiece),
                boardChain = DominoBoardChain(
                    openingPiece = openingPiece,
                ),
                players = listOf(
                    DominoPlayer(
                        id = 0,
                        name = "Humano",
                        hand = listOf(
                            DominoPiece(6, 5),
                        ),
                        participantType =
                            DominoParticipantType.HUMAN,
                    ),
                    DominoPlayer(
                        id = 1,
                        name = "Aplicativo sem jogada",
                        hand = listOf(
                            DominoPiece(0, 0),
                        ),
                        participantType =
                            DominoParticipantType.APPLICATION,
                    ),
                    DominoPlayer(
                        id = 2,
                        name = "Aplicativo seguinte",
                        hand = listOf(
                            DominoPiece(6, 4),
                        ),
                        participantType =
                            DominoParticipantType.APPLICATION,
                    ),
                    DominoPlayer(
                        id = 3,
                        name = "Aplicativo final",
                        hand = listOf(
                            DominoPiece(6, 3),
                        ),
                        participantType =
                            DominoParticipantType.APPLICATION,
                    ),
                ),
                sleepingPieces = emptyList(),
                currentPlayerIndex = 1,
                lastRoundWinnerIndex = null,
                openingPiece = openingPiece,
                teamScores = listOf(0, 0),
                lastMove = null,
                roundWinnerPlayerIndex = null,
                roundWinnerTeamIndex = null,
                roundWinKind = null,
                gameWinnerTeamIndex = null,
            ),
            roundNumber = 1,
            localPlayerIndex = 1,
            phase = DominoMatchPhase.PresentingPass(
                playerIndex = 1,
            ),
            clockPolicy =
                DominoMatchClockPolicy.OnlinePerPlayerRound,
            playerClockMillis = listOf(
                20_000L,
                17_500L,
                20_000L,
                20_000L,
            ),
            playerClockReserveMillis = listOf(
                20_000L,
                15_000L,
                20_000L,
                20_000L,
            ),
        )

        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = "pass-room",
            matchId = "pass-match",
            revision = 7L,
            serverEpochMillis = 1_000L,
        )

        val reduction = reduceOnlineGameAction(
            action = createOnlinePassTurnAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = "application-without-move",
                revision = snapshot.revision,
                actionId = "mandatory-pass",
            ),
            currentSnapshot = snapshot,
            seatIndex = 1,
        )

        assertTrue(
            reduction is
                    OnlineMatchActionReduction.Accepted,
        )

        val updatedRuntimeState =
            (
                reduction as
                        OnlineMatchActionReduction.Accepted
            ).runtimeState

        assertEquals(
            runtimeState.playerClockMillis,
            updatedRuntimeState.playerClockMillis,
        )

        assertEquals(
            runtimeState.playerClockReserveMillis,
            updatedRuntimeState.playerClockReserveMillis,
        )

        assertEquals(
            getNextCounterClockwisePlayerIndex(
                currentPlayerIndex =
                    runtimeState.gameState.currentPlayerIndex,
                playerCount =
                    runtimeState.gameState.players.size,
            ),
            updatedRuntimeState
                .gameState
                .currentPlayerIndex,
        )
    }

    @Test
    fun application_decision_delay_is_two_seconds() {
        assertEquals(
            2_000L,
            DominoMatchTiming.BotDecisionDelayMillis,
        )
    }

    private fun findCurrentParticipant(
        room: OnlineRoomSnapshotDto,
        gameState: DominoGameState,
    ): OnlineRoomPlayerDto {
        return requireNotNull(
            room.players.firstOrNull { player ->
                player.seatIndex ==
                        gameState.currentPlayerIndex
            },
        )
    }

    private fun createCurrentTurnAction(
        snapshot: OnlineMatchSnapshotDto,
        gameState: DominoGameState,
        playerId: String,
        actionId: String,
    ): OnlinePlayerActionDto {
        val move = findFirstPlayableMoveOrNull(
            gameState = gameState,
        )

        return if (move != null) {
            createOnlinePlayMoveAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = playerId,
                revision = snapshot.revision,
                move = move,
                actionId = actionId,
            )
        } else {
            createOnlinePassTurnAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = playerId,
                revision = snapshot.revision,
                actionId = actionId,
            )
        }
    }

    private fun findFirstPlayableMoveOrNull(
        gameState: DominoGameState,
    ): PlayableMove? {
        val currentPlayer =
            gameState.players[
                gameState.currentPlayerIndex
            ]

        return currentPlayer.hand
            .firstNotNullOfOrNull { piece ->
                getPlayableMoves(
                    board = gameState.board,
                    piece = piece,
                    openingPiece =
                        gameState.openingPiece,
                ).firstOrNull()
            }
    }
}
