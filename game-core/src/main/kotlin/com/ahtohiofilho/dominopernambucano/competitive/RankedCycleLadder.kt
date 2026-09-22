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

        val entries = standings.map { standing ->
            RankedLadderEntry(
                technicalId = standing.accountId,
                stats = standing.stats,
            )
        }
        val canonicalEntries = rankEligibleEntries(
            entries = entries,
            rankingRuleVersion = period.rankingRuleVersion,
        )

        require(
            canonicalEntries.map { entry -> entry.technicalId } ==
                entries.map { entry -> entry.technicalId },
        )
        require(
            standings.map { standing -> standing.rank } ==
                buildPublicRanks(
                    rankedEntries = entries,
                    rankingRuleVersion = period.rankingRuleVersion,
                ),
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
    excludedAccountIds: Set<String> = emptySet(),
): RankedCycleLadder {
    require(
        excludedAccountIds.none { accountId -> accountId.isBlank() },
    )
    require(
        results.map { result -> result.resultId }.distinct().size ==
            results.size,
    ) {
        "Há resultados ranqueados duplicados."
    }

    val matchingResults = results.filter { result ->
        result.rankingRuleVersion == period.rankingRuleVersion &&
            period.contains(result.completedAtEpochMillis) &&
            result.players.any { player ->
                player.accountId !in excludedAccountIds
            }
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

            if (accountId in excludedAccountIds) {
                return@forEachIndexed
            }

            val current = statsByAccountId[accountId]
                ?: RankedLadderStats()

            statsByAccountId[accountId] = current.accumulate(player)
        }
    }

    val rankedEntries = rankEligibleEntries(
        entries = statsByAccountId.map { (accountId, stats) ->
            RankedLadderEntry(
                technicalId = accountId,
                stats = stats,
            )
        },
        rankingRuleVersion = period.rankingRuleVersion,
    )
    val publicRanks = buildPublicRanks(
        rankedEntries = rankedEntries,
        rankingRuleVersion = period.rankingRuleVersion,
    )

    return RankedCycleLadder(
        period = period,
        resultCount = matchingResults.size,
        standings = rankedEntries.mapIndexed { index, entry ->
            RankedCycleStanding(
                rank = publicRanks[index],
                accountId = entry.technicalId,
                stats = entry.stats,
            )
        },
    )
}

private fun buildPublicRanks(
    rankedEntries: List<RankedLadderEntry>,
    rankingRuleVersion: Int,
): List<Int> {
    var previousEntry: RankedLadderEntry? = null
    var previousRank = 0

    return rankedEntries.mapIndexed { index, entry ->
        val currentRank = if (
            previousEntry != null &&
            areRankedLadderEntriesPubliclyTied(
                left = requireNotNull(previousEntry),
                right = entry,
                rankingRuleVersion = rankingRuleVersion,
            )
        ) {
            previousRank
        } else {
            index + 1
        }

        previousEntry = entry
        previousRank = currentRank
        currentRank
    }
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
        assists = Math.addExact(
            assists,
            result.assists.toLong(),
        ),
        touchesGiven = Math.addExact(
            touchesGiven,
            result.touchesGiven.toLong(),
        ),
        automaticRounds = Math.addExact(
            automaticRounds,
            result.automaticRounds.toLong(),
        ),
        automaticPlays = Math.addExact(
            automaticPlays,
            result.automaticPlays.toLong(),
        ),
        timeoutRounds = Math.addExact(
            timeoutRounds,
            result.timeoutRounds.toLong(),
        ),
    )
}
