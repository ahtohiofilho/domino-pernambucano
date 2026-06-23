package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMatchSnapshotProjectionTest {
    @Test
    fun round_in_progress_exposes_only_requested_player_hand() {
        val snapshot = createSnapshot(
            phase = DominoMatchPhase.WaitingForLocalMove,
        )

        val projected = snapshot.projectForParticipant(
            seatIndex = 1,
        )

        assertEquals(
            snapshot.gameState.players[1].hand,
            projected.gameState.players[1].hand,
        )

        snapshot.gameState.players.forEachIndexed { playerIndex, sourcePlayer ->
            if (playerIndex == 1) {
                return@forEachIndexed
            }

            val projectedPlayer = projected.gameState.players[playerIndex]

            assertEquals(
                sourcePlayer.hand.size,
                projectedPlayer.hand.size,
            )
            assertTrue(
                projectedPlayer.hand.all { piece ->
                    piece.left == -1 && piece.right == -1
                },
            )
        }

        assertEquals(
            snapshot.gameState.sleepingPieces.size,
            projected.gameState.sleepingPieces.size,
        )
        assertTrue(
            projected.gameState.sleepingPieces.all { piece ->
                piece.left == -1 && piece.right == -1
            },
        )
        assertFalse(
            projected == snapshot,
        )
    }

    @Test
    fun round_summary_reveals_every_hand_and_sleeping_pieces() {
        val snapshot = createSnapshot(
            phase = DominoMatchPhase.RoundSummary,
        )

        val projected = snapshot.projectForParticipant(
            seatIndex = 2,
        )

        assertEquals(
            snapshot,
            projected,
        )
    }

    @Test
    fun match_finished_reveals_every_hand_and_sleeping_pieces() {
        val snapshot = createSnapshot(
            phase = DominoMatchPhase.MatchFinished,
        )

        val projected = snapshot.projectForParticipant(
            seatIndex = 3,
        )

        assertEquals(
            snapshot,
            projected,
        )
    }

    private fun createSnapshot(
        phase: DominoMatchPhase,
    ): OnlineMatchSnapshotDto {
        return DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = phase,
            clockPolicy = DominoMatchClockPolicy.Disabled,
            playerClockMillis = emptyList(),
        ).toOnlineSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = 1L,
        )
    }
}
