package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchPlayerResult
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.competitive.createRankedMatchResultId
import com.ahtohiofilho.dominopernambucano.competitive.resolveRankingCycle
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.PublicRankingAwardTierDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCyclesResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingPublicationStatusDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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

class PublicHistoricalRankingHttpRouteTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun closed_cycle_inventory_and_ranking_are_publicly_queryable() =
        testApplication {
            val prepared = preparedClosedDailyRanking()
            val resolver = headerResolver(
                "viewer" to OnlineRequestIdentity(
                    playerId = "player-viewer",
                    principalId = "principal-viewer",
                    sessionId = "session-viewer",
                    kind = OnlinePrincipalKind.ACCOUNT,
                    accountId = "snapshot-account-0-0",
                ),
            )

            application {
                module(
                    store = prepared.store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                    nowEpochMillis = {
                        prepared.period.endsAtEpochMillis
                    },
                )
            }

            val cyclesResponse = client.get(
                "/${OnlineRemoteRoutes.RANKING_CYCLES}" +
                    "?cycle=DAILY&offset=0&limit=10",
            ) {
                header(TEST_IDENTITY_HEADER, "viewer")
            }
            val cycles = json.decodeFromString<
                PublicRankingCyclesResponseDto
            >(
                cyclesResponse.bodyAsText(),
            )

            assertEquals(HttpStatusCode.OK, cyclesResponse.status)
            assertEquals(
                "private, max-age=30, must-revalidate",
                cyclesResponse.headers[HttpHeaders.CacheControl],
            )
            val cyclesEntityTag = requireNotNull(
                cyclesResponse.headers[HttpHeaders.ETag],
            )
            val cachedCycles = client.get(
                "/${OnlineRemoteRoutes.RANKING_CYCLES}" +
                    "?cycle=DAILY&offset=0&limit=10",
            ) {
                header(TEST_IDENTITY_HEADER, "viewer")
                header(HttpHeaders.IfNoneMatch, cyclesEntityTag)
            }
            assertEquals(
                HttpStatusCode.NotModified,
                cachedCycles.status,
            )
            assertEquals(1, cycles.totalClosedCycles)
            assertEquals(1, cycles.cycles.size)
            assertFalse(cycles.hasMore)

            val summary = cycles.cycles.single()
            assertEquals(prepared.period.cycleId, summary.cycleId)
            assertEquals(104, summary.totalEligiblePlayers)
            assertEquals(100, summary.retainedRankingSize)
            assertEquals(100, summary.publicationThreshold)
            assertEquals(
                PublicRankingPublicationStatusDto.PUBLISHED,
                summary.publicationStatus,
            )
            assertEquals(0, summary.eligiblePlayersRemaining)
            assertTrue(summary.awardsEligible)
            assertEquals(1, summary.awardRuleVersion)
            assertEquals(50, summary.awardedRankingSize)
            assertFalse(summary.isLegacyTruncated)
            assertEquals(1, summary.retentionPolicyVersion)
            assertTrue(summary.isRetentionLimited)
            assertEquals(
                prepared.period.endsAtEpochMillis,
                summary.closedAtEpochMillis,
            )

            val rankingResponse = client.get(
                "/${OnlineRemoteRoutes.RANKING}" +
                    "?cycle=DAILY" +
                    "&cycleId=${prepared.period.cycleId}" +
                    "&offset=98&limit=5",
            ) {
                header(TEST_IDENTITY_HEADER, "viewer")
            }
            val body = rankingResponse.bodyAsText()
            val ranking = json.decodeFromString<
                PublicRankingResponseDto
            >(body)

            assertEquals(HttpStatusCode.OK, rankingResponse.status)
            assertEquals(
                "private, max-age=300, must-revalidate",
                rankingResponse.headers[HttpHeaders.CacheControl],
            )
            val rankingEntityTag = requireNotNull(
                rankingResponse.headers[HttpHeaders.ETag],
            )
            val cachedRanking = client.get(
                "/${OnlineRemoteRoutes.RANKING}" +
                    "?cycle=DAILY" +
                    "&cycleId=${prepared.period.cycleId}" +
                    "&offset=98&limit=5",
            ) {
                header(TEST_IDENTITY_HEADER, "viewer")
                header(HttpHeaders.IfNoneMatch, rankingEntityTag)
            }
            assertEquals(
                HttpStatusCode.NotModified,
                cachedRanking.status,
            )
            assertTrue(ranking.isClosed)
            assertEquals(
                prepared.period.endsAtEpochMillis,
                ranking.closedAtEpochMillis,
            )
            assertEquals(104, ranking.totalEligiblePlayers)
            assertEquals(100, ranking.retainedRankingSize)
            assertEquals(100, ranking.publicationThreshold)
            assertEquals(
                PublicRankingPublicationStatusDto.PUBLISHED,
                ranking.publicationStatus,
            )
            assertEquals(0, ranking.eligiblePlayersRemaining)
            assertTrue(ranking.awardsEligible)
            assertEquals(1, ranking.awardRuleVersion)
            assertEquals(50, ranking.awardedRankingSize)
            assertFalse(ranking.isLegacyTruncated)
            assertEquals(1, ranking.retentionPolicyVersion)
            assertTrue(ranking.isRetentionLimited)
            assertEquals(2, ranking.entries.size)
            assertTrue(
                ranking.entries.all { entry ->
                    entry.awardTier == null
                },
            )
            assertFalse(ranking.hasMore)
            assertNotNull(ranking.viewer)
            assertEquals(1, requireNotNull(ranking.viewer).rank)
            assertEquals(
                PublicRankingAwardTierDto.DIAMOND,
                requireNotNull(ranking.viewer).awardTier,
            )
            assertFalse(body.contains("snapshot-account"))
            assertFalse(body.contains("accountId"))
            assertFalse(body.contains("playerId"))
        }

    @Test
    fun closed_cycle_below_threshold_is_not_published_or_awarded() =
        testApplication {
            val prepared = preparedClosedDailyRanking(
                matchCount = 1,
            )
            val resolver = headerResolver(
                "viewer" to OnlineRequestIdentity(
                    playerId = "player-viewer",
                    principalId = "principal-viewer",
                    sessionId = "session-viewer",
                    kind = OnlinePrincipalKind.ACCOUNT,
                    accountId = "snapshot-account-0-0",
                ),
            )

            application {
                module(
                    store = prepared.store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                    nowEpochMillis = {
                        prepared.period.endsAtEpochMillis
                    },
                )
            }

            val cyclesResponse = client.get(
                "/${OnlineRemoteRoutes.RANKING_CYCLES}" +
                    "?cycle=DAILY&offset=0&limit=10",
            ) {
                header(TEST_IDENTITY_HEADER, "viewer")
            }
            val cycles = json.decodeFromString<
                PublicRankingCyclesResponseDto
            >(cyclesResponse.bodyAsText())
            val summary = cycles.cycles.single()

            assertEquals(4, summary.totalEligiblePlayers)
            assertEquals(100, summary.publicationThreshold)
            assertEquals(
                PublicRankingPublicationStatusDto.BELOW_THRESHOLD,
                summary.publicationStatus,
            )
            assertEquals(96, summary.eligiblePlayersRemaining)
            assertFalse(summary.awardsEligible)
            assertEquals(1, summary.awardRuleVersion)
            assertEquals(0, summary.awardedRankingSize)

            val rankingResponse = client.get(
                "/${OnlineRemoteRoutes.RANKING}" +
                    "?cycle=DAILY" +
                    "&cycleId=${prepared.period.cycleId}" +
                    "&offset=0&limit=10",
            ) {
                header(TEST_IDENTITY_HEADER, "viewer")
            }
            val ranking = json.decodeFromString<
                PublicRankingResponseDto
            >(rankingResponse.bodyAsText())

            assertEquals(HttpStatusCode.OK, rankingResponse.status)
            assertEquals(4, ranking.totalEligiblePlayers)
            assertEquals(100, ranking.publicationThreshold)
            assertEquals(
                PublicRankingPublicationStatusDto.BELOW_THRESHOLD,
                ranking.publicationStatus,
            )
            assertEquals(96, ranking.eligiblePlayersRemaining)
            assertFalse(ranking.awardsEligible)
            assertEquals(1, ranking.awardRuleVersion)
            assertEquals(0, ranking.awardedRankingSize)
            assertTrue(ranking.entries.isEmpty())
            assertFalse(ranking.hasMore)
            assertNull(ranking.viewer)
        }

    @Test
    fun invalid_or_missing_historical_cycle_is_rejected() =
        testApplication {
            val prepared = preparedClosedDailyRanking()
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
                    store = prepared.store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                    nowEpochMillis = {
                        prepared.period.endsAtEpochMillis
                    },
                )
            }

            suspend fun status(url: String): HttpStatusCode {
                return client.get(url) {
                    header(TEST_IDENTITY_HEADER, "visitor")
                }.status
            }

            assertEquals(
                HttpStatusCode.BadRequest,
                status(
                    "/${OnlineRemoteRoutes.RANKING}" +
                        "?cycle=DAILY&cycleId=",
                ),
            )
            assertEquals(
                HttpStatusCode.NotFound,
                status(
                    "/${OnlineRemoteRoutes.RANKING}" +
                        "?cycle=DAILY&cycleId=ranking-v1:daily:1900-01-01",
                ),
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                status(
                    "/${OnlineRemoteRoutes.RANKING}" +
                        "?cycle=WEEKLY" +
                        "&cycleId=${prepared.period.cycleId}",
                ),
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                status(
                    "/${OnlineRemoteRoutes.RANKING_CYCLES}" +
                        "?cycle=DAILY&limit=101",
                ),
            )
        }

    private fun preparedClosedDailyRanking(
        matchCount: Int = 26,
    ): PreparedRanking {
        val day = epochMillis(
            year = 2026,
            month = 7,
            day = 25,
            hour = 12,
        )
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = day,
        )
        var now = day
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )

        store.restorePersistentState(
            OnlineServerStoreState(
                rankedResults = (0 until matchCount).map { matchIndex ->
                    result(
                        matchIndex = matchIndex,
                        completedAtEpochMillis = day + matchIndex,
                    )
                },
            ),
        )

        now = period.endsAtEpochMillis
        assertTrue(store.advanceAuthoritativeTime())

        return PreparedRanking(
            store = store,
            period = period,
        )
    }

    private fun result(
        matchIndex: Int,
        completedAtEpochMillis: Long,
    ): RankedMatchResult {
        val matchId = "historical-route-match-$matchIndex"

        return RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = CURRENT_RANKING_RULE_VERSION,
            completedAtEpochMillis = completedAtEpochMillis,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(1, 1),
            completedRounds = 1,
            players = (0 until 4).map { seatIndex ->
                val won = seatIndex % 2 == 0

                RankedMatchPlayerResult(
                    playerId =
                        "historical-player-$matchIndex-$seatIndex",
                    accountId =
                        "snapshot-account-$matchIndex-$seatIndex",
                    seatIndex = seatIndex,
                    teamIndex = seatIndex % 2,
                    won = won,
                    victoriesDelta = if (won) 1 else 0,
                    gamesDelta = 1,
                    teamBalanceDelta = if (won) 6 else -3,
                    individualPointsScored =
                        if (won) 4 else 1,
                    touchesGiven = if (won) 2 else 0,
                    automaticRounds = 0,
                )
            },
        )
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
    ): Long {
        return GregorianCalendar(
            TimeZone.getTimeZone("America/Recife"),
            Locale.ROOT,
        ).apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis
    }

    private fun headerResolver(
        vararg entries: Pair<String, OnlineRequestIdentity>,
    ): OnlineRequestIdentityResolver {
        val identities = mapOf(*entries)

        return OnlineRequestIdentityResolver { call: ApplicationCall ->
            call.request.headers[TEST_IDENTITY_HEADER]
                ?.let(identities::get)
        }
    }

    private data class PreparedRanking(
        val store: InMemoryOnlineServerStore,
        val period:
            com.ahtohiofilho.dominopernambucano.competitive.RankedCyclePeriod,
    )

    companion object {
        private const val TEST_IDENTITY_HEADER =
            "X-Test-Online-Identity"
    }
}
