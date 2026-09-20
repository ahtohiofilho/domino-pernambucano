package com.ahtohiofilho.dominopernambucano.competitive

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingCyclePeriodTest {
    @Test
    fun daily_cycle_uses_recife_midnight_and_end_exclusive() {
        val instant = epochMillis(
            year = 2026,
            month = 7,
            day = 25,
            hour = 14,
            minute = 30,
        )

        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = instant,
        )

        assertEquals(
            "ranking-v$CURRENT_RANKING_RULE_VERSION:daily:2026-07-25",
            period.cycleId,
        )
        assertEquals(
            epochMillis(2026, 7, 25),
            period.startsAtEpochMillis,
        )
        assertEquals(
            epochMillis(2026, 7, 26),
            period.endsAtEpochMillis,
        )
        assertTrue(period.contains(period.startsAtEpochMillis))
        assertFalse(period.contains(period.endsAtEpochMillis))
    }

    @Test
    fun weekly_cycle_runs_from_monday_through_sunday() {
        val sunday = epochMillis(
            year = 2026,
            month = 7,
            day = 26,
            hour = 23,
            minute = 59,
        )

        val period = resolveRankingCycle(
            kind = RankingCycleKind.WEEKLY,
            completedAtEpochMillis = sunday,
        )

        assertEquals(
            "ranking-v$CURRENT_RANKING_RULE_VERSION:weekly:2026-07-20",
            period.cycleId,
        )
        assertEquals(
            epochMillis(2026, 7, 20),
            period.startsAtEpochMillis,
        )
        assertEquals(
            epochMillis(2026, 7, 27),
            period.endsAtEpochMillis,
        )

        val nextMonday = resolveRankingCycle(
            kind = RankingCycleKind.WEEKLY,
            completedAtEpochMillis = period.endsAtEpochMillis,
        )

        assertEquals(
            "ranking-v$CURRENT_RANKING_RULE_VERSION:weekly:2026-07-27",
            nextMonday.cycleId,
        )
    }

    @Test
    fun monthly_and_annual_cycles_cross_year_without_overlap() {
        val december = epochMillis(
            year = 2026,
            month = 12,
            day = 31,
            hour = 23,
            minute = 59,
            second = 59,
            millisecond = 999,
        )

        val monthly = resolveRankingCycle(
            kind = RankingCycleKind.MONTHLY,
            completedAtEpochMillis = december,
        )
        val annual = resolveRankingCycle(
            kind = RankingCycleKind.ANNUAL,
            completedAtEpochMillis = december,
        )

        assertEquals(
            "ranking-v$CURRENT_RANKING_RULE_VERSION:monthly:2026-12",
            monthly.cycleId,
        )
        assertEquals(
            epochMillis(2027, 1, 1),
            monthly.endsAtEpochMillis,
        )
        assertEquals(
            "ranking-v$CURRENT_RANKING_RULE_VERSION:annual:2026",
            annual.cycleId,
        )
        assertEquals(
            epochMillis(2027, 1, 1),
            annual.endsAtEpochMillis,
        )
    }

    @Test
    fun leap_day_has_a_complete_daily_cycle() {
        val leapDay = epochMillis(
            year = 2028,
            month = 2,
            day = 29,
            hour = 12,
        )

        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = leapDay,
        )

        assertEquals(
            epochMillis(2028, 2, 29),
            period.startsAtEpochMillis,
        )
        assertEquals(
            epochMillis(2028, 3, 1),
            period.endsAtEpochMillis,
        )
    }

    @Test
    fun one_result_resolves_to_all_four_versioned_cycles() {
        val instant = epochMillis(
            year = 2026,
            month = 7,
            day = 25,
            hour = 9,
        )

        val periods = resolveRankingCycles(
            completedAtEpochMillis = instant,
            rankingRuleVersion = 3,
        )

        assertEquals(
            RankingCycleKind.values().toList(),
            periods.map { period -> period.kind },
        )
        assertTrue(
            periods.all { period ->
                period.rankingRuleVersion == 3 &&
                        period.timeZoneId ==
                        CANONICAL_RANKING_TIME_ZONE_ID &&
                        period.contains(instant) &&
                        period.cycleId.startsWith("ranking-v3:")
            },
        )
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        millisecond: Int = 0,
    ): Long {
        return GregorianCalendar(
            TimeZone.getTimeZone(
                CANONICAL_RANKING_TIME_ZONE_ID,
            ),
            Locale.ROOT,
        ).apply {
            isLenient = false
            clear()
            set(
                Calendar.YEAR,
                year,
            )
            set(
                Calendar.MONTH,
                month - 1,
            )
            set(
                Calendar.DAY_OF_MONTH,
                day,
            )
            set(
                Calendar.HOUR_OF_DAY,
                hour,
            )
            set(
                Calendar.MINUTE,
                minute,
            )
            set(
                Calendar.SECOND,
                second,
            )
            set(
                Calendar.MILLISECOND,
                millisecond,
            )
        }.timeInMillis
    }
}
