package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineClockReserveRoundTripRegressionTest {
    @Test
    fun spent_main_clock_is_refilled_and_survives_snapshot_round_trip_for_next_turn_projection() {
        val initialGameState = createInitialDominoGameState()
        val seatIndex = initialGameState.currentPlayerIndex

        val playableMove = initialGameState.players[seatIndex]
            .hand
            .asSequence()
            .flatMap { piece ->
                getPlayableMoves(
                    board = initialGameState.board,
                    piece = piece,
                    openingPiece = initialGameState.openingPiece,
                ).asSequence()
            }
            .first()

        val playerCount = initialGameState.players.size
        val runtimeState = DominoMatchRuntimeState(
            gameState = initialGameState,
            roundNumber = 1,
            localPlayerIndex = seatIndex,
            phase = DominoMatchPhase.WaitingForLocalMove,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
            playerClockMillis = List(playerCount) { index ->
                if (index == seatIndex) 8_000L else 20_000L
            },
            playerClockReserveMillis = List(playerCount) {
                20_000L
            },
        )

        val sourceSnapshot = runtimeState.toOnlineSnapshotDto(
            roomId = "clock-regression-room",
            matchId = "clock-regression-match",
            revision = 7L,
        )

        val reduction = reduceOnlineGameAction(
            action = createOnlinePlayMoveAction(
                roomId = sourceSnapshot.roomId,
                matchId = sourceSnapshot.matchId,
                playerId = "clock-regression-player",
                revision = sourceSnapshot.revision,
                move = playableMove,
                actionId = "clock-regression-action",
            ),
            currentSnapshot = sourceSnapshot,
            seatIndex = seatIndex,
        )

        assertTrue(
            reduction is OnlineMatchActionReduction.Accepted,
        )

        val acceptedRuntimeState =
            (reduction as OnlineMatchActionReduction.Accepted)
                .runtimeState

        /*
         * The player spent 12 seconds of the 20-second main clock.
         * A successful PLAY_MOVE must transfer exactly 12 seconds from reserve:
         * main 8 -> 20, reserve 20 -> 8.
         */
        assertEquals(
            20_000L,
            acceptedRuntimeState.playerClockMillis[seatIndex],
        )
        assertEquals(
            8_000L,
            acceptedRuntimeState.playerClockReserveMillis[seatIndex],
        )

        /*
         * Reproduce the server -> DTO -> client boundary.
         */
        val transportedRuntimeState = acceptedRuntimeState
            .toOnlineSnapshotDto(
                roomId = sourceSnapshot.roomId,
                matchId = sourceSnapshot.matchId,
                revision = 8L,
            )
            .toRuntimeState(
                localPlayerIndex = seatIndex,
            )

        assertEquals(
            20_000L,
            transportedRuntimeState.playerClockMillis[seatIndex],
        )
        assertEquals(
            8_000L,
            transportedRuntimeState.playerClockReserveMillis[seatIndex],
        )

        /*
         * Simulate the later authoritative snapshot where this same seat gets
         * the turn again. The client projection must start from the refilled
         * 20-second authoritative baseline, not from the old spent 8 seconds.
         */
        val returnedTurnRuntimeState = transportedRuntimeState.copy(
            gameState = transportedRuntimeState.gameState.copy(
                currentPlayerIndex = seatIndex,
            ),
            phase = DominoMatchPhase.WaitingForLocalMove,
        )

        val projectedRuntimeState = projectOnlineAuthoritativeClock(
            runtimeState = returnedTurnRuntimeState,
            elapsedSinceSnapshotMillis = 2_000L,
        )

        assertEquals(
            18_000L,
            projectedRuntimeState.playerClockMillis[seatIndex],
        )
        assertEquals(
            8_000L,
            projectedRuntimeState.playerClockReserveMillis[seatIndex],
        )
    }
}