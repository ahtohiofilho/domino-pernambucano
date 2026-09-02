package com.ahtohiofilho.dominopernambucano.competitive

import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.PlayedMove
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankedMatchMetricRulesTest {
    @Test
    fun hit_points_are_attributed_to_the_player_who_scored_them() {
        val previous = gameState(
            teamScores = listOf(2, 3),
        )
        val updated = previous.copy(
            teamScores = listOf(6, 3),
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.CRUZADA,
            gameWinnerTeamIndex = 0,
        )

        val delta = requireNotNull(
            calculateRankedRoundMetricDelta(
                previousState = previous,
                updatedState = updated,
                automaticSeatIndexes = setOf(1, 3),
            ),
        )

        assertEquals(listOf(4, 0, 0, 0), delta.individualPointDeltasBySeat)
        assertEquals(listOf(0, 0), delta.collectiveCountPointsByTeam)
        assertEquals(setOf(1, 3), delta.automaticRoundSeatIndexes)
    }

    @Test
    fun closed_game_point_is_collective_and_not_attributed_to_a_player() {
        val previous = gameState(
            teamScores = listOf(5, 3),
        )
        val updated = previous.copy(
            teamScores = listOf(6, 3),
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.CLOSED,
            gameWinnerTeamIndex = 0,
        )

        val delta = requireNotNull(
            calculateRankedRoundMetricDelta(
                previousState = previous,
                updatedState = updated,
                automaticSeatIndexes = emptySet(),
            ),
        )

        assertEquals(listOf(0, 0, 0, 0), delta.individualPointDeltasBySeat)
        assertEquals(listOf(1, 0), delta.collectiveCountPointsByTeam)
    }

    @Test
    fun touch_is_given_to_the_move_author_when_the_next_player_cannot_play() {
        val previous = gameState(
            board = listOf(DominoPiece(6, 6)),
            currentPlayerIndex = 0,
            playerHands = listOf(
                listOf(DominoPiece(6, 5)),
                listOf(DominoPiece(1, 2)),
                listOf(DominoPiece(0, 0)),
                listOf(DominoPiece(3, 4)),
            ),
        )
        val updated = previous.copy(
            board = listOf(
                DominoPiece(6, 6),
                DominoPiece(6, 5),
            ),
            players = previous.players.mapIndexed { index, player ->
                if (index == 0) {
                    player.copy(hand = emptyList())
                } else {
                    player
                }
            },
            currentPlayerIndex = 1,
            lastMove = PlayedMove(
                playerIndex = 0,
                piece = DominoPiece(6, 5),
                wasLaELo = false,
                wasCruzada = false,
            ),
            consecutivePassTurns = 0,
        )

        assertEquals(
            0,
            findRankedTouchGiverSeatIndex(
                previousState = previous,
                updatedState = updated,
            ),
        )
    }

    @Test
    fun no_touch_is_recorded_when_the_next_player_has_a_legal_piece() {
        val previous = gameState(
            board = listOf(DominoPiece(6, 6)),
            currentPlayerIndex = 0,
            playerHands = listOf(
                listOf(DominoPiece(6, 5)),
                listOf(DominoPiece(5, 2)),
                listOf(DominoPiece(0, 0)),
                listOf(DominoPiece(3, 4)),
            ),
        )
        val updated = previous.copy(
            board = listOf(
                DominoPiece(6, 6),
                DominoPiece(6, 5),
            ),
            currentPlayerIndex = 1,
            lastMove = PlayedMove(
                playerIndex = 0,
                piece = DominoPiece(6, 5),
                wasLaELo = false,
                wasCruzada = false,
            ),
        )

        assertNull(
            findRankedTouchGiverSeatIndex(
                previousState = previous,
                updatedState = updated,
            ),
        )
    }

    @Test
    fun final_balance_is_points_for_minus_points_against() {
        val finalState = gameState(
            teamScores = listOf(6, 4),
        ).copy(
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.COMMON,
            gameWinnerTeamIndex = 0,
        )

        val result = buildRankedSeatResultDeltas(
            finalState = finalState,
            seatMetrics = listOf(
                RankedSeatMatchMetrics(individualPoints = 4),
                RankedSeatMatchMetrics(individualPoints = 2),
                RankedSeatMatchMetrics(individualPoints = 1),
                RankedSeatMatchMetrics(individualPoints = 0),
            ),
        )

        assertEquals(2, result[0].teamBalanceDelta)
        assertEquals(-2, result[1].teamBalanceDelta)
        assertEquals(2, result[2].teamBalanceDelta)
        assertEquals(-2, result[3].teamBalanceDelta)

        assertEquals(4, result[0].individualPointsDelta)
        assertEquals(2, result[1].individualPointsDelta)
        assertEquals(1, result[2].individualPointsDelta)
        assertEquals(0, result[3].individualPointsDelta)
    }

    private fun gameState(
        teamScores: List<Int> = listOf(0, 0),
        board: List<DominoPiece> = emptyList(),
        currentPlayerIndex: Int = 0,
        playerHands: List<List<DominoPiece>> = List(4) { emptyList() },
    ): DominoGameState {
        return DominoGameState(
            board = board,
            boardChain = DominoBoardChain(),
            players = playerHands.mapIndexed { index, hand ->
                DominoPlayer(
                    id = index,
                    name = "Player ${index + 1}",
                    hand = hand,
                )
            },
            sleepingPieces = emptyList(),
            currentPlayerIndex = currentPlayerIndex,
            lastRoundWinnerIndex = null,
            openingPiece = board.firstOrNull(),
            teamScores = teamScores,
            lastMove = null,
            roundWinnerPlayerIndex = null,
            roundWinnerTeamIndex = null,
            roundWinKind = null,
            gameWinnerTeamIndex = null,
        )
    }
}
