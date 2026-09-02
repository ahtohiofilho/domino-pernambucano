package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchMetricAccumulator
import com.ahtohiofilho.dominopernambucano.competitive.accumulateRankedMatchTransition
import com.ahtohiofilho.dominopernambucano.competitive.buildRankedSeatResultDeltas
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.getTeamIndexForPlayer
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished

data class PostMatchPlayerStatistics(
    val seatIndex: Int,
    val pointsScored: Int,
    val touchesGiven: Int,
    val automaticRounds: Int,
)

data class PostMatchStatistics(
    val completedRounds: Int,
    val collectiveCountPointsByTeam: List<Int>,
    val teamBalanceDeltaByTeam: List<Int>,
    val players: List<PostMatchPlayerStatistics>,
    val mvpSeatIndex: Int?,
)

class PostMatchStatisticsTracker(
    initialState: DominoGameState,
) {
    private var previousState = initialState
    private var accumulator = RankedMatchMetricAccumulator.empty(
        playerCount = initialState.players.size,
        teamCount = initialState.teamScores.size,
    )

    fun snapshot(): PostMatchStatistics {
        return createPostMatchStatistics(
            accumulator = accumulator,
            finalOrCurrentState = previousState,
        )
    }

    fun accept(
        updatedState: DominoGameState,
    ): PostMatchStatistics {
        val shouldReset =
            previousState.players.size != updatedState.players.size ||
                    previousState.teamScores.size != updatedState.teamScores.size ||
                    (
                        isGameFinished(previousState) &&
                                !isGameFinished(updatedState) &&
                                updatedState.teamScores.all { score -> score == 0 }
                    )

        if (shouldReset) {
            accumulator = RankedMatchMetricAccumulator.empty(
                playerCount = updatedState.players.size,
                teamCount = updatedState.teamScores.size,
            )
            previousState = updatedState
            return snapshot()
        }

        if (updatedState != previousState) {
            accumulator = accumulateRankedMatchTransition(
                accumulator = accumulator,
                previousState = previousState,
                updatedState = updatedState,
                automaticSeatIndexes = emptySet(),
            )
        }

        previousState = updatedState
        return snapshot()
    }
}

internal fun createPostMatchStatistics(
    accumulator: RankedMatchMetricAccumulator,
    finalOrCurrentState: DominoGameState,
): PostMatchStatistics {
    require(
        accumulator.seatMetrics.size ==
                finalOrCurrentState.players.size,
    )
    require(
        accumulator.collectiveCountPointsByTeam.size ==
                finalOrCurrentState.teamScores.size,
    )

    val teamBalanceDeltaByTeam =
        if (isGameFinished(finalOrCurrentState)) {
            val resultDeltas = buildRankedSeatResultDeltas(
                finalState = finalOrCurrentState,
                seatMetrics = accumulator.seatMetrics,
            )

            finalOrCurrentState.teamScores.indices.map { teamIndex ->
                requireNotNull(
                    resultDeltas.firstOrNull { delta ->
                        delta.teamIndex == teamIndex
                    },
                ).teamBalanceDelta
            }
        } else {
            List(finalOrCurrentState.teamScores.size) { 0 }
        }

    val players = accumulator.seatMetrics.mapIndexed { seatIndex, metrics ->
        PostMatchPlayerStatistics(
            seatIndex = seatIndex,
            pointsScored = metrics.individualPoints,
            touchesGiven = metrics.touchesGiven,
            automaticRounds = metrics.automaticRounds,
        )
    }

    return PostMatchStatistics(
        completedRounds = accumulator.completedRounds,
        collectiveCountPointsByTeam =
            accumulator.collectiveCountPointsByTeam.toList(),
        teamBalanceDeltaByTeam = teamBalanceDeltaByTeam,
        players = players,
        mvpSeatIndex = if (isGameFinished(finalOrCurrentState)) {
            resolvePostMatchMvpSeatIndex(
                players = players,
                winnerTeamIndex = finalOrCurrentState.gameWinnerTeamIndex,
            )
        } else {
            null
        },
    )
}

internal fun calculatePostMatchMvpScoreTimesTwo(
    pointsScored: Int,
    touchesGiven: Int,
    automaticRounds: Int,
): Int {
    require(pointsScored >= 0)
    require(touchesGiven >= 0)
    require(automaticRounds >= 0)

    /*
     * 2 x MVP score:
     * 2 x (2*points + touches - 0.5*automatic)
     * = 4*points + 2*touches - automatic.
     *
     * Keeping this integer avoids floating-point tie noise.
     */
    return (4 * pointsScored) +
            (2 * touchesGiven) -
            automaticRounds
}

internal fun resolvePostMatchMvpSeatIndex(
    players: List<PostMatchPlayerStatistics>,
    winnerTeamIndex: Int?,
): Int? {
    return players.sortedWith(
        compareByDescending<PostMatchPlayerStatistics> { player ->
            calculatePostMatchMvpScoreTimesTwo(
                pointsScored = player.pointsScored,
                touchesGiven = player.touchesGiven,
                automaticRounds = player.automaticRounds,
            )
        }
            .thenByDescending { player -> player.pointsScored }
            .thenByDescending { player -> player.touchesGiven }
            .thenBy { player -> player.automaticRounds }
            .thenByDescending { player ->
                if (
                    winnerTeamIndex != null &&
                    getTeamIndexForPlayer(player.seatIndex) == winnerTeamIndex
                ) {
                    1
                } else {
                    0
                }
            }
            .thenBy { player -> player.seatIndex },
    ).firstOrNull()?.seatIndex
}