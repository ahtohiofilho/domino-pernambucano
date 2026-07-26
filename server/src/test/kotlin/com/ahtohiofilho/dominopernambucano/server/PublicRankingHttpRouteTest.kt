package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchPlayerResult
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.createRankedMatchResultId
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
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
        assertEquals(2, response.entries.size)
        assertTrue(response.hasMore)
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
                accountId = "account-a",
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
        assertEquals(2, ranking.entries.size)
        assertFalse(ranking.hasMore)
        assertFalse(body.contains("account-a"))
        assertFalse(body.contains("accountId"))
        assertFalse(body.contains("playerId"))
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
    }

    private fun rankingUrl(offset: Int = 0, limit: Int = 2): String {
        return "/${OnlineRemoteRoutes.RANKING}" +
            "?cycle=DAILY&offset=$offset&limit=$limit"
    }

    private fun storeWithRanking(completedAtEpochMillis: Long): InMemoryOnlineServerStore {
        return InMemoryOnlineServerStore().also { store ->
            store.restorePersistentState(
                OnlineServerStoreState(
                    rankedResults = listOf(
                        result("ranked-1", completedAtEpochMillis),
                    ),
                ),
            )
        }
    }

    private fun result(matchId: String, completedAtEpochMillis: Long): RankedMatchResult {
        return RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = CURRENT_RANKING_RULE_VERSION,
            completedAtEpochMillis = completedAtEpochMillis,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(1, 1),
            completedRounds = 1,
            players = listOf(
                player(0, "account-a", true, 6, 4, 2, 0),
                player(1, "account-b", false, -3, 3, 1, 0),
                player(2, "account-c", true, 6, 1, 0, 0),
                player(3, "account-d", false, -3, 0, 1, 1),
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
