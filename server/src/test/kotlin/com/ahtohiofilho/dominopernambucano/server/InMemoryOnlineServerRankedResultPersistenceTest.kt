package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchClassification
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOnlineServerRankedResultPersistenceTest {
    @Test
    fun current_room_flow_remains_unranked_by_default() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val room = startFourHumanMatch(store)
        val matchId = requireNotNull(room.matchId)

        assertEquals(
            RankedMatchClassification.UNRANKED,
            store.getRankedMatchClassification(matchId),
        )
        assertNull(store.getRankedMatchResult(matchId))
    }

    @Test
    fun accumulator_survives_json_restart() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val room = startFourHumanMatch(store)
        val matchId = requireNotNull(room.matchId)

        playUntilRoundTerminal(
            store = store,
            room = room,
            matchId = matchId,
        )

        val accumulatorBeforeRestart = requireNotNull(
            store.getRankedMatchMetricAccumulator(matchId),
        )
        val json = Json {
            encodeDefaults = true
        }
        val encodedState = json.encodeToString(
            store.snapshotPersistentState(),
        )
        val decodedState =
            json.decodeFromString<OnlineServerStoreState>(
                encodedState,
            )
        val restartedStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        restartedStore.restorePersistentState(decodedState)

        assertEquals(
            accumulatorBeforeRestart,
            restartedStore.getRankedMatchMetricAccumulator(matchId),
        )
    }

    @Test
    fun ranked_result_is_idempotent_persistent_and_not_pruned() {
        var now = 1_700_000_000_000L
        val resourcePolicy = OnlineServerStoreResourcePolicy(
            waitingRoomRetentionMillis = 1L,
            finalizedRoomRetentionMillis = 1L,
            pruneIntervalMillis = 1L,
        )
        val store = InMemoryOnlineServerStore(
            resourcePolicy = resourcePolicy,
            nowEpochMillis = { now },
        )
        val room = startFourHumanMatch(
            store = store,
            matchMode = DominoMatchMode.PUBLIC_RANKED,
        )
        val matchId = requireNotNull(room.matchId)

        val finalSnapshot = playUntilMatchFinished(
            store = store,
            room = room,
            matchId = matchId,
        )
        val resultBeforeRestart = requireNotNull(
            store.getRankedMatchResult(matchId),
        )
        val accumulatorBeforeRestart = requireNotNull(
            store.getRankedMatchMetricAccumulator(matchId),
        )

        assertEquals(
            finalSnapshot.gameState.teamScores,
            resultBeforeRestart.finalTeamScores,
        )
        assertEquals(
            accumulatorBeforeRestart.collectiveCountPointsByTeam,
            resultBeforeRestart.collectiveCountPointsByTeam,
        )
        assertEquals(
            accumulatorBeforeRestart.completedRounds,
            resultBeforeRestart.completedRounds,
        )
        assertEquals(
            room.players
                .sortedBy { player -> player.seatIndex }
                .map { player -> player.playerId },
            resultBeforeRestart.players.map { player ->
                player.playerId
            },
        )
        val expectedAccountIds = room.players
            .sortedBy { player -> player.seatIndex }
            .map { player ->
                requireNotNull(
                    store.promoteAccount(
                        playerId = player.playerId,
                    ),
                ).accountId
            }

        assertEquals(
            expectedAccountIds,
            resultBeforeRestart.players.map { player ->
                player.accountId
            },
        )
        assertTrue(
            resultBeforeRestart.players.all { player ->
                !player.accountId.isNullOrBlank()
            },
        )

        val json = Json {
            encodeDefaults = true
        }
        val persistedState =
            json.decodeFromString<OnlineServerStoreState>(
                json.encodeToString(
                    store.snapshotPersistentState(),
                ),
            )
        val restartedStore = InMemoryOnlineServerStore(
            resourcePolicy = resourcePolicy,
            nowEpochMillis = { now },
        )

        restartedStore.restorePersistentState(persistedState)

        assertEquals(
            resultBeforeRestart,
            restartedStore.getRankedMatchResult(matchId),
        )
        assertEquals(
            accumulatorBeforeRestart,
            restartedStore.getRankedMatchMetricAccumulator(matchId),
        )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            InMemoryOnlineServerStore(
                resourcePolicy = resourcePolicy,
                nowEpochMillis = { now },
            ).restorePersistentState(
                persistedState.copy(
                    rankedResults = listOf(
                        resultBeforeRestart,
                        resultBeforeRestart,
                    ),
                ),
            )
        }

        now += 2L

        assertTrue(
            restartedStore.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "replacement-player",
                    playerName = "Replacement",
                ),
            ).accepted,
        )

        assertNull(
            restartedStore.getRoomSnapshot(room.roomId),
        )
        assertNull(
            restartedStore.getMatchSnapshot(matchId),
        )
        assertEquals(
            resultBeforeRestart,
            restartedStore.getRankedMatchResult(matchId),
        )
        assertEquals(
            1,
            restartedStore
                .snapshotPersistentState()
                .rankedResults
                .size,
        )
    }

    private fun playUntilMatchFinished(
        store: InMemoryOnlineServerStore,
        room: OnlineRoomSnapshotDto,
        matchId: String,
    ): OnlineMatchSnapshotDto {
        repeat(64) { roundIndex ->
            val terminalSnapshot = playUntilRoundTerminal(
                store = store,
                room = room,
                matchId = matchId,
            )
            val terminalState = terminalSnapshot.toRuntimeState(
                localPlayerIndex = 0,
            )

            if (terminalState.phase == DominoMatchPhase.MatchFinished) {
                return terminalSnapshot
            }

            assertEquals(
                DominoMatchPhase.RoundSummary,
                terminalState.phase,
            )

            val result = store.submitAction(
                createOnlineStartNextRoundAction(
                    roomId = room.roomId,
                    matchId = matchId,
                    playerId = room.hostPlayerId,
                    revision = terminalSnapshot.revision,
                    actionId =
                        "ranked-result-next-round-$roundIndex",
                ),
            )

            assertTrue(result.accepted)
        }

        error("A partida nao terminou dentro do limite de rodadas.")
    }

    private fun playUntilRoundTerminal(
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

            val currentSeatIndex =
                snapshot.gameState.currentPlayerIndex
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
                    actionId =
                        "ranked-result-move-$step-${snapshot.revision}",
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

        error("A rodada nao terminou dentro do limite de acoes.")
    }

    private fun startFourHumanMatch(
        store: InMemoryOnlineServerStore,
        matchMode: DominoMatchMode =
            DominoMatchMode.PRIVATE_UNRANKED,
    ): OnlineRoomSnapshotDto {
        val createRequest = CreateOnlineRoomRequestDto(
            localPlayerId = "player-1",
            playerName = if (
                matchMode == DominoMatchMode.PUBLIC_RANKED
            ) {
                rankedTableCode(1)
            } else {
                "Jogador 1"
            },
        )
        val room = requireNotNull(
            if (matchMode == DominoMatchMode.PRIVATE_UNRANKED) {
                store.createRoom(createRequest)
            } else {
                val account = requireNotNull(
                    store.promoteAccount(
                        playerId = createRequest.localPlayerId,
                    ),
                )
                requireNotNull(
                    store.updateAccountProfile(
                        accountId = account.accountId,
                        publicDisplayName = "Jogador Teste",
                        tableName = createRequest.playerName,
                    ),
                )
                store.createPublicRankedRoom(
                    request = createRequest,
                    identity = account.toRequestIdentity(),
                )
            }.roomSnapshot,
        )

        listOf(2, 3).forEach { playerNumber ->
            val request = JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "player-$playerNumber",
                playerName = if (
                    matchMode == DominoMatchMode.PUBLIC_RANKED
                ) {
                    rankedTableCode(playerNumber)
                } else {
                    "Jogador $playerNumber"
                },
            )
            val result =
                if (matchMode == DominoMatchMode.PRIVATE_UNRANKED) {
                    store.joinRoom(request)
                } else {
                    val account = requireNotNull(
                        store.promoteAccount(
                            playerId = request.localPlayerId,
                        ),
                    )
                    requireNotNull(
                        store.updateAccountProfile(
                            accountId = account.accountId,
                            publicDisplayName = "Jogador Teste",
                            tableName = request.playerName,
                        ),
                    )
                    store.joinPublicRankedRoom(
                        request = request,
                        identity = account.toRequestIdentity(),
                    )
                }
            assertTrue(result.accepted)
        }

        val finalRequest = JoinOnlineRoomRequestDto(
            roomCode = room.roomCode,
            localPlayerId = "player-4",
            playerName = if (
                matchMode == DominoMatchMode.PUBLIC_RANKED
            ) {
                rankedTableCode(4)
            } else {
                "Jogador 4"
            },
        )

        val joinedRoom = requireNotNull(
            if (matchMode == DominoMatchMode.PRIVATE_UNRANKED) {
                store.joinRoom(finalRequest)
            } else {
                val account = requireNotNull(
                    store.promoteAccount(
                        playerId = finalRequest.localPlayerId,
                    ),
                )
                requireNotNull(
                    store.updateAccountProfile(
                        accountId = account.accountId,
                        publicDisplayName = "Jogador Teste",
                        tableName = finalRequest.playerName,
                    ),
                )
                store.joinPublicRankedRoom(
                    request = finalRequest,
                    identity = account.toRequestIdentity(),
                )
            }.roomSnapshot,
        )

        if (matchMode == DominoMatchMode.PRIVATE_UNRANKED) {
            return requireNotNull(
                store.startPrivateRoom(
                    PrivateRoomStartRequestDto(
                        roomId = joinedRoom.roomId,
                        localPlayerId = "player-1",
                    ),
                ).roomSnapshot,
            )
        }

        return joinedRoom
    }

    private fun rankedTableCode(
        playerNumber: Int,
    ): String = "P0$playerNumber"

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "ranked-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )
}
