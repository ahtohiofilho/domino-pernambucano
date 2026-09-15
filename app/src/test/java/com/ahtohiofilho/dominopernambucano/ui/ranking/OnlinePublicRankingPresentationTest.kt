package com.ahtohiofilho.dominopernambucano.ui.ranking

import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingFailureKind
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleSummaryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingPublicationStatusDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePublicRankingPresentationTest {
    @Test
    fun stale_current_page_restarts_only_after_the_first_page() {
        assertTrue(
            shouldRestartRankingPagination(
                failure =
                    OnlinePublicRankingFailureKind
                        .PAGINATION_RESTART_REQUIRED,
                requestedOffset = 50,
                selectedScope = PublicRankingScope.CURRENT,
            ),
        )
        assertFalse(
            shouldRestartRankingPagination(
                failure =
                    OnlinePublicRankingFailureKind
                        .PAGINATION_RESTART_REQUIRED,
                requestedOffset = 0,
                selectedScope = PublicRankingScope.CURRENT,
            ),
        )
        assertFalse(
            shouldRestartRankingPagination(
                failure =
                    OnlinePublicRankingFailureKind
                        .PAGINATION_RESTART_REQUIRED,
                requestedOffset = 50,
                selectedScope = PublicRankingScope.CLOSED,
            ),
        )
    }

    @Test
    fun cycle_resource_ids_are_complete_and_unique() {
        val resourceIds = PublicRankingCycleDto.values().map { cycle ->
            cycle.publicLabelRes()
        }

        assertEquals(
            PublicRankingCycleDto.values().size,
            resourceIds.toSet().size,
        )
        assertTrue(
            resourceIds.all { resourceId ->
                resourceId != 0
            },
        )
    }

    @Test
    fun scope_resource_ids_are_complete_and_unique() {
        val resourceIds = PublicRankingScope.values().map { scope ->
            scope.publicLabelRes()
        }

        assertEquals(
            PublicRankingScope.values().size,
            resourceIds.toSet().size,
        )
        assertTrue(
            resourceIds.all { resourceId ->
                resourceId != 0
            },
        )
    }

    @Test
    fun user_visible_failure_resource_ids_are_complete_and_unique() {
        val userVisibleFailures =
            OnlinePublicRankingFailureKind.values().filterNot { failure ->
                failure ==
                    OnlinePublicRankingFailureKind
                        .PAGINATION_RESTART_REQUIRED
            }
        val resourceIds =
            userVisibleFailures.map { failure ->
                failure.publicMessageRes()
            }

        assertEquals(
            userVisibleFailures.size,
            resourceIds.toSet().size,
        )
        assertTrue(
            resourceIds.all { resourceId ->
                resourceId != 0
            },
        )
        assertEquals(
            OnlinePublicRankingFailureKind.PROTOCOL_ERROR.publicMessageRes(),
            OnlinePublicRankingFailureKind
                .PAGINATION_RESTART_REQUIRED
                .publicMessageRes(),
        )
    }

    @Test
    fun monthly_period_uses_the_requested_locale() {
        val summary = cycleSummary(
            cycle = PublicRankingCycleDto.MONTHLY,
            cycleId = "monthly",
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
        )

        val english =
            summary.publicPeriodLabel(Locale.US)
        val spanish =
            summary.publicPeriodLabel(
                Locale("es", "ES"),
            )
        val portuguese =
            summary.publicPeriodLabel(
                Locale("pt", "BR"),
            )

        assertTrue(english.contains("July"))
        assertTrue(spanish.lowercase().contains("julio"))
        assertTrue(portuguese.lowercase().contains("julho"))
        assertTrue(english.endsWith("2026"))
        assertTrue(spanish.endsWith("2026"))
        assertTrue(portuguese.endsWith("2026"))
    }

    @Test
    fun weekly_period_preserves_the_recife_boundary() {
        val label = cycleSummary(
            cycle = PublicRankingCycleDto.WEEKLY,
            cycleId = "weekly",
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
        ).publicPeriodLabel(Locale.US)

        assertTrue(label.contains("2026"))
        assertTrue(label.contains("–"))
        assertTrue(label.contains("26"))
        assertFalse(label.contains("Jul 27"))
    }

    @Test
    fun rank_label_is_familiar_in_english_and_localized_elsewhere() {
        val entry = entry(
            rank = 7,
            competitorId = "competitor-secret",
            displayName = null,
        )

        assertEquals(
            "#7",
            entry.publicRankLabel(Locale.US),
        )
        assertEquals(
            "7\u00AA",
            entry.publicRankLabel(
                Locale("pt", "BR"),
            ),
        )
        assertEquals(
            "7\u00BA",
            entry.publicRankLabel(
                Locale("es", "ES"),
            ),
        )
    }

    @Test
    fun entry_display_name_never_falls_back_to_competitor_id() {
        val entry = entry(
            rank = 7,
            competitorId = "competitor-secret",
            displayName = null,
        )

        assertEquals(
            "Player 7",
            entry.publicDisplayName(
                fallback = "Player 7",
            ),
        )
        assertFalse(
            entry.publicDisplayName(
                fallback = "Player 7",
            ).contains(entry.competitorId),
        )
    }

    @Test
    fun public_name_and_exact_score_fraction_are_preserved() {
        val entry = entry(
            rank = 1,
            competitorId = "competitor-opaque",
            displayName = "  Antônio Filho  ",
        )

        assertEquals(
            "Antônio Filho",
            entry.publicDisplayName(
                fallback = "Player 1",
            ),
        )
        assertEquals(
            "2/3",
            entry.publicScoreText(),
        )
        assertEquals(
            "0.667",
            entry.publicDecimalScoreText(),
        )
    }

    @Test
    fun avatar_monogram_is_derived_locally_from_public_name() {
        val named = entry(
            rank = 1,
            competitorId = "competitor-secret",
            displayName = "  Antonio Filho  ",
        )
        val fallback = entry(
            rank = 7,
            competitorId = "competitor-secret",
            displayName = null,
        )

        assertEquals(
            "ANT",
            named.publicAvatarMonogram(
                fallback = "Player 1",
            ),
        )
        assertEquals(
            "PLA",
            fallback.publicAvatarMonogram(
                fallback = "Player 7",
            ),
        )
        assertFalse(
            fallback.publicAvatarMonogram(
                fallback = "Player 7",
            ).contains("competitor-secret"),
        )
    }
    @Test
    fun below_threshold_response_maps_to_pending_publication() {
        val response = rankingResponse(
            publicationStatus =
                PublicRankingPublicationStatusDto.BELOW_THRESHOLD,
            totalEligiblePlayers = 42,
            publicationThreshold = 100,
            eligiblePlayersRemaining = 58,
            awardsEligible = false,
        )

        assertTrue(response.isOfficialRankingPending())
        assertFalse(response.isOfficialRankingPublished())
    }

    @Test
    fun published_response_maps_to_official_ranking() {
        val response = rankingResponse(
            publicationStatus =
                PublicRankingPublicationStatusDto.PUBLISHED,
            totalEligiblePlayers = 100,
            publicationThreshold = 100,
            eligiblePlayersRemaining = 0,
            awardsEligible = true,
        )

        assertFalse(response.isOfficialRankingPending())
        assertTrue(response.isOfficialRankingPublished())
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

    private fun rankingResponse(
        publicationStatus: PublicRankingPublicationStatusDto,
        totalEligiblePlayers: Int,
        publicationThreshold: Int,
        eligiblePlayersRemaining: Int,
        awardsEligible: Boolean,
    ): PublicRankingResponseDto {
        return PublicRankingResponseDto(
            cycle = PublicRankingCycleDto.DAILY,
            cycleId = "daily-current",
            rankingRevision = "a".repeat(64),
            rankingRuleVersion = 1,
            timeZoneId = "America/Recife",
            startsAtEpochMillis = 1_700_000_000_000L,
            endsAtEpochMillis = 1_700_086_400_000L,
            resultCount = 10,
            totalEligiblePlayers = totalEligiblePlayers,
            publicationThreshold = publicationThreshold,
            publicationStatus = publicationStatus,
            eligiblePlayersRemaining = eligiblePlayersRemaining,
            awardsEligible = awardsEligible,
            offset = 0,
            limit = 100,
            hasMore = false,
            entries = emptyList(),
            viewer = null,
        )
    }

    private fun cycleSummary(
        cycle: PublicRankingCycleDto,
        cycleId: String,
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
            publicationThreshold = 1,
            publicationStatus =
                PublicRankingPublicationStatusDto.PUBLISHED,
            eligiblePlayersRemaining = 0,
            awardsEligible = true,
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
