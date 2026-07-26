package com.ahtohiofilho.dominopernambucano.competitive

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable

const val CANONICAL_RANKING_TIME_ZONE_ID = "America/Recife"

@Serializable
enum class RankingCycleKind {
    DAILY,
    WEEKLY,
    MONTHLY,
    ANNUAL,
}

@Serializable
data class RankedCyclePeriod(
    val cycleId: String,
    val kind: RankingCycleKind,
    val rankingRuleVersion: Int,
    val timeZoneId: String,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
) {
    init {
        require(cycleId.isNotBlank())
        require(rankingRuleVersion > 0)
        require(timeZoneId == CANONICAL_RANKING_TIME_ZONE_ID)
        require(startsAtEpochMillis >= 0L)
        require(endsAtEpochMillis > startsAtEpochMillis)
    }

    fun contains(
        epochMillis: Long,
    ): Boolean {
        return epochMillis >= startsAtEpochMillis &&
                epochMillis < endsAtEpochMillis
    }
}

fun resolveRankingCycles(
    completedAtEpochMillis: Long,
    rankingRuleVersion: Int = CURRENT_RANKING_RULE_VERSION,
): List<RankedCyclePeriod> {
    return RankingCycleKind.values().map { kind ->
        resolveRankingCycle(
            kind = kind,
            completedAtEpochMillis = completedAtEpochMillis,
            rankingRuleVersion = rankingRuleVersion,
        )
    }
}

fun resolveRankingCycle(
    kind: RankingCycleKind,
    completedAtEpochMillis: Long,
    rankingRuleVersion: Int = CURRENT_RANKING_RULE_VERSION,
): RankedCyclePeriod {
    require(completedAtEpochMillis >= 0L)
    require(rankingRuleVersion > 0)

    val start = canonicalRankingCalendar(
        epochMillis = completedAtEpochMillis,
    )

    clearTimeOfDay(start)

    when (kind) {
        RankingCycleKind.DAILY -> Unit

        RankingCycleKind.WEEKLY -> {
            val daysSinceMonday = (
                    start.get(Calendar.DAY_OF_WEEK) -
                            Calendar.MONDAY +
                            7
                    ) % 7

            start.add(
                Calendar.DAY_OF_MONTH,
                -daysSinceMonday,
            )
        }

        RankingCycleKind.MONTHLY -> {
            start.set(
                Calendar.DAY_OF_MONTH,
                1,
            )
        }

        RankingCycleKind.ANNUAL -> {
            start.set(
                Calendar.MONTH,
                Calendar.JANUARY,
            )
            start.set(
                Calendar.DAY_OF_MONTH,
                1,
            )
        }
    }

    val end = start.clone() as Calendar

    when (kind) {
        RankingCycleKind.DAILY -> end.add(
            Calendar.DAY_OF_MONTH,
            1,
        )

        RankingCycleKind.WEEKLY -> end.add(
            Calendar.DAY_OF_MONTH,
            7,
        )

        RankingCycleKind.MONTHLY -> end.add(
            Calendar.MONTH,
            1,
        )

        RankingCycleKind.ANNUAL -> end.add(
            Calendar.YEAR,
            1,
        )
    }

    return RankedCyclePeriod(
        cycleId = buildRankingCycleId(
            kind = kind,
            rankingRuleVersion = rankingRuleVersion,
            start = start,
        ),
        kind = kind,
        rankingRuleVersion = rankingRuleVersion,
        timeZoneId = CANONICAL_RANKING_TIME_ZONE_ID,
        startsAtEpochMillis = start.timeInMillis,
        endsAtEpochMillis = end.timeInMillis,
    ).also { period ->
        check(period.contains(completedAtEpochMillis))
    }
}

private fun canonicalRankingCalendar(
    epochMillis: Long,
): Calendar {
    val timeZone = TimeZone.getTimeZone(
        CANONICAL_RANKING_TIME_ZONE_ID,
    )

    check(timeZone.id == CANONICAL_RANKING_TIME_ZONE_ID)

    return GregorianCalendar(
        timeZone,
        Locale.ROOT,
    ).apply {
        isLenient = false
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
        timeInMillis = epochMillis
    }
}

private fun clearTimeOfDay(
    calendar: Calendar,
) {
    calendar.set(
        Calendar.HOUR_OF_DAY,
        0,
    )
    calendar.set(
        Calendar.MINUTE,
        0,
    )
    calendar.set(
        Calendar.SECOND,
        0,
    )
    calendar.set(
        Calendar.MILLISECOND,
        0,
    )
}

private fun buildRankingCycleId(
    kind: RankingCycleKind,
    rankingRuleVersion: Int,
    start: Calendar,
): String {
    val periodKey = when (kind) {
        RankingCycleKind.DAILY -> String.format(
            Locale.ROOT,
            "%04d-%02d-%02d",
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH) + 1,
            start.get(Calendar.DAY_OF_MONTH),
        )

        RankingCycleKind.WEEKLY -> String.format(
            Locale.ROOT,
            "%04d-%02d-%02d",
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH) + 1,
            start.get(Calendar.DAY_OF_MONTH),
        )

        RankingCycleKind.MONTHLY -> String.format(
            Locale.ROOT,
            "%04d-%02d",
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH) + 1,
        )

        RankingCycleKind.ANNUAL -> String.format(
            Locale.ROOT,
            "%04d",
            start.get(Calendar.YEAR),
        )
    }

    return "ranking-v$rankingRuleVersion:" +
            kind.name.lowercase(Locale.ROOT) +
            ":" +
            periodKey
}
