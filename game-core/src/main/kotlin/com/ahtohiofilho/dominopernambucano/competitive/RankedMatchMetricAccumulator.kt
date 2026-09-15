package com.ahtohiofilho.dominopernambucano.competitive

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import com.ahtohiofilho.dominopernambucano.domain.getTeamIndexForPlayer
import kotlinx.serialization.Serializable

@Serializable
data class RankedMatchMetricAccumulator(
    val seatMetrics: List<RankedSeatMatchMetrics>,
    val collectiveCountPointsByTeam: List<Int>,
    val completedRounds: Int = 0,
    val leftEndOwnerSeatIndex: Int? = null,
    val rightEndOwnerSeatIndex: Int? = null,
) {
    init {
        require(seatMetrics.isNotEmpty())
        require(collectiveCountPointsByTeam.isNotEmpty())
        require(collectiveCountPointsByTeam.all { points -> points >= 0 })
        require(completedRounds >= 0)
        require(
            leftEndOwnerSeatIndex == null ||
                leftEndOwnerSeatIndex in seatMetrics.indices,
        )
        require(
            rightEndOwnerSeatIndex == null ||
                rightEndOwnerSeatIndex in seatMetrics.indices,
        )
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

private fun findRankedPlayedSeatIndex(
    previousState: DominoGameState,
    updatedState: DominoGameState,
): Int? {
    val updatedLastMove = updatedState.lastMove ?: return null

    if (previousState.lastMove == updatedLastMove) {
        return null
    }

    val seatIndex = updatedLastMove.playerIndex

    return seatIndex.takeIf {
        didRankedSeatPlayPiece(
            previousState = previousState,
            updatedState = updatedState,
            seatIndex = seatIndex,
        )
    }
}

private fun findRankedPlayedBoardSide(
    previousState: DominoGameState,
    updatedState: DominoGameState,
): BoardSide? {
    if (
        updatedState.board.size !=
            previousState.board.size + 1
    ) {
        return null
    }

    if (previousState.board.isEmpty()) {
        return null
    }

    val addedOnLeft =
        updatedState.board.drop(1) == previousState.board
    val addedOnRight =
        updatedState.board.dropLast(1) == previousState.board

    require(addedOnLeft.xor(addedOnRight)) {
        "Ranked transition must add exactly one board end."
    }

    return if (addedOnLeft) {
        BoardSide.LEFT
    } else {
        BoardSide.RIGHT
    }
}

private fun updatedRankedBoardEndOwners(
    accumulator: RankedMatchMetricAccumulator,
    previousState: DominoGameState,
    updatedState: DominoGameState,
    playedSeatIndex: Int?,
): Pair<Int?, Int?> {
    if (playedSeatIndex == null) {
        return Pair(
            accumulator.leftEndOwnerSeatIndex,
            accumulator.rightEndOwnerSeatIndex,
        )
    }

    if (previousState.board.isEmpty()) {
        require(updatedState.board.size == 1)

        return Pair(
            playedSeatIndex,
            playedSeatIndex,
        )
    }

    return when (
        requireNotNull(
            findRankedPlayedBoardSide(
                previousState = previousState,
                updatedState = updatedState,
            ),
        )
    ) {
        BoardSide.LEFT -> Pair(
            playedSeatIndex,
            accumulator.rightEndOwnerSeatIndex,
        )

        BoardSide.RIGHT -> Pair(
            accumulator.leftEndOwnerSeatIndex,
            playedSeatIndex,
        )
    }
}

private fun findRankedAssistSeatIndex(
    accumulator: RankedMatchMetricAccumulator,
    previousState: DominoGameState,
    updatedState: DominoGameState,
    roundDelta: RankedRoundMetricDelta,
    playedSeatIndex: Int?,
): Int? {
    val winnerSeatIndex =
        updatedState.roundWinnerPlayerIndex ?: return null

    if (playedSeatIndex != winnerSeatIndex) {
        return null
    }

    val relevantOwners = when (roundDelta.roundWinKind) {
        RoundWinKind.CLOSED,
        RoundWinKind.CLOSED_TIE -> return null

        RoundWinKind.COMMON,
        RoundWinKind.DOUBLE -> {
            when (
                requireNotNull(
                    findRankedPlayedBoardSide(
                        previousState = previousState,
                        updatedState = updatedState,
                    ),
                )
            ) {
                BoardSide.LEFT ->
                    listOf(accumulator.leftEndOwnerSeatIndex)

                BoardSide.RIGHT ->
                    listOf(accumulator.rightEndOwnerSeatIndex)
            }
        }

        RoundWinKind.LA_E_LO,
        RoundWinKind.CRUZADA -> listOf(
            accumulator.leftEndOwnerSeatIndex,
            accumulator.rightEndOwnerSeatIndex,
        )
    }

    val winnerTeamIndex =
        getTeamIndexForPlayer(winnerSeatIndex)

    val qualifyingPartnerSeats = relevantOwners
        .filterNotNull()
        .filter { ownerSeatIndex ->
            ownerSeatIndex != winnerSeatIndex &&
                getTeamIndexForPlayer(ownerSeatIndex) ==
                    winnerTeamIndex
        }
        .distinct()

    require(qualifyingPartnerSeats.size <= 1) {
        "A ranked assist can belong to only one partner."
    }

    return qualifyingPartnerSeats.singleOrNull()
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

    val playedSeatIndex = findRankedPlayedSeatIndex(
        previousState = previousState,
        updatedState = updatedState,
    )

    var updatedSeatMetrics = accumulator.seatMetrics

    if (
        playedSeatIndex != null &&
        playedSeatIndex in automaticSeatIndexes
    ) {
        updatedSeatMetrics =
            updatedSeatMetrics.mapIndexed { seatIndex, metrics ->
                if (seatIndex == playedSeatIndex) {
                    metrics.copy(
                        automaticPlays =
                            metrics.automaticPlays + 1,
                    )
                } else {
                    metrics
                }
            }
    }

    findRankedTouchGiverSeatIndex(
        previousState = previousState,
        updatedState = updatedState,
    )?.let { touchGiverSeatIndex ->
        updatedSeatMetrics =
            updatedSeatMetrics.mapIndexed { seatIndex, metrics ->
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
    )

    if (roundDelta == null) {
        val (leftOwner, rightOwner) =
            updatedRankedBoardEndOwners(
                accumulator = accumulator,
                previousState = previousState,
                updatedState = updatedState,
                playedSeatIndex = playedSeatIndex,
            )

        return if (
            updatedSeatMetrics == accumulator.seatMetrics &&
            leftOwner == accumulator.leftEndOwnerSeatIndex &&
            rightOwner == accumulator.rightEndOwnerSeatIndex
        ) {
            accumulator
        } else {
            accumulator.copy(
                seatMetrics = updatedSeatMetrics,
                leftEndOwnerSeatIndex = leftOwner,
                rightEndOwnerSeatIndex = rightOwner,
            )
        }
    }

    val assistSeatIndex = findRankedAssistSeatIndex(
        accumulator = accumulator,
        previousState = previousState,
        updatedState = updatedState,
        roundDelta = roundDelta,
        playedSeatIndex = playedSeatIndex,
    )

    updatedSeatMetrics =
        updatedSeatMetrics.mapIndexed { seatIndex, metrics ->
            metrics.copy(
                individualPoints =
                    metrics.individualPoints +
                        roundDelta
                            .individualPointDeltasBySeat[seatIndex],
                automaticRounds =
                    metrics.automaticRounds +
                        if (
                            seatIndex in
                            roundDelta.automaticRoundSeatIndexes
                        ) {
                            1
                        } else {
                            0
                        },
                assists =
                    metrics.assists +
                        if (seatIndex == assistSeatIndex) {
                            roundDelta.teamScoreDelta
                        } else {
                            0
                        },
            )
        }

    val updatedCollectiveCountPoints =
        accumulator.collectiveCountPointsByTeam.mapIndexed {
                teamIndex,
                points,
            ->
            points +
                roundDelta.collectiveCountPointsByTeam[teamIndex]
        }

    return accumulator.copy(
        seatMetrics = updatedSeatMetrics,
        collectiveCountPointsByTeam =
            updatedCollectiveCountPoints,
        completedRounds = accumulator.completedRounds + 1,
        leftEndOwnerSeatIndex = null,
        rightEndOwnerSeatIndex = null,
    )
}