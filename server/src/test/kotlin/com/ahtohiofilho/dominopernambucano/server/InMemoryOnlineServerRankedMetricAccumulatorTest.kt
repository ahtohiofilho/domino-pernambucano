package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.createOnlinePlayMoveAction
import com.ahtohiofilho.dominopernambucano.online.createOnlineStartNextRoundAction
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOnlineServerRankedMetricAccumulatorTest {
    @Test
    fun automatic_round_survives_reconnection_and_control_reclaim() {
        var now = 1_000L
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val room = startFourHumanMatch(store)
        val matchId = requireNotNull(room.matchId)
        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(matchId),
        )
        val expiredSeatIndex = initialSnapshot.gameState.currentPlayerIndex
        val expiredPlayer = requireNotNull(
            room.players.firstOrNull { player ->
                player.seatIndex == expiredSeatIndex
            },
        )

        now += 31_000L
        assertTrue(store.advanceAuthoritativeTime())

        val automaticSnapshot = requireNotNull(
            store.getMatchSnapshot(matchId),
        )
        assertTrue(
            expiredSeatIndex in automaticSnapshot.automaticPlayerIndexes,
        )

        val reconnectResult = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = expiredPlayer.playerId,
                playerName = expiredPlayer.name,
            ),
        )
        assertTrue(reconnectResult.accepted)

        val reclaimedSnapshot = requireNotNull(
            store.getMatchSnapshot(matchId),
        )
        assertTrue(
            expiredSeatIndex !in reclaimedSnapshot.automaticPlayerIndexes,
        )

        playUntilRoundSummary(
            store = store,
            room = room,
            matchId = matchId,
        )

        val accumulator = requireNotNull(
            store.getRankedMatchMetricAccumulator(matchId),
        )
        assertEquals(
            1,
            accumulator.seatMetrics[expiredSeatIndex].automaticRounds,
        )
    }

    @Test
    fun authoritative_runtime_accumulates_each_completed_round_once() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val room = startFourHumanMatch(store)
        val matchId = requireNotNull(room.matchId)

        val firstRoundSnapshot = playUntilRoundSummary(
            store = store,
            room = room,
            matchId = matchId,
        )
        val afterFirstRound = requireNotNull(
            store.getRankedMatchMetricAccumulator(matchId),
        )

        assertEquals(1, afterFirstRound.completedRounds)
        assertEquals(
            firstRoundSnapshot.gameState.teamScores.sum(),
            accumulatedScoringPoints(afterFirstRound),
        )
        assertTrue(
            afterFirstRound.seatMetrics.all { metrics ->
                metrics.automaticRounds == 0
            },
        )

        val startNextRoundResult = store.submitAction(
            createOnlineStartNextRoundAction(
                roomId = room.roomId,
                matchId = matchId,
                playerId = room.hostPlayerId,
                revision = firstRoundSnapshot.revision,
                actionId = "start-second-ranked-metric-round",
            ),
        )

        assertTrue(startNextRoundResult.accepted)
        assertEquals(
            afterFirstRound,
            store.getRankedMatchMetricAccumulator(matchId),
        )

        val secondRoundSnapshot = playUntilRoundSummary(
            store = store,
            room = room,
            matchId = matchId,
        )
        val afterSecondRound = requireNotNull(
            store.getRankedMatchMetricAccumulator(matchId),
        )

        assertEquals(2, afterSecondRound.completedRounds)
        assertEquals(
            secondRoundSnapshot.gameState.teamScores.sum(),
            accumulatedScoringPoints(afterSecondRound),
        )
    }

    private fun accumulatedScoringPoints(
        accumulator: com.ahtohiofilho.dominopernambucano.competitive.RankedMatchMetricAccumulator,
    ): Int {
        return accumulator.seatMetrics.sumOf { metrics ->
            metrics.individualPoints
        } + accumulator.collectiveCountPointsByTeam.sum()
    }

    private fun playUntilRoundSummary(
        store: InMemoryOnlineServerStore,
        room: OnlineRoomSnapshotDto,
        matchId: String,
    ): OnlineMatchSnapshotDto {
        repeat(512) { step ->
            val snapshot = requireNotNull(
                store.getMatchSnapshot(matchId),
            )
            val runtimeState = snapshot.toRuntimeState(
                localPlayerIndex = snapshot.gameState.currentPlayerIndex,
            )

            if (
                runtimeState.phase == DominoMatchPhase.RoundSummary ||
                runtimeState.phase == DominoMatchPhase.MatchFinished
            ) {
                return snapshot
            }

            if (runtimeState.phase is DominoMatchPhase.PresentingPass) {
                assertTrue(store.advanceAuthoritativeTime())
                return@repeat
            }

            assertEquals(
                DominoMatchPhase.WaitingForLocalMove,
                runtimeState.phase,
            )

            val currentSeatIndex = snapshot.gameState.currentPlayerIndex
            val currentPlayer = requireNotNull(
                room.players.firstOrNull { player ->
                    player.seatIndex == currentSeatIndex
                },
            )
            val move = requireNotNull(
                findBasicBotMove(runtimeState.gameState),
            )

            val result = store.submitAction(
                createOnlinePlayMoveAction(
                    roomId = room.roomId,
                    matchId = matchId,
                    playerId = currentPlayer.playerId,
                    revision = snapshot.revision,
                    move = move,
                    actionId = "ranked-metric-move-$step-${snapshot.revision}",
                ),
            )

            if (!result.accepted) {
                assertTrue(
                    result.reason == "Tempo esgotado." ||
                            result.reason == "Snapshot desatualizado.",
                )
                assertTrue(
                    requireNotNull(
                        store.getMatchSnapshot(matchId),
                    ).revision > snapshot.revision,
                )
            }
        }

        error("A rodada não terminou dentro do limite de ações de teste.")
    }

    private fun startFourHumanMatch(
        store: InMemoryOnlineServerStore,
    ): OnlineRoomSnapshotDto {
        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        listOf(2, 3).forEach { playerNumber ->
            val result = store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "player-$playerNumber",
                    playerName = "Jogador $playerNumber",
                ),
            )
            assertTrue(result.accepted)
        }

        val fullWaitingRoom = requireNotNull(
            store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "player-4",
                    playerName = "Jogador 4",
                ),
            ).roomSnapshot,
        )

        return requireNotNull(
            store.startPrivateRoom(
                PrivateRoomStartRequestDto(
                    roomId = fullWaitingRoom.roomId,
                    localPlayerId = "player-1",
                ),
            ).roomSnapshot,
        )
    }
}
