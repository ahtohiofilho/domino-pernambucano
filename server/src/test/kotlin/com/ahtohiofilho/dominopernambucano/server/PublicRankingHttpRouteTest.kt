package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchPlayerResult
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.createRankedMatchResultId
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingPublicationStatusDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankingHttpRouteTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun ranking_requires_identity_but_allows_anonymous_viewing() = testApplication {
        val day = rankingInstant()
        val resolver = headerResolver(
            "visitor" to OnlineRequestIdentity(
                playerId = "visitor-player",
                principalId = "visitor-principal",
                sessionId = "visitor-session",
                kind = OnlinePrincipalKind.ANONYMOUS,
                accountId = null,
            ),
        )

        application {
            module(
                store = storeWithRanking(day),
                serverEnvironment = OnlineServerEnvironment.TEST,
                identityResolver = resolver,
                nowEpochMillis = { day },
            )
        }

        val missing = client.get(rankingUrl())
        val visitor = client.get(rankingUrl()) {
            header(TEST_IDENTITY_HEADER, "visitor")
        }

        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals(HttpStatusCode.OK, visitor.status)

        val response = json.decodeFromString<PublicRankingResponseDto>(
            visitor.bodyAsText(),
        )
        assertEquals(PublicRankingCycleDto.DAILY, response.cycle)
        assertEquals(4, response.totalEligiblePlayers)
        assertEquals(100, response.publicationThreshold)
        assertEquals(
            PublicRankingPublicationStatusDto.BELOW_THRESHOLD,
            response.publicationStatus,
        )
        assertEquals(96, response.eligiblePlayersRemaining)
        assertFalse(response.awardsEligible)
        assertEquals(1, response.awardRuleVersion)
        assertEquals(0, response.awardedRankingSize)
        assertTrue(response.entries.isEmpty())
        assertFalse(response.hasMore)
        assertNull(response.viewer)
    }

    @Test
    fun account_view_includes_own_position_without_authority_ids() = testApplication {
        val day = rankingInstant()
        val resolver = headerResolver(
            "account-a" to OnlineRequestIdentity(
                playerId = "player-a",
                principalId = "principal-a",
                sessionId = "session-a",
                kind = OnlinePrincipalKind.ACCOUNT,
                accountId = "account-0000-0",
            ),
        )

        application {
            module(
                store = storeWithRanking(
                    completedAtEpochMillis = day,
                    matchCount = 25,
                ),
                serverEnvironment = OnlineServerEnvironment.TEST,
                identityResolver = resolver,
                nowEpochMillis = { day },
            )
        }

        val httpResponse = client.get(rankingUrl(offset = 2, limit = 2)) {
            header(TEST_IDENTITY_HEADER, "account-a")
        }
        val body = httpResponse.bodyAsText()
        val ranking = json.decodeFromString<PublicRankingResponseDto>(body)

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertNotNull(ranking.viewer)
        val viewer = requireNotNull(ranking.viewer)
        assertEquals(1, viewer.rank)
        assertTrue(viewer.competitorId.startsWith("competitor-"))
        assertFalse(viewer.competitorId.contains("account-a"))
        assertEquals(100, ranking.totalEligiblePlayers)
        assertEquals(100, ranking.publicationThreshold)
        assertEquals(
            PublicRankingPublicationStatusDto.PUBLISHED,
            ranking.publicationStatus,
        )
        assertEquals(0, ranking.eligiblePlayersRemaining)
        assertTrue(ranking.awardsEligible)
        assertEquals(1, ranking.awardRuleVersion)
        assertEquals(0, ranking.awardedRankingSize)
        assertNull(viewer.awardTier)
        assertTrue(
            ranking.entries.all { entry ->
                entry.awardTier == null
            },
        )
        assertEquals(2, ranking.entries.size)
        assertTrue(ranking.hasMore)
        assertFalse(body.contains("account-a"))
        assertFalse(body.contains("accountId"))
        assertFalse(body.contains("playerId"))
    }

    @Test
    fun current_ranking_pages_are_bound_to_one_revision() =
        testApplication {
            val day = rankingInstant()
            val resolver = headerResolver(
                "visitor" to OnlineRequestIdentity(
                    playerId = "visitor-player",
                    principalId = "visitor-principal",
                    sessionId = "visitor-session",
                    kind = OnlinePrincipalKind.ANONYMOUS,
                    accountId = null,
                ),
            )

            application {
                module(
                    store = storeWithRanking(
                        completedAtEpochMillis = day,
                        matchCount = 25,
                    ),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                    nowEpochMillis = { day },
                )
            }

            val first = client.get(rankingUrl()) {
                header(TEST_IDENTITY_HEADER, "visitor")
            }
            val firstPage = json.decodeFromString<PublicRankingResponseDto>(
                first.bodyAsText(),
            )
            val sameRevision = client.get(
                rankingUrl(offset = 2, limit = 2) +
                    "&revision=${firstPage.rankingRevision}",
            ) {
                header(TEST_IDENTITY_HEADER, "visitor")
            }
            val staleRevision = client.get(
                rankingUrl(offset = 2, limit = 2) +
                    "&revision=${"0".repeat(64)}",
            ) {
                header(TEST_IDENTITY_HEADER, "visitor")
            }

            assertEquals(HttpStatusCode.OK, first.status)
            assertTrue(
                firstPage.rankingRevision.matches(
                    Regex("[0-9a-f]{64}"),
                ),
            )
            assertEquals(HttpStatusCode.OK, sameRevision.status)
            assertEquals(HttpStatusCode.Conflict, staleRevision.status)
        }

    @Test
    fun homologation_publishes_four_eligible_players() = testApplication {
        val day = rankingInstant()
        val resolver = headerResolver(
            "visitor" to OnlineRequestIdentity(
                playerId = "visitor-player",
                principalId = "visitor-principal",
                sessionId = "visitor-session",
                kind = OnlinePrincipalKind.ANONYMOUS,
                accountId = null,
            ),
        )

        application {
            module(
                store = storeWithRanking(day),
                serverEnvironment = OnlineServerEnvironment.HOMOLOGATION,
                identityResolver = resolver,
                nowEpochMillis = { day },
            )
        }

        val httpResponse = client.get(rankingUrl()) {
            header(TEST_IDENTITY_HEADER, "visitor")
        }
        val ranking = json.decodeFromString<PublicRankingResponseDto>(
            httpResponse.bodyAsText(),
        )

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertEquals(4, ranking.totalEligiblePlayers)
        assertEquals(4, ranking.publicationThreshold)
        assertEquals(
            PublicRankingPublicationStatusDto.PUBLISHED,
            ranking.publicationStatus,
        )
        assertEquals(0, ranking.eligiblePlayersRemaining)
        assertTrue(ranking.awardsEligible)
        assertEquals(1, ranking.awardRuleVersion)
        assertEquals(0, ranking.awardedRankingSize)
        assertTrue(
            ranking.entries.all { entry ->
                entry.awardTier == null
            },
        )
        assertEquals(2, ranking.entries.size)
        assertTrue(ranking.hasMore)
        assertNull(ranking.viewer)
    }

    @Test
    fun invalid_cycle_and_pagination_are_rejected() = testApplication {
        val day = rankingInstant()
        val resolver = headerResolver(
            "visitor" to OnlineRequestIdentity(
                playerId = "visitor-player",
                principalId = "visitor-principal",
                sessionId = "visitor-session",
                kind = OnlinePrincipalKind.ANONYMOUS,
                accountId = null,
            ),
        )

        application {
            module(
                store = storeWithRanking(day),
                serverEnvironment = OnlineServerEnvironment.TEST,
                identityResolver = resolver,
                nowEpochMillis = { day },
            )
        }

        suspend fun status(url: String): HttpStatusCode {
            return client.get(url) {
                header(TEST_IDENTITY_HEADER, "visitor")
            }.status
        }

        assertEquals(
            HttpStatusCode.BadRequest,
            status("/${OnlineRemoteRoutes.RANKING}"),
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            status("/${OnlineRemoteRoutes.RANKING}?cycle=INVALID"),
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            status("/${OnlineRemoteRoutes.RANKING}?cycle=DAILY&offset=abc"),
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            status("/${OnlineRemoteRoutes.RANKING}?cycle=DAILY&offset=-1"),
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            status("/${OnlineRemoteRoutes.RANKING}?cycle=DAILY&limit=101"),
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            status("/${OnlineRemoteRoutes.RANKING}?cycle=DAILY&revision=invalid"),
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            status(
                "/${OnlineRemoteRoutes.RANKING}" +
                    "?cycle=DAILY&cycleId=closed&revision=${"0".repeat(64)}",
            ),
        )
    }

    private fun rankingUrl(offset: Int = 0, limit: Int = 2): String {
        return "/${OnlineRemoteRoutes.RANKING}" +
            "?cycle=DAILY&offset=$offset&limit=$limit"
    }

    private fun storeWithRanking(
        completedAtEpochMillis: Long,
        matchCount: Int = 1,
    ): InMemoryOnlineServerStore {
        return InMemoryOnlineServerStore().also { store ->
            store.restorePersistentState(
                OnlineServerStoreState(
                    rankedResults = (0 until matchCount).map {
                        matchIndex ->
                        result(
                            matchIndex = matchIndex,
                            completedAtEpochMillis =
                                completedAtEpochMillis +
                                    matchIndex,
                        )
                    },
                ),
            )
        }
    }

    private fun result(
        matchIndex: Int,
        completedAtEpochMillis: Long,
    ): RankedMatchResult {
        val matchId = "ranked-$matchIndex"
        val accountPrefix =
            matchIndex.toString().padStart(4, '0')

        return RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = CURRENT_RANKING_RULE_VERSION,
            completedAtEpochMillis = completedAtEpochMillis,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(1, 1),
            completedRounds = 1,
            players = listOf(
                player(
                    0,
                    "account-$accountPrefix-0",
                    true,
                    6,
                    4,
                    2,
                    0,
                ),
                player(
                    1,
                    "account-$accountPrefix-1",
                    false,
                    -3,
                    3,
                    1,
                    0,
                ),
                player(
                    2,
                    "account-$accountPrefix-2",
                    true,
                    6,
                    1,
                    0,
                    0,
                ),
                player(
                    3,
                    "account-$accountPrefix-3",
                    false,
                    -3,
                    0,
                    1,
                    1,
                ),
            ),
        )
    }

    private fun player(
        seatIndex: Int,
        accountId: String,
        won: Boolean,
        teamBalanceDelta: Int,
        individualPointsScored: Int,
        touchesGiven: Int,
        automaticRounds: Int,
    ): RankedMatchPlayerResult {
        return RankedMatchPlayerResult(
            playerId = "player-$seatIndex",
            accountId = accountId,
            seatIndex = seatIndex,
            teamIndex = seatIndex % 2,
            won = won,
            victoriesDelta = if (won) 1 else 0,
            gamesDelta = 1,
            teamBalanceDelta = teamBalanceDelta,
            individualPointsScored = individualPointsScored,
            touchesGiven = touchesGiven,
            automaticRounds = automaticRounds,
        )
    }

    private fun rankingInstant(): Long {
        return GregorianCalendar(
            TimeZone.getTimeZone("America/Recife"),
            Locale.ROOT,
        ).apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JULY)
            set(Calendar.DAY_OF_MONTH, 25)
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis
    }

    private fun headerResolver(
        vararg entries: Pair<String, OnlineRequestIdentity>,
    ): OnlineRequestIdentityResolver {
        val identities = mapOf(*entries)
        return OnlineRequestIdentityResolver { call: ApplicationCall ->
            call.request.headers[TEST_IDENTITY_HEADER]?.let(identities::get)
        }
    }

    companion object {
        private const val TEST_IDENTITY_HEADER = "X-Test-Online-Identity"
    }
}
