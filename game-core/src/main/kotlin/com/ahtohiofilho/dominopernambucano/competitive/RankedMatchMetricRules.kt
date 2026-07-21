package com.ahtohiofilho.dominopernambucano.competitive

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import com.ahtohiofilho.dominopernambucano.domain.getTeamIndexForPlayer
import com.ahtohiofilho.dominopernambucano.domain.hasPlayablePiece
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import kotlinx.serialization.Serializable

data class RankedRoundMetricDelta(
    val roundWinKind: RoundWinKind,
    val winnerTeamIndex: Int?,
    val teamScoreDelta: Int,
    val individualPointDeltasBySeat: List<Int>,
    val collectiveCountPointsByTeam: List<Int>,
    val automaticRoundSeatIndexes: Set<Int>,
)

@Serializable
data class RankedSeatMatchMetrics(
    val individualPoints: Int = 0,
    val touchesGiven: Int = 0,
    val automaticRounds: Int = 0,
) {
    init {
        require(individualPoints >= 0)
        require(touchesGiven >= 0)
        require(automaticRounds >= 0)
    }
}

data class RankedSeatResultDelta(
    val seatIndex: Int,
    val teamIndex: Int,
    val won: Boolean,
    val victoriesDelta: Int,
    val gamesDelta: Int,
    val teamBalanceDelta: Int,
    val individualPointsDelta: Int,
    val touchesGivenDelta: Int,
    val automaticRoundsDelta: Int,
)

fun calculateRankedRoundMetricDelta(
    previousState: DominoGameState,
    updatedState: DominoGameState,
    automaticSeatIndexes: Set<Int>,
): RankedRoundMetricDelta? {
    if (isRoundFinished(previousState) || !isRoundFinished(updatedState)) {
        return null
    }

    require(previousState.players.size == updatedState.players.size)
    require(previousState.teamScores.size == updatedState.teamScores.size)
    require(automaticSeatIndexes.all { seatIndex ->
        seatIndex in updatedState.players.indices
    })

    val scoreDeltas = updatedState.teamScores.mapIndexed { teamIndex, score ->
        score - previousState.teamScores[teamIndex]
    }

    require(scoreDeltas.all { delta -> delta >= 0 })

    val positiveTeamIndexes = scoreDeltas.indices.filter { teamIndex ->
        scoreDeltas[teamIndex] > 0
    }

    val winKind = requireNotNull(updatedState.roundWinKind)

    if (winKind == RoundWinKind.CLOSED_TIE) {
        require(positiveTeamIndexes.isEmpty())
        require(updatedState.roundWinnerTeamIndex == null)
        require(updatedState.roundWinnerPlayerIndex == null)
    } else {
        require(positiveTeamIndexes.size == 1)
        require(updatedState.roundWinnerTeamIndex == positiveTeamIndexes.single())
    }

    val winnerTeamIndex = updatedState.roundWinnerTeamIndex
    val teamScoreDelta = winnerTeamIndex?.let { teamIndex ->
        scoreDeltas[teamIndex]
    } ?: 0

    val individualPointDeltas = MutableList(updatedState.players.size) { 0 }
    val collectiveCountPoints = MutableList(updatedState.teamScores.size) { 0 }

    when (winKind) {
        RoundWinKind.CLOSED -> {
            val teamIndex = requireNotNull(winnerTeamIndex)
            collectiveCountPoints[teamIndex] = teamScoreDelta
        }

        RoundWinKind.CLOSED_TIE -> Unit

        RoundWinKind.COMMON,
        RoundWinKind.DOUBLE,
        RoundWinKind.LA_E_LO,
        RoundWinKind.CRUZADA -> {
            val winnerPlayerIndex = requireNotNull(
                updatedState.roundWinnerPlayerIndex,
            )
            require(
                getTeamIndexForPlayer(winnerPlayerIndex) == winnerTeamIndex,
            )
            individualPointDeltas[winnerPlayerIndex] = teamScoreDelta
        }
    }

    return RankedRoundMetricDelta(
        roundWinKind = winKind,
        winnerTeamIndex = winnerTeamIndex,
        teamScoreDelta = teamScoreDelta,
        individualPointDeltasBySeat = individualPointDeltas,
        collectiveCountPointsByTeam = collectiveCountPoints,
        automaticRoundSeatIndexes = automaticSeatIndexes.toSet(),
    )
}

fun findRankedTouchGiverSeatIndex(
    previousState: DominoGameState,
    updatedState: DominoGameState,
): Int? {
    val updatedLastMove = updatedState.lastMove ?: return null

    if (previousState.lastMove == updatedLastMove) {
        return null
    }

    if (isRoundFinished(updatedState) || isGameFinished(updatedState)) {
        return null
    }

    if (updatedState.consecutivePassTurns != 0) {
        return null
    }

    val touchedSeatIndex = updatedState.currentPlayerIndex

    if (
        hasPlayablePiece(
            state = updatedState,
            playerIndex = touchedSeatIndex,
        )
    ) {
        return null
    }

    return updatedLastMove.playerIndex
}

fun buildRankedSeatResultDeltas(
    finalState: DominoGameState,
    seatMetrics: List<RankedSeatMatchMetrics>,
): List<RankedSeatResultDelta> {
    require(isGameFinished(finalState))
    require(finalState.players.size == seatMetrics.size)

    val winnerTeamIndex = requireNotNull(finalState.gameWinnerTeamIndex)

    return finalState.players.indices.map { seatIndex ->
        val teamIndex = getTeamIndexForPlayer(seatIndex)
        val teamFinalScore = finalState.teamScores[teamIndex]
        val won = teamIndex == winnerTeamIndex
        val metrics = seatMetrics[seatIndex]

        RankedSeatResultDelta(
            seatIndex = seatIndex,
            teamIndex = teamIndex,
            won = won,
            victoriesDelta = if (won) 1 else 0,
            gamesDelta = 1,
            teamBalanceDelta = if (won) {
                teamFinalScore
            } else {
                -teamFinalScore
            },
            individualPointsDelta = metrics.individualPoints,
            touchesGivenDelta = metrics.touchesGiven,
            automaticRoundsDelta = metrics.automaticRounds,
        )
    }
}
