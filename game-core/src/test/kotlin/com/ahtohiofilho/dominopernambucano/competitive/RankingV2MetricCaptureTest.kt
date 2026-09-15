package com.ahtohiofilho.dominopernambucano.competitive

import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.PlayedMove
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankingV2MetricCaptureTest {
    @Test
    fun opposite_end_moves_do_not_erase_partner_end_ownership() {
        val previous = state(
            board = listOf(
                DominoPiece(6, 6),
                DominoPiece(6, 1),
            ),
            hands = hands(
                seat3 = listOf(DominoPiece(1, 2)),
            ),
            lastMove = move(
                seat = 1,
                piece = DominoPiece(6, 1),
            ),
        )
        val updated = previous.copy(
            board = previous.board + DominoPiece(1, 2),
            players = previous.players.mapIndexed { seat, player ->
                if (seat == 3) {
                    player.copy(hand = emptyList())
                } else {
                    player
                }
            },
            lastMove = move(
                seat = 3,
                piece = DominoPiece(1, 2),
            ),
        )
        val accumulator = RankedMatchMetricAccumulator(
            seatMetrics = List(4) { RankedSeatMatchMetrics() },
            collectiveCountPointsByTeam = listOf(0, 0),
            leftEndOwnerSeatIndex = 2,
            rightEndOwnerSeatIndex = 1,
        )

        val result = accumulateRankedMatchTransition(
            accumulator = accumulator,
            previousState = previous,
            updatedState = updated,
            automaticSeatIndexes = emptySet(),
        )

        assertEquals(2, result.leftEndOwnerSeatIndex)
        assertEquals(3, result.rightEndOwnerSeatIndex)
    }

    @Test
    fun common_hit_credits_partner_from_consumed_end() {
        val result = scoringHit(
            winKind = RoundWinKind.COMMON,
            roundPoints = 1,
            addOnLeft = true,
            leftOwner = 2,
            rightOwner = 3,
            winningPiece = DominoPiece(5, 6),
        )

        assertEquals(1, result.seatMetrics[2].assists)
        assertNull(result.leftEndOwnerSeatIndex)
        assertNull(result.rightEndOwnerSeatIndex)
    }

    @Test
    fun double_hit_credits_partner_from_consumed_end() {
        val result = scoringHit(
            winKind = RoundWinKind.DOUBLE,
            roundPoints = 2,
            addOnLeft = false,
            leftOwner = 1,
            rightOwner = 2,
            winningPiece = DominoPiece(1, 1),
        )

        assertEquals(2, result.seatMetrics[2].assists)
    }

    @Test
    fun la_e_lo_credits_full_assist_if_one_relevant_end_is_partner_owned() {
        val result = scoringHit(
            winKind = RoundWinKind.LA_E_LO,
            roundPoints = 3,
            addOnLeft = false,
            leftOwner = 2,
            rightOwner = 1,
            winningPiece = DominoPiece(1, 6),
        )

        assertEquals(3, result.seatMetrics[2].assists)
    }

    @Test
    fun cruzada_with_two_partner_owned_ends_credits_once_not_twice() {
        val result = scoringHit(
            winKind = RoundWinKind.CRUZADA,
            roundPoints = 4,
            addOnLeft = false,
            leftOwner = 2,
            rightOwner = 2,
            previousBoard = listOf(
                DominoPiece(1, 6),
                DominoPiece(6, 1),
            ),
            winningPiece = DominoPiece(1, 1),
        )

        assertEquals(4, result.seatMetrics[2].assists)
    }

    @Test
    fun opponent_owned_ends_do_not_generate_assists() {
        val result = scoringHit(
            winKind = RoundWinKind.CRUZADA,
            roundPoints = 4,
            addOnLeft = false,
            leftOwner = 1,
            rightOwner = 3,
            previousBoard = listOf(
                DominoPiece(1, 6),
                DominoPiece(6, 1),
            ),
            winningPiece = DominoPiece(1, 1),
        )

        assertEquals(
            listOf(0, 0, 0, 0),
            result.seatMetrics.map { metrics -> metrics.assists },
        )
    }

    @Test
    fun closed_round_never_generates_assists() {
        val previous = state(
            board = listOf(DominoPiece(6, 6)),
            hands = hands(),
            teamScores = listOf(5, 3),
        )
        val updated = previous.copy(
            teamScores = listOf(6, 3),
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.CLOSED,
            gameWinnerTeamIndex = 0,
        )
        val accumulator = RankedMatchMetricAccumulator(
            seatMetrics = List(4) { RankedSeatMatchMetrics() },
            collectiveCountPointsByTeam = listOf(0, 0),
            leftEndOwnerSeatIndex = 2,
            rightEndOwnerSeatIndex = 2,
        )

        val result = accumulateRankedMatchTransition(
            accumulator = accumulator,
            previousState = previous,
            updatedState = updated,
            automaticSeatIndexes = emptySet(),
        )

        assertEquals(
            listOf(0, 0, 0, 0),
            result.seatMetrics.map { metrics -> metrics.assists },
        )
    }

    @Test
    fun automatic_plays_count_each_piece_but_round_penalty_counts_once() {
        val start = state(
            board = listOf(DominoPiece(6, 6)),
            hands = hands(
                seat0 = listOf(
                    DominoPiece(6, 5),
                    DominoPiece(5, 4),
                    DominoPiece(0, 0),
                ),
                seat1 = listOf(DominoPiece(4, 4)),
            ),
        )
        val first = start.copy(
            board = start.board + DominoPiece(6, 5),
            players = start.players.mapIndexed { seat, player ->
                if (seat == 0) {
                    player.copy(
                        hand = listOf(
                            DominoPiece(5, 4),
                            DominoPiece(0, 0),
                        ),
                    )
                } else {
                    player
                }
            },
            currentPlayerIndex = 1,
            lastMove = move(
                seat = 0,
                piece = DominoPiece(6, 5),
            ),
        )

        val afterFirst = accumulateRankedMatchTransition(
            accumulator = RankedMatchMetricAccumulator.empty(
                playerCount = 4,
                teamCount = 2,
            ),
            previousState = start,
            updatedState = first,
            automaticSeatIndexes = setOf(0),
        )

        val mandatoryPass = first.copy(
            currentPlayerIndex = 2,
            consecutivePassTurns = 1,
        )
        val afterPass = accumulateRankedMatchTransition(
            accumulator = afterFirst,
            previousState = first,
            updatedState = mandatoryPass,
            automaticSeatIndexes = setOf(0),
        )

        assertEquals(
            1,
            afterPass.seatMetrics[0].automaticPlays,
        )

        val second = mandatoryPass.copy(
            board = mandatoryPass.board + DominoPiece(5, 4),
            players = mandatoryPass.players.mapIndexed { seat, player ->
                if (seat == 0) {
                    player.copy(
                        hand = listOf(DominoPiece(0, 0)),
                    )
                } else {
                    player
                }
            },
            lastMove = move(
                seat = 0,
                piece = DominoPiece(5, 4),
            ),
            consecutivePassTurns = 0,
        )
        val afterSecond = accumulateRankedMatchTransition(
            accumulator = afterPass,
            previousState = mandatoryPass,
            updatedState = second,
            automaticSeatIndexes = setOf(0),
        )

        assertEquals(
            2,
            afterSecond.seatMetrics[0].automaticPlays,
        )

        val finished = second.copy(
            teamScores = listOf(1, 0),
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.CLOSED,
        )
        val afterRound = accumulateRankedMatchTransition(
            accumulator = afterSecond,
            previousState = second,
            updatedState = finished,
            automaticSeatIndexes = setOf(0),
        )

        assertEquals(
            2,
            afterRound.seatMetrics[0].automaticPlays,
        )
        assertEquals(
            1,
            afterRound.seatMetrics[0].automaticRounds,
        )
    }

    @Test
    fun result_and_v2_ladder_carry_assists_and_automatic_plays() {
        val completedAt = 1_800_000_000_000L
        val finalState = state(
            board = listOf(DominoPiece(6, 6)),
            hands = hands(),
            teamScores = listOf(6, 3),
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.COMMON,
            gameWinnerTeamIndex = 0,
        )
        val accumulator = RankedMatchMetricAccumulator(
            seatMetrics = listOf(
                RankedSeatMatchMetrics(
                    individualPoints = 4,
                    touchesGiven = 2,
                    automaticRounds = 1,
                    assists = 3,
                    automaticPlays = 2,
                ),
                RankedSeatMatchMetrics(),
                RankedSeatMatchMetrics(
                    individualPoints = 2,
                    assists = 1,
                ),
                RankedSeatMatchMetrics(),
            ),
            collectiveCountPointsByTeam = listOf(0, 0),
            completedRounds = 1,
        )
        val result = buildRankedMatchResult(
            matchId = "v2-metric-propagation",
            completedAtEpochMillis = completedAt,
            finalState = finalState,
            playerIdentitiesBySeat = List(4) { seat ->
                RankedMatchPlayerIdentity(
                    playerId = "player-$seat",
                    accountId = "account-$seat",
                )
            },
            accumulator = accumulator,
            rankingRuleVersion = RANKING_RULE_VERSION_V2,
        )

        assertEquals(3, result.players[0].assists)
        assertEquals(2, result.players[0].automaticPlays)

        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = completedAt,
            rankingRuleVersion = RANKING_RULE_VERSION_V2,
        )
        val ladder = buildRankedCycleLadder(
            period = period,
            results = listOf(result),
        )
        val account0 = ladder.standings.single {
            standing -> standing.accountId == "account-0"
        }

        assertEquals(3L, account0.stats.assists)
        assertEquals(2L, account0.stats.automaticPlays)
    }

    @Test
    fun additive_defaults_decode_legacy_metric_payloads() {
        val json = Json

        val accumulator = json.decodeFromString<
            RankedMatchMetricAccumulator
        >(
            """
            {
              "seatMetrics": [
                {
                  "individualPoints": 1,
                  "touchesGiven": 2,
                  "automaticRounds": 1
                }
              ],
              "collectiveCountPointsByTeam": [0, 0],
              "completedRounds": 1
            }
            """.trimIndent(),
        )

        assertEquals(0, accumulator.seatMetrics[0].assists)
        assertEquals(0, accumulator.seatMetrics[0].automaticPlays)
        assertNull(accumulator.leftEndOwnerSeatIndex)
        assertNull(accumulator.rightEndOwnerSeatIndex)

        val player = json.decodeFromString<RankedMatchPlayerResult>(
            """
            {
              "playerId": "legacy-player",
              "accountId": "legacy-account",
              "seatIndex": 0,
              "teamIndex": 0,
              "won": true,
              "victoriesDelta": 1,
              "gamesDelta": 1,
              "teamBalanceDelta": 3,
              "individualPointsScored": 2,
              "touchesGiven": 1,
              "automaticRounds": 0
            }
            """.trimIndent(),
        )

        assertEquals(0, player.assists)
        assertEquals(0, player.automaticPlays)
    }

    private fun scoringHit(
        winKind: RoundWinKind,
        roundPoints: Int,
        addOnLeft: Boolean,
        leftOwner: Int?,
        rightOwner: Int?,
        previousBoard: List<DominoPiece> = listOf(
            DominoPiece(6, 6),
            DominoPiece(6, 1),
        ),
        winningPiece: DominoPiece,
    ): RankedMatchMetricAccumulator {
        val previous = state(
            board = previousBoard,
            hands = hands(
                seat0 = listOf(winningPiece),
            ),
            teamScores = listOf(0, 0),
            lastMove = move(
                seat = 3,
                piece = previousBoard.last(),
            ),
        )
        val updatedBoard = if (addOnLeft) {
            listOf(winningPiece) + previous.board
        } else {
            previous.board + winningPiece
        }
        val updated = previous.copy(
            board = updatedBoard,
            players = previous.players.mapIndexed { seat, player ->
                if (seat == 0) {
                    player.copy(hand = emptyList())
                } else {
                    player
                }
            },
            teamScores = listOf(roundPoints, 0),
            lastMove = move(
                seat = 0,
                piece = winningPiece,
                wasLaELo = winKind == RoundWinKind.LA_E_LO,
                wasCruzada = winKind == RoundWinKind.CRUZADA,
            ),
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = winKind,
        )
        val accumulator = RankedMatchMetricAccumulator(
            seatMetrics = List(4) { RankedSeatMatchMetrics() },
            collectiveCountPointsByTeam = listOf(0, 0),
            leftEndOwnerSeatIndex = leftOwner,
            rightEndOwnerSeatIndex = rightOwner,
        )

        return accumulateRankedMatchTransition(
            accumulator = accumulator,
            previousState = previous,
            updatedState = updated,
            automaticSeatIndexes = emptySet(),
        )
    }

    private fun move(
        seat: Int,
        piece: DominoPiece,
        wasLaELo: Boolean = false,
        wasCruzada: Boolean = false,
    ): PlayedMove {
        return PlayedMove(
            playerIndex = seat,
            piece = piece,
            wasLaELo = wasLaELo,
            wasCruzada = wasCruzada,
        )
    }

    private fun hands(
        seat0: List<DominoPiece> = emptyList(),
        seat1: List<DominoPiece> = emptyList(),
        seat2: List<DominoPiece> = emptyList(),
        seat3: List<DominoPiece> = emptyList(),
    ): List<List<DominoPiece>> {
        return listOf(
            seat0,
            seat1,
            seat2,
            seat3,
        )
    }

    private fun state(
        board: List<DominoPiece>,
        hands: List<List<DominoPiece>>,
        teamScores: List<Int> = listOf(0, 0),
        currentPlayerIndex: Int = 0,
        lastMove: PlayedMove? = null,
        roundWinnerPlayerIndex: Int? = null,
        roundWinnerTeamIndex: Int? = null,
        roundWinKind: RoundWinKind? = null,
        gameWinnerTeamIndex: Int? = null,
    ): DominoGameState {
        return DominoGameState(
            board = board,
            boardChain = DominoBoardChain(),
            players = hands.mapIndexed { seat, hand ->
                DominoPlayer(
                    id = seat,
                    name = "Player ${seat + 1}",
                    hand = hand,
                )
            },
            sleepingPieces = emptyList(),
            currentPlayerIndex = currentPlayerIndex,
            lastRoundWinnerIndex = null,
            openingPiece = board.firstOrNull(),
            teamScores = teamScores,
            lastMove = lastMove,
            roundWinnerPlayerIndex = roundWinnerPlayerIndex,
            roundWinnerTeamIndex = roundWinnerTeamIndex,
            roundWinKind = roundWinKind,
            gameWinnerTeamIndex = gameWinnerTeamIndex,
        )
    }
}