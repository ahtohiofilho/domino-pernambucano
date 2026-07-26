package com.ahtohiofilho.dominopernambucano.competitive

data class RankedCycleStanding(
    val rank: Int,
    val accountId: String,
    val stats: RankedLadderStats,
) {
    init {
        require(rank > 0)
        require(accountId.isNotBlank())
        require(stats.isEligible)
    }
}

data class RankedCycleLadder(
    val period: RankedCyclePeriod,
    val resultCount: Int,
    val standings: List<RankedCycleStanding>,
) {
    init {
        require(resultCount >= 0)
        require(
            standings.map { standing -> standing.rank } ==
                    (1..standings.size).toList(),
        )
        require(
            standings.map { standing -> standing.accountId }
                .distinct()
                .size == standings.size,
        )
    }
}

fun buildRankedCycleLadder(
    period: RankedCyclePeriod,
    results: Collection<RankedMatchResult>,
): RankedCycleLadder {
    require(
        results.map { result -> result.resultId }.distinct().size ==
                results.size,
    ) {
        "Há resultados ranqueados duplicados."
    }

    val matchingResults = results.filter { result ->
        result.rankingRuleVersion == period.rankingRuleVersion &&
                period.contains(result.completedAtEpochMillis)
    }
    val statsByAccountId = linkedMapOf<String, RankedLadderStats>()

    matchingResults.forEach { result ->
        val accountIds = result.players.map { player ->
            requireNotNull(player.accountId) {
                "Resultado ranqueado sem accountId canônico."
            }.also { accountId ->
                require(accountId.isNotBlank()) {
                    "Resultado ranqueado com accountId vazio."
                }
            }
        }

        require(accountIds.distinct().size == accountIds.size) {
            "Uma conta aparece mais de uma vez no mesmo resultado."
        }

        result.players.forEachIndexed { index, player ->
            val accountId = accountIds[index]
            val current = statsByAccountId[accountId]
                ?: RankedLadderStats()

            statsByAccountId[accountId] = current.accumulate(player)
        }
    }

    val rankedEntries = rankEligibleEntries(
        statsByAccountId.map { (accountId, stats) ->
            RankedLadderEntry(
                technicalId = accountId,
                stats = stats,
            )
        },
    )

    return RankedCycleLadder(
        period = period,
        resultCount = matchingResults.size,
        standings = rankedEntries.mapIndexed { index, entry ->
            RankedCycleStanding(
                rank = index + 1,
                accountId = entry.technicalId,
                stats = entry.stats,
            )
        },
    )
}

private fun RankedLadderStats.accumulate(
    result: RankedMatchPlayerResult,
): RankedLadderStats {
    return RankedLadderStats(
        victories = Math.addExact(
            victories,
            result.victoriesDelta.toLong(),
        ),
        games = Math.addExact(
            games,
            result.gamesDelta.toLong(),
        ),
        teamBalance = Math.addExact(
            teamBalance,
            result.teamBalanceDelta.toLong(),
        ),
        individualPoints = Math.addExact(
            individualPoints,
            result.individualPointsScored.toLong(),
        ),
        touchesGiven = Math.addExact(
            touchesGiven,
            result.touchesGiven.toLong(),
        ),
        automaticRounds = Math.addExact(
            automaticRounds,
            result.automaticRounds.toLong(),
        ),
    )
}
