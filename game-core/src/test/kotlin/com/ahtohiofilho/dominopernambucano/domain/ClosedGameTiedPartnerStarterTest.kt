package com.ahtohiofilho.dominopernambucano.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClosedGameTiedPartnerStarterTest {
    @Test
    fun tied_partners_can_select_first_partner_as_next_starter() {
        val result = finishRoundByClosedGame(
            state = tiedPartnerState(),
            tiedPartnerStarterSelector = { candidates ->
                candidates.first()
            },
        )

        assertEquals(RoundWinKind.CLOSED, result.roundWinKind)
        assertEquals(0, result.roundWinnerTeamIndex)
        assertNull(result.roundWinnerPlayerIndex)
        assertEquals(0, result.lastRoundWinnerIndex)
        assertEquals(0, result.currentPlayerIndex)
        assertEquals(listOf(1, 0), result.teamScores)
    }

    @Test
    fun tied_partners_can_select_second_partner_as_next_starter() {
        val result = finishRoundByClosedGame(
            state = tiedPartnerState(),
            tiedPartnerStarterSelector = { candidates ->
                candidates.last()
            },
        )

        assertEquals(RoundWinKind.CLOSED, result.roundWinKind)
        assertEquals(0, result.roundWinnerTeamIndex)
        assertNull(result.roundWinnerPlayerIndex)
        assertEquals(2, result.lastRoundWinnerIndex)
        assertEquals(2, result.currentPlayerIndex)
        assertEquals(listOf(1, 0), result.teamScores)
    }

    @Test
    fun unique_count_winner_remains_the_next_starter_without_random_selection() {
        val result = finishRoundByClosedGame(
            state = uniqueWinnerState(),
            tiedPartnerStarterSelector = {
                error("Não deve haver sorteio com vencedor individual único.")
            },
        )

        assertEquals(2, result.roundWinnerPlayerIndex)
        assertEquals(2, result.lastRoundWinnerIndex)
        assertEquals(2, result.currentPlayerIndex)
        assertEquals(0, result.roundWinnerTeamIndex)
    }

    @Test
    fun opposing_team_count_tie_remains_closed_tie_without_random_selection() {
        val result = finishRoundByClosedGame(
            state = opposingTeamTieState(),
            tiedPartnerStarterSelector = {
                error("Não deve haver sorteio quando o empate envolve duplas adversárias.")
            },
        )

        assertEquals(RoundWinKind.CLOSED_TIE, result.roundWinKind)
        assertNull(result.roundWinnerPlayerIndex)
        assertNull(result.roundWinnerTeamIndex)
        assertEquals(listOf(0, 0), result.teamScores)
    }

    @Test
    fun closed_round_respects_existing_score_multiplier() {
        val result = finishRoundByClosedGame(
            state = tiedPartnerState().copy(
                scoreMultiplier = 3,
            ),
            tiedPartnerStarterSelector = { candidates ->
                candidates.first()
            },
        )

        assertEquals(listOf(3, 0), result.teamScores)
    }

    private fun tiedPartnerState(): DominoGameState {
        return baseState(
            hands = listOf(
                listOf(DominoPiece(0, 1)),
                listOf(DominoPiece(5, 5)),
                listOf(DominoPiece(1, 0)),
                listOf(DominoPiece(6, 6)),
            ),
        )
    }

    private fun uniqueWinnerState(): DominoGameState {
        return baseState(
            hands = listOf(
                listOf(DominoPiece(2, 2)),
                listOf(DominoPiece(5, 5)),
                listOf(DominoPiece(0, 1)),
                listOf(DominoPiece(6, 6)),
            ),
        )
    }

    private fun opposingTeamTieState(): DominoGameState {
        return baseState(
            hands = listOf(
                listOf(DominoPiece(0, 1)),
                listOf(DominoPiece(1, 0)),
                listOf(DominoPiece(5, 5)),
                listOf(DominoPiece(6, 6)),
            ),
        )
    }

    private fun baseState(
        hands: List<List<DominoPiece>>,
    ): DominoGameState {
        return DominoGameState(
            board = listOf(DominoPiece(3, 3)),
            boardChain = DominoBoardChain(),
            players = hands.mapIndexed { index, hand ->
                DominoPlayer(
                    id = index,
                    name = "Player ${index + 1}",
                    hand = hand,
                )
            },
            sleepingPieces = emptyList(),
            currentPlayerIndex = 0,
            lastRoundWinnerIndex = null,
            openingPiece = null,
            teamScores = listOf(0, 0),
            lastMove = null,
            roundWinnerPlayerIndex = null,
            roundWinnerTeamIndex = null,
            roundWinKind = null,
            gameWinnerTeamIndex = null,
        )
    }
}
