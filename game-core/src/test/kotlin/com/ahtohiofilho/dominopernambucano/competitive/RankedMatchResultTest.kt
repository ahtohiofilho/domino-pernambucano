package com.ahtohiofilho.dominopernambucano.competitive

import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankedMatchResultTest {
    @Test
    fun immutable_result_materializes_canonical_player_deltas() {
        val result = buildRankedMatchResult(
            matchId = "match-1",
            completedAtEpochMillis = 5_000L,
            finalState = finalState(),
            playerIdentitiesBySeat = listOf(
                RankedMatchPlayerIdentity(
                    playerId = "player-1",
                    accountId = "account-1",
                ),
                RankedMatchPlayerIdentity(
                    playerId = "player-2",
                ),
                RankedMatchPlayerIdentity(
                    playerId = "player-3",
                    accountId = "account-3",
                ),
                RankedMatchPlayerIdentity(
                    playerId = "player-4",
                ),
            ),
            accumulator = accumulator(),
        )

        assertEquals(
            "ranked-match-result:match-1",
            result.resultId,
        )
        assertEquals(
            CURRENT_RANKING_RULE_VERSION,
            result.rankingRuleVersion,
        )
        assertEquals(listOf(6, 3), result.finalTeamScores)
        assertEquals(
            listOf(1, 1),
            result.collectiveCountPointsByTeam,
        )
        assertEquals(4, result.completedRounds)

        assertEquals(3, result.players[0].teamBalanceDelta)
        assertEquals(-3, result.players[1].teamBalanceDelta)
        assertEquals(3, result.players[2].teamBalanceDelta)
        assertEquals(-3, result.players[3].teamBalanceDelta)
        assertEquals(4, result.players[0].individualPointsScored)
        assertEquals(3, result.players[0].touchesGiven)
        assertEquals(1, result.players[0].automaticRounds)
        assertEquals("account-1", result.players[0].accountId)
        assertNull(result.players[1].accountId)
    }

    @Test
    fun result_and_accumulator_round_trip_through_json() {
        val result = buildRankedMatchResult(
            matchId = "match-json",
            completedAtEpochMillis = 9_000L,
            finalState = finalState(),
            playerIdentitiesBySeat = List(4) { seatIndex ->
                RankedMatchPlayerIdentity(
                    playerId = "player-${seatIndex + 1}",
                )
            },
            accumulator = accumulator(),
        )
        val json = Json {
            encodeDefaults = true
        }

        val encodedResult = json.encodeToString(result)
        val decodedResult =
            json.decodeFromString<RankedMatchResult>(encodedResult)

        val encodedAccumulator =
            json.encodeToString(accumulator())
        val decodedAccumulator =
            json.decodeFromString<RankedMatchMetricAccumulator>(
                encodedAccumulator,
            )

        assertEquals(result, decodedResult)
        assertEquals(accumulator(), decodedAccumulator)
    }

    private fun accumulator(): RankedMatchMetricAccumulator {
        return RankedMatchMetricAccumulator(
            seatMetrics = listOf(
                RankedSeatMatchMetrics(
                    individualPoints = 4,
                    touchesGiven = 3,
                    automaticRounds = 1,
                ),
                RankedSeatMatchMetrics(
                    individualPoints = 2,
                    touchesGiven = 1,
                ),
                RankedSeatMatchMetrics(
                    individualPoints = 1,
                ),
                RankedSeatMatchMetrics(),
            ),
            collectiveCountPointsByTeam = listOf(1, 1),
            completedRounds = 4,
        )
    }

    private fun finalState(): DominoGameState {
        return DominoGameState(
            board = emptyList(),
            boardChain = DominoBoardChain(),
            players = List(4) { seatIndex ->
                DominoPlayer(
                    id = seatIndex,
                    name = "Player ${seatIndex + 1}",
                    hand = emptyList(),
                )
            },
            sleepingPieces = emptyList(),
            currentPlayerIndex = 0,
            lastRoundWinnerIndex = 0,
            openingPiece = null,
            teamScores = listOf(6, 3),
            lastMove = null,
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.COMMON,
            gameWinnerTeamIndex = 0,
        )
    }
}
