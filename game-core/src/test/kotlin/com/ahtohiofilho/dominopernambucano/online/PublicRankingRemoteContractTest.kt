package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankingRemoteContractTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun route_and_cycle_contract_are_stable() {
        assertEquals("ranking", OnlineRemoteRoutes.RANKING)
        assertEquals(
            listOf(
                PublicRankingCycleDto.DAILY,
                PublicRankingCycleDto.WEEKLY,
                PublicRankingCycleDto.MONTHLY,
                PublicRankingCycleDto.ANNUAL,
            ),
            PublicRankingCycleDto.values().toList(),
        )
        assertEquals(
            listOf(
                PublicRankingPublicationStatusDto.BELOW_THRESHOLD,
                PublicRankingPublicationStatusDto.PUBLISHED,
            ),
            PublicRankingPublicationStatusDto.values().toList(),
        )
    }

    @Test
    fun response_round_trip_uses_public_fields_only() {
        val response = PublicRankingResponseDto(
            cycle = PublicRankingCycleDto.DAILY,
            cycleId = "ranking-v1:daily:2026-07-25",
            rankingRuleVersion = 1,
            timeZoneId = "America/Recife",
            startsAtEpochMillis = 100L,
            endsAtEpochMillis = 200L,
            resultCount = 1,
            totalEligiblePlayers = 1,
            publicationThreshold = 100,
            publicationStatus =
                PublicRankingPublicationStatusDto.BELOW_THRESHOLD,
            eligiblePlayersRemaining = 99,
            awardsEligible = false,
            offset = 0,
            limit = 50,
            hasMore = false,
            entries = listOf(
                PublicRankingEntryDto(
                    rank = 1,
                    competitorId = "competitor-abc",
                    victories = 1,
                    games = 1,
                    scoreNumerator = 2,
                    scoreDenominator = 3,
                    teamBalance = 6,
                    individualPoints = 4,
                    touchesGiven = 2,
                    automaticRounds = 0,
                ),
            ),
        )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<PublicRankingResponseDto>(encoded)

        assertEquals(response, decoded)
        assertFalse(encoded.contains("accountId"))
        assertFalse(encoded.contains("playerId"))
        assertTrue(encoded.contains("publicationThreshold"))
        assertTrue(encoded.contains("eligiblePlayersRemaining"))
        assertEquals(
            PublicRankingPublicationStatusDto.BELOW_THRESHOLD,
            decoded.publicationStatus,
        )
        assertEquals(99, decoded.eligiblePlayersRemaining)
        assertFalse(decoded.awardsEligible)
        assertNull(decoded.entries.single().displayName)
    }
}
