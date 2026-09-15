package com.ahtohiofilho.dominopernambucano.competitive

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import kotlinx.serialization.Serializable

const val RANKING_RULE_VERSION_V1 = 1
const val RANKING_RULE_VERSION_V2 = 2

/*
 * O V2 permanece preparado, mas ainda não é ativado como regra corrente
 * neste subciclo. A ativação só acontece depois da migração de
 * persistência/API/histórico.
 */
const val CURRENT_RANKING_RULE_VERSION = RANKING_RULE_VERSION_V1

@Serializable
enum class RankedMatchClassification {
    UNRANKED,
    RANKED,
}

@Serializable
data class RankedMatchPlayerIdentity(
    val playerId: String,
    val accountId: String? = null,
) {
    init {
        require(playerId.isNotBlank())
        require(accountId == null || accountId.isNotBlank())
    }
}

@Serializable
data class RankedMatchPlayerResult(
    val playerId: String,
    val accountId: String? = null,
    val seatIndex: Int,
    val teamIndex: Int,
    val won: Boolean,
    val victoriesDelta: Int,
    val gamesDelta: Int,
    val teamBalanceDelta: Int,
    val individualPointsScored: Int,
    val touchesGiven: Int,
    val automaticRounds: Int,
    val assists: Int = 0,
    val automaticPlays: Int = 0,
) {
    init {
        require(playerId.isNotBlank())
        require(accountId == null || accountId.isNotBlank())
        require(seatIndex >= 0)
        require(teamIndex >= 0)
        require(victoriesDelta in 0..1)
        require(gamesDelta == 1)
        require(individualPointsScored >= 0)
        require(touchesGiven >= 0)
        require(automaticRounds >= 0)
        require(assists >= 0)
        require(automaticPlays >= 0)
    }
}

@Serializable
data class RankedMatchResult(
    val resultId: String,
    val matchId: String,
    val rankingRuleVersion: Int,
    val completedAtEpochMillis: Long,
    val finalTeamScores: List<Int>,
    val collectiveCountPointsByTeam: List<Int>,
    val completedRounds: Int,
    val players: List<RankedMatchPlayerResult>,
) {
    init {
        require(resultId.isNotBlank())
        require(matchId.isNotBlank())
        require(resultId == createRankedMatchResultId(matchId))
        require(rankingRuleVersion > 0)
        require(completedAtEpochMillis >= 0L)
        require(finalTeamScores.isNotEmpty())
        require(finalTeamScores.all { score -> score >= 0 })
        require(
            collectiveCountPointsByTeam.size ==
                    finalTeamScores.size,
        )
        require(
            collectiveCountPointsByTeam.all { points ->
                points >= 0
            },
        )
        require(completedRounds > 0)
        require(players.isNotEmpty())
        require(
            players.map { player -> player.playerId }.distinct().size ==
                    players.size,
        )
        require(
            players.map { player -> player.seatIndex }.toSet() ==
                    players.indices.toSet(),
        )
    }
}

fun createRankedMatchResultId(
    matchId: String,
): String {
    require(matchId.isNotBlank())

    return "ranked-match-result:$matchId"
}

fun buildRankedMatchResult(
    matchId: String,
    completedAtEpochMillis: Long,
    finalState: DominoGameState,
    playerIdentitiesBySeat: List<RankedMatchPlayerIdentity>,
    accumulator: RankedMatchMetricAccumulator,
    rankingRuleVersion: Int = CURRENT_RANKING_RULE_VERSION,
): RankedMatchResult {
    require(matchId.isNotBlank())
    require(completedAtEpochMillis >= 0L)
    require(rankingRuleVersion > 0)
    require(isGameFinished(finalState))
    require(playerIdentitiesBySeat.size == finalState.players.size)
    require(accumulator.seatMetrics.size == finalState.players.size)
    require(
        accumulator.collectiveCountPointsByTeam.size ==
                finalState.teamScores.size,
    )
    require(accumulator.completedRounds > 0)

    val seatDeltas = buildRankedSeatResultDeltas(
        finalState = finalState,
        seatMetrics = accumulator.seatMetrics,
    )

    return RankedMatchResult(
        resultId = createRankedMatchResultId(matchId),
        matchId = matchId,
        rankingRuleVersion = rankingRuleVersion,
        completedAtEpochMillis = completedAtEpochMillis,
        finalTeamScores = finalState.teamScores.toList(),
        collectiveCountPointsByTeam =
            accumulator.collectiveCountPointsByTeam.toList(),
        completedRounds = accumulator.completedRounds,
        players = seatDeltas.map { delta ->
            val identity = playerIdentitiesBySeat[delta.seatIndex]

            RankedMatchPlayerResult(
                playerId = identity.playerId,
                accountId = identity.accountId,
                seatIndex = delta.seatIndex,
                teamIndex = delta.teamIndex,
                won = delta.won,
                victoriesDelta = delta.victoriesDelta,
                gamesDelta = delta.gamesDelta,
                teamBalanceDelta = delta.teamBalanceDelta,
                individualPointsScored =
                    delta.individualPointsDelta,
                touchesGiven = delta.touchesGivenDelta,
                automaticRounds = delta.automaticRoundsDelta,
                assists = delta.assistsDelta,
                automaticPlays = delta.automaticPlaysDelta,
            )
        },
    )
}
