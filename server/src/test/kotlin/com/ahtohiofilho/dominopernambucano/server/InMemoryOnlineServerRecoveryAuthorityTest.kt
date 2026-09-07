package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.createOnlinePassTurnAction
import com.ahtohiofilho.dominopernambucano.online.createOnlinePlayMoveAction
import com.ahtohiofilho.dominopernambucano.online.createOnlineSnapshotRequestAction
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOnlineServerRecoveryAuthorityTest {
    @Test
    fun authoritative_server_advances_round_summary_without_client_transition() {
        var now = 1_000L
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val room = startFourHumanMatch(store)
        val matchId = requireNotNull(room.matchId)

        val summary = playUntilRoundSummary(
            store = store,
            room = room,
            matchId = matchId,
        )

        assertEquals(
            DominoMatchPhase.RoundSummary,
            summary.toRuntimeState(
                localPlayerIndex = 0,
            ).phase,
        )

        val summaryStartedAt = requireNotNull(
            summary.serverEpochMillis,
        )

        now =
            summaryStartedAt +
                DominoMatchTiming.RoundSummaryAutoAdvanceMillis -
                1L

        store.advanceAuthoritativeTime()

        assertEquals(
            summary.revision,
            requireNotNull(
                store.getMatchSnapshot(matchId),
            ).revision,
        )

        now += 1L

        assertTrue(
            store.advanceAuthoritativeTime(),
        )

        val advanced = requireNotNull(
            store.getMatchSnapshot(matchId),
        )

        assertTrue(
            advanced.revision > summary.revision,
        )
        assertTrue(
            advanced.toRuntimeState(
                localPlayerIndex = 0,
            ).phase != DominoMatchPhase.RoundSummary,
        )
    }

    @Test
    fun authenticated_presence_refresh_reclaims_timed_out_human_control() {
        var now = 10_000L
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val room = startFourHumanMatch(store)
        val matchId = requireNotNull(room.matchId)
        val initial = requireNotNull(
            store.getMatchSnapshot(matchId),
        )
        val timedOutSeat =
            initial.gameState.currentPlayerIndex
        val timedOutPlayerId = requireNotNull(
            room.players.singleOrNull { player ->
                player.seatIndex == timedOutSeat
            },
        ).playerId

        releaseRoundIntroForTest(
            store = store,
            roomId = room.roomId,
            matchId = matchId,
        )

        now += 60_000L

        assertTrue(
            store.advanceAuthoritativeTime(),
        )

        val afterTimeout = requireNotNull(
            store.getMatchSnapshot(matchId),
        )

        assertTrue(
            timedOutSeat in
                afterTimeout.automaticPlayerIndexes,
        )

        val refresh = store.submitAction(
            createOnlineSnapshotRequestAction(
                roomId = room.roomId,
                matchId = matchId,
                playerId = timedOutPlayerId,
                revision = afterTimeout.revision,
                actionId = "recovery-presence-refresh",
            ),
        )

        assertTrue(refresh.accepted)

        val reclaimed = requireNotNull(
            store.getMatchSnapshot(matchId),
        )

        assertTrue(
            timedOutSeat !in
                reclaimed.automaticPlayerIndexes,
        )
        assertTrue(
            reclaimed.revision >
                afterTimeout.revision,
        )
    }

    private fun startFourHumanMatch(
        store: InMemoryOnlineServerStore,
    ): OnlineRoomSnapshotDto {
        val created = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        var latestRoom = created

        (2..4).forEach { index ->
            latestRoom = requireNotNull(
                store.joinRoom(
                    JoinOnlineRoomRequestDto(
                        roomCode = created.roomCode,
                        localPlayerId = "player-$index",
                        playerName = "Jogador $index",
                    ),
                ).roomSnapshot,
            )
        }

        val started = store.startPrivateRoom(
            PrivateRoomStartRequestDto(
                roomId = latestRoom.roomId,
                localPlayerId = "player-1",
            ),
        )

        assertTrue(started.accepted)

        return requireNotNull(
            started.roomSnapshot,
        )
    }

    private fun playUntilRoundSummary(
        store: InMemoryOnlineServerStore,
        room: OnlineRoomSnapshotDto,
        matchId: String,
    ): OnlineMatchSnapshotDto {
        repeat(256) { actionIndex ->
            val snapshot = requireNotNull(
                store.getMatchSnapshot(matchId),
            )
            val runtimeState = snapshot.toRuntimeState(
                localPlayerIndex = 0,
            )

            if (isRoundFinished(runtimeState.gameState)) {
                return snapshot
            }

            /*
             * A snapshot may legitimately announce PresentingPass.
             * submitAction() performs an authoritative pre-validation tick;
             * sending a PASS from that older revision would then be rejected
             * as stale. Let the server consume any pending mandatory
             * transition first and restart from its newest snapshot.
             */
            if (store.advanceAuthoritativeTime()) {
                return@repeat
            }

            val seatIndex =
                runtimeState.gameState.currentPlayerIndex
            val playerId = requireNotNull(
                room.players.singleOrNull { player ->
                    player.seatIndex == seatIndex
                },
            ).playerId
            val move = findBasicBotMove(
                state = runtimeState.gameState,
            )

            val result = if (move != null) {
                store.submitAction(
                    createOnlinePlayMoveAction(
                        roomId = room.roomId,
                        matchId = matchId,
                        playerId = playerId,
                        revision = snapshot.revision,
                        move = move,
                        actionId =
                            "recovery-round-move-$actionIndex",
                    ),
                )
            } else {
                store.submitAction(
                    createOnlinePassTurnAction(
                        roomId = room.roomId,
                        matchId = matchId,
                        playerId = playerId,
                        revision = snapshot.revision,
                        actionId =
                            "recovery-round-pass-$actionIndex",
                    ),
                )
            }

            assertTrue(
                result.reason.orEmpty(),
                result.accepted,
            )
        }

        error(
            "A rodada não terminou dentro do limite do teste.",
        )
    }
}