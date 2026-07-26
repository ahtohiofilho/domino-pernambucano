package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchPlayerResult
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.createRankedMatchResultId
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
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
import org.junit.Assert.assertNull
import org.junit.Test

class PublicRankingProfileProjectionHttpTest {
    private val json = Json {
        ignoreUnknownKeys = false
    }

    @Test
    fun ranking_projects_only_persisted_public_names() =
        testApplication {
            val day = rankingInstant()
            val store = InMemoryOnlineServerStore()
            store.restorePersistentState(
                OnlineServerStoreState(
                    accounts = listOf(
                        account(
                            accountId = "account-a",
                            playerId = "player-a",
                            displayName = "Antônio Filho",
                            tableName = "AFI",
                        ),
                        account(
                            accountId = "account-b",
                            playerId = "player-b",
                            displayName = null,
                            tableName = null,
                        ),
                        account(
                            accountId = "account-c",
                            playerId = "player-c",
                            displayName = "Carlos Silva",
                            tableName = "CS",
                        ),
                        account(
                            accountId = "account-d",
                            playerId = "player-d",
                            displayName = null,
                            tableName = null,
                        ),
                    ),
                    rankedResults = listOf(
                        result(
                            matchId = "ranked-1",
                            completedAtEpochMillis = day,
                        ),
                    ),
                ),
            )
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
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                    nowEpochMillis = { day },
                )
            }

            val httpResponse = client.get(
                "/${OnlineRemoteRoutes.RANKING}" +
                    "?cycle=DAILY&offset=0&limit=4",
            ) {
                header(TEST_IDENTITY_HEADER, "visitor")
            }
            val body = httpResponse.bodyAsText()
            val ranking =
                json.decodeFromString<PublicRankingResponseDto>(body)

            assertEquals(HttpStatusCode.OK, httpResponse.status)
            assertEquals(
                "Antônio Filho",
                ranking.entries
                    .first { entry -> entry.rank == 1 }
                    .displayName,
            )
            assertEquals(
                "Carlos Silva",
                ranking.entries
                    .first { entry -> entry.rank == 2 }
                    .displayName,
            )
            assertNull(
                ranking.entries
                    .first { entry -> entry.rank == 3 }
                    .displayName,
            )
            assertNull(
                ranking.entries
                    .first { entry -> entry.rank == 4 }
                    .displayName,
            )
            assertFalse(body.contains("account-a"))
            assertFalse(body.contains("player-a"))
            assertFalse(body.contains("accountId"))
            assertFalse(body.contains("playerId"))
        }

    private fun account(
        accountId: String,
        playerId: String,
        displayName: String?,
        tableName: String?,
    ): OnlineServerAccount {
        return OnlineServerAccount(
            accountId = accountId,
            playerId = playerId,
            createdAtEpochMillis = 1_000L,
            publicDisplayName = displayName,
            tableName = tableName,
            profileUpdatedAtEpochMillis =
                if (displayName == null) null else 2_000L,
        )
    }

    private fun result(
        matchId: String,
        completedAtEpochMillis: Long,
    ): RankedMatchResult {
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
            call.request.headers[TEST_IDENTITY_HEADER]
                ?.let(identities::get)
        }
    }

    companion object {
        private const val TEST_IDENTITY_HEADER =
            "X-Test-Online-Identity"
    }
}
