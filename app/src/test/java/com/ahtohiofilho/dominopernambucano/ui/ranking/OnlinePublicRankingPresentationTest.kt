package com.ahtohiofilho.dominopernambucano.ui.ranking

import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingFailureKind
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleSummaryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePublicRankingPresentationTest {
    @Test
    fun cycle_labels_are_direct_and_complete() {
        assertEquals(
            listOf(
                "Diário",
                "Semanal",
                "Mensal",
                "Anual",
            ),
            PublicRankingCycleDto.values().map { cycle ->
                cycle.publicLabel()
            },
        )
    }

    @Test
    fun ranking_scope_labels_distinguish_current_and_closed() {
        assertEquals(
            listOf(
                "Atual",
                "Encerrados",
            ),
            PublicRankingScope.values().map { scope ->
                scope.publicLabel()
            },
        )
    }

    @Test
    fun closed_period_labels_follow_recife_calendar() {
        assertEquals(
            "27/07/2026",
            cycleSummary(
                cycle = PublicRankingCycleDto.DAILY,
                startsAtEpochMillis = recifeMillis(
                    year = 2026,
                    month = Calendar.JULY,
                    day = 27,
                ),
                endsAtEpochMillis = recifeMillis(
                    year = 2026,
                    month = Calendar.JULY,
                    day = 28,
                ),
            ).publicPeriodLabel(),
        )
        assertEquals(
            "20/07 a 26/07/2026",
            cycleSummary(
                cycle = PublicRankingCycleDto.WEEKLY,
                startsAtEpochMillis = recifeMillis(
                    year = 2026,
                    month = Calendar.JULY,
                    day = 20,
                ),
                endsAtEpochMillis = recifeMillis(
                    year = 2026,
                    month = Calendar.JULY,
                    day = 27,
                ),
            ).publicPeriodLabel(),
        )
        assertEquals(
            "Julho de 2026",
            cycleSummary(
                cycle = PublicRankingCycleDto.MONTHLY,
                startsAtEpochMillis = recifeMillis(
                    year = 2026,
                    month = Calendar.JULY,
                    day = 1,
                ),
                endsAtEpochMillis = recifeMillis(
                    year = 2026,
                    month = Calendar.AUGUST,
                    day = 1,
                ),
            ).publicPeriodLabel(),
        )
        assertEquals(
            "2026",
            cycleSummary(
                cycle = PublicRankingCycleDto.ANNUAL,
                startsAtEpochMillis = recifeMillis(
                    year = 2026,
                    month = Calendar.JANUARY,
                    day = 1,
                ),
                endsAtEpochMillis = recifeMillis(
                    year = 2027,
                    month = Calendar.JANUARY,
                    day = 1,
                ),
            ).publicPeriodLabel(),
        )
    }

    @Test
    fun current_and_historical_contexts_are_explicit() {
        val current = response(
            isClosed = false,
            closedAtEpochMillis = null,
        )
        val historical = response(
            isClosed = true,
            closedAtEpochMillis = recifeMillis(
                year = 2026,
                month = Calendar.JULY,
                day = 28,
            ),
        )

        assertEquals(
            "Ranking atual · 27/07/2026",
            current.publicContextLabel(),
        )
        assertEquals(
            "Ranking encerrado · 27/07/2026",
            historical.publicContextLabel(),
        )
    }

    @Test
    fun historical_retention_is_explained_without_inferring_viewer_status() {
        val historical = response(
            isClosed = true,
            closedAtEpochMillis = recifeMillis(
                year = 2026,
                month = Calendar.JULY,
                day = 28,
            ),
            totalEligiblePlayers = 400,
            retainedRankingSize = 100,
        )

        assertEquals(
            "400 jogadores elegíveis · 120 partidas · Top 100 preservado",
            historical.publicSummaryLabel(),
        )
        assertEquals(
            "Este histórico preserva somente o Top 100 do período.",
            historical.publicRetentionNotice(),
        )
    }

    @Test
    fun retention_notice_is_omitted_when_full_ranking_is_preserved() {
        val historical = response(
            isClosed = true,
            closedAtEpochMillis = recifeMillis(
                year = 2026,
                month = Calendar.JULY,
                day = 28,
            ),
            totalEligiblePlayers = 40,
            retainedRankingSize = 40,
        )

        assertEquals(
            "40 jogadores elegíveis · 120 partidas",
            historical.publicSummaryLabel(),
        )
        assertNull(
            historical.publicRetentionNotice(),
        )
    }

    @Test
    fun closed_cycle_pages_are_merged_deduplicated_and_sorted() {
        val older = cycleSummary(
            cycle = PublicRankingCycleDto.DAILY,
            cycleId = "daily-older",
            startsAtEpochMillis = 100L,
            endsAtEpochMillis = 200L,
        )
        val newer = cycleSummary(
            cycle = PublicRankingCycleDto.DAILY,
            cycleId = "daily-newer",
            startsAtEpochMillis = 200L,
            endsAtEpochMillis = 300L,
        )
        val duplicateNewer = newer.copy(
            resultCount = 999,
        )

        val merged = mergeClosedRankingCycles(
            current = listOf(older, newer),
            incoming = listOf(duplicateNewer),
        )

        assertEquals(
            listOf(
                "daily-newer",
                "daily-older",
            ),
            merged.map { summary ->
                summary.cycleId
            },
        )
        assertEquals(
            2,
            merged.size,
        )
    }

    @Test
    fun entry_title_never_renders_technical_competitor_id() {
        val entry = entry(
            rank = 7,
            competitorId = "competitor-secret",
            displayName = null,
        )

        assertEquals(
            "7º · Jogador 7",
            entry.publicTitle(),
        )
        assertFalse(
            entry.publicTitle().contains(
                entry.competitorId,
            ),
        )
    }

    @Test
    fun public_name_and_exact_score_fraction_are_preserved() {
        val entry = entry(
            rank = 1,
            competitorId = "competitor-opaque",
            displayName = "Antônio Filho",
        )

        assertEquals(
            "1º · Antônio Filho",
            entry.publicTitle(),
        )
        assertEquals(
            "2/3",
            entry.publicScoreText(),
        )
    }

    @Test
    fun failure_messages_do_not_promise_ranked_participation() {
        val messages = OnlinePublicRankingFailureKind.values().map {
                failure ->
            failure.publicMessage()
        }

        assertTrue(
            messages.all { message ->
                message.isNotBlank()
            },
        )
        assertFalse(
            messages.any { message ->
                message.contains(
                    "partida",
                    ignoreCase = true,
                )
            },
        )
    }

    private fun response(
        isClosed: Boolean,
        closedAtEpochMillis: Long?,
        totalEligiblePlayers: Int = 40,
        retainedRankingSize: Int = totalEligiblePlayers,
    ): PublicRankingResponseDto {
        return PublicRankingResponseDto(
            cycle = PublicRankingCycleDto.DAILY,
            cycleId = "ranking-v1:daily:2026-07-27",
            rankingRuleVersion = 1,
            timeZoneId = "America/Recife",
            startsAtEpochMillis = recifeMillis(
                year = 2026,
                month = Calendar.JULY,
                day = 27,
            ),
            endsAtEpochMillis = recifeMillis(
                year = 2026,
                month = Calendar.JULY,
                day = 28,
            ),
            resultCount = 120,
            totalEligiblePlayers = totalEligiblePlayers,
            offset = 0,
            limit = 50,
            hasMore = false,
            entries = emptyList(),
            retainedRankingSize = retainedRankingSize,
            isClosed = isClosed,
            closedAtEpochMillis = closedAtEpochMillis,
        )
    }

    private fun cycleSummary(
        cycle: PublicRankingCycleDto,
        cycleId: String = "cycle-id",
        startsAtEpochMillis: Long,
        endsAtEpochMillis: Long,
    ): PublicRankingCycleSummaryDto {
        return PublicRankingCycleSummaryDto(
            cycle = cycle,
            cycleId = cycleId,
            rankingRuleVersion = 1,
            timeZoneId = "America/Recife",
            startsAtEpochMillis = startsAtEpochMillis,
            endsAtEpochMillis = endsAtEpochMillis,
            closedAtEpochMillis = endsAtEpochMillis,
            resultCount = 10,
            totalEligiblePlayers = 8,
            retainedRankingSize = 8,
        )
    }

    private fun entry(
        rank: Int,
        competitorId: String,
        displayName: String?,
    ): PublicRankingEntryDto {
        return PublicRankingEntryDto(
            rank = rank,
            competitorId = competitorId,
            displayName = displayName,
            victories = 1,
            games = 1,
            scoreNumerator = 2,
            scoreDenominator = 3,
            teamBalance = 6,
            individualPoints = 4,
            touchesGiven = 2,
            automaticRounds = 0,
        )
    }

    private fun recifeMillis(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        return Calendar.getInstance(
            TimeZone.getTimeZone("America/Recife"),
        ).apply {
            clear()
            set(
                year,
                month,
                day,
                0,
                0,
                0,
            )
        }.timeInMillis
    }
}
