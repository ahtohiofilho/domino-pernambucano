package com.ahtohiofilho.dominopernambucano.competitive

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState

data class RankedMatchMetricAccumulator(
    val seatMetrics: List<RankedSeatMatchMetrics>,
    val collectiveCountPointsByTeam: List<Int>,
    val completedRounds: Int = 0,
) {
    init {
        require(seatMetrics.isNotEmpty())
        require(collectiveCountPointsByTeam.isNotEmpty())
        require(collectiveCountPointsByTeam.all { points -> points >= 0 })
        require(completedRounds >= 0)
    }

    companion object {
        fun empty(
            playerCount: Int,
            teamCount: Int,
        ): RankedMatchMetricAccumulator {
            require(playerCount > 0)
            require(teamCount > 0)

            return RankedMatchMetricAccumulator(
                seatMetrics = List(playerCount) {
                    RankedSeatMatchMetrics()
                },
                collectiveCountPointsByTeam = List(teamCount) { 0 },
            )
        }
    }
}


fun didRankedSeatPlayPiece(
    previousState: DominoGameState,
    updatedState: DominoGameState,
    seatIndex: Int,
): Boolean {
    require(previousState.players.size == updatedState.players.size)
    require(seatIndex in previousState.players.indices)

    val previousHandSize = previousState.players[seatIndex].hand.size
    val updatedHandSize = updatedState.players[seatIndex].hand.size

    return updatedHandSize == previousHandSize - 1 &&
            updatedState.board.size == previousState.board.size + 1 &&
            updatedState.lastMove?.playerIndex == seatIndex
}

fun accumulateRankedMatchTransition(
    accumulator: RankedMatchMetricAccumulator,
    previousState: DominoGameState,
    updatedState: DominoGameState,
    automaticSeatIndexes: Set<Int>,
): RankedMatchMetricAccumulator {
    require(previousState.players.size == updatedState.players.size)
    require(previousState.teamScores.size == updatedState.teamScores.size)
    require(accumulator.seatMetrics.size == updatedState.players.size)
    require(
        accumulator.collectiveCountPointsByTeam.size ==
                updatedState.teamScores.size,
    )

    var updatedSeatMetrics = accumulator.seatMetrics

    findRankedTouchGiverSeatIndex(
        previousState = previousState,
        updatedState = updatedState,
    )?.let { touchGiverSeatIndex ->
        updatedSeatMetrics = updatedSeatMetrics.mapIndexed { seatIndex, metrics ->
            if (seatIndex == touchGiverSeatIndex) {
                metrics.copy(
                    touchesGiven = metrics.touchesGiven + 1,
                )
            } else {
                metrics
            }
        }
    }

    val roundDelta = calculateRankedRoundMetricDelta(
        previousState = previousState,
        updatedState = updatedState,
        automaticSeatIndexes = automaticSeatIndexes,
    ) ?: return if (updatedSeatMetrics == accumulator.seatMetrics) {
        accumulator
    } else {
        accumulator.copy(
            seatMetrics = updatedSeatMetrics,
        )
    }

    updatedSeatMetrics = updatedSeatMetrics.mapIndexed { seatIndex, metrics ->
        metrics.copy(
            individualPoints = metrics.individualPoints +
                    roundDelta.individualPointDeltasBySeat[seatIndex],
            automaticRounds = metrics.automaticRounds +
                    if (seatIndex in roundDelta.automaticRoundSeatIndexes) {
                        1
                    } else {
                        0
                    },
        )
    }

    val updatedCollectiveCountPoints =
        accumulator.collectiveCountPointsByTeam.mapIndexed { teamIndex, points ->
            points + roundDelta.collectiveCountPointsByTeam[teamIndex]
        }

    return accumulator.copy(
        seatMetrics = updatedSeatMetrics,
        collectiveCountPointsByTeam = updatedCollectiveCountPoints,
        completedRounds = accumulator.completedRounds + 1,
    )
}
