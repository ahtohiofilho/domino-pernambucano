package com.ahtohiofilho.dominopernambucano.competitive

import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.PlayedMove
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedMatchMetricAccumulatorTest {
    @Test
    fun transition_accumulates_touch_points_and_automatic_rounds_once() {
        val initial = gameState(
            board = listOf(DominoPiece(6, 6)),
            currentPlayerIndex = 0,
            playerHands = listOf(
                listOf(DominoPiece(6, 5), DominoPiece(0, 0)),
                listOf(DominoPiece(1, 2)),
                listOf(DominoPiece(3, 3)),
                listOf(DominoPiece(4, 4)),
            ),
            teamScores = listOf(2, 3),
        )
        val afterTouchMove = initial.copy(
            board = listOf(
                DominoPiece(6, 6),
                DominoPiece(6, 5),
            ),
            players = initial.players.mapIndexed { seatIndex, player ->
                if (seatIndex == 0) {
                    player.copy(
                        hand = listOf(DominoPiece(0, 0)),
                    )
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

        assertTrue(
            didRankedSeatPlayPiece(
                previousState = initial,
                updatedState = afterTouchMove,
                seatIndex = 0,
            ),
        )

        val afterTouch = accumulateRankedMatchTransition(
            accumulator = RankedMatchMetricAccumulator.empty(
                playerCount = 4,
                teamCount = 2,
            ),
            previousState = initial,
            updatedState = afterTouchMove,
            automaticSeatIndexes = emptySet(),
        )

        assertEquals(1, afterTouch.seatMetrics[0].touchesGiven)
        assertEquals(0, afterTouch.completedRounds)

        val finishedRound = afterTouchMove.copy(
            teamScores = listOf(6, 3),
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.CRUZADA,
            gameWinnerTeamIndex = 0,
        )

        val afterRound = accumulateRankedMatchTransition(
            accumulator = afterTouch,
            previousState = afterTouchMove,
            updatedState = finishedRound,
            automaticSeatIndexes = setOf(1, 3),
        )

        assertEquals(1, afterRound.completedRounds)
        assertEquals(4, afterRound.seatMetrics[0].individualPoints)
        assertEquals(1, afterRound.seatMetrics[0].touchesGiven)
        assertEquals(1, afterRound.seatMetrics[1].automaticRounds)
        assertEquals(1, afterRound.seatMetrics[3].automaticRounds)
        assertEquals(listOf(0, 0), afterRound.collectiveCountPointsByTeam)

        val repeatedFinishedState = accumulateRankedMatchTransition(
            accumulator = afterRound,
            previousState = finishedRound,
            updatedState = finishedRound,
            automaticSeatIndexes = setOf(1, 3),
        )

        assertSame(afterRound, repeatedFinishedState)
    }

    @Test
    fun mandatory_pass_is_not_an_automatic_piece_play() {
        val previous = gameState(
            board = listOf(DominoPiece(6, 6)),
            currentPlayerIndex = 0,
            playerHands = listOf(
                listOf(DominoPiece(1, 2)),
                listOf(DominoPiece(3, 4)),
                listOf(DominoPiece(5, 5)),
                listOf(DominoPiece(0, 0)),
            ),
        )
        val afterMandatoryPass = previous.copy(
            currentPlayerIndex = 1,
            consecutivePassTurns = 1,
        )

        assertFalse(
            didRankedSeatPlayPiece(
                previousState = previous,
                updatedState = afterMandatoryPass,
                seatIndex = 0,
            ),
        )
    }

    @Test
    fun closed_round_accumulates_collective_count_without_individual_points() {
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

        val accumulator = accumulateRankedMatchTransition(
            accumulator = RankedMatchMetricAccumulator.empty(
                playerCount = 4,
                teamCount = 2,
            ),
            previousState = previous,
            updatedState = updated,
            automaticSeatIndexes = emptySet(),
        )

        assertEquals(1, accumulator.completedRounds)
        assertEquals(
            listOf(0, 0, 0, 0),
            accumulator.seatMetrics.map { metrics ->
                metrics.individualPoints
            },
        )
        assertEquals(listOf(1, 0), accumulator.collectiveCountPointsByTeam)
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
            players = playerHands.mapIndexed { seatIndex, hand ->
                DominoPlayer(
                    id = seatIndex,
                    name = "Player ${seatIndex + 1}",
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
