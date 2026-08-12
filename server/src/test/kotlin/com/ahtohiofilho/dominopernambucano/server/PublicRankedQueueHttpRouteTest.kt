package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueEnterRequestDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpStatus
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankedQueueHttpRouteTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun queue_http_boundary_requires_an_authenticated_account() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-player-1" },
            )
            val account = requireNotNull(
                store.promoteAccount(
                    playerId = "player-1",
                ),
            )
            val resolver = TestHeaderIdentityResolver(
                identitiesByKey = mapOf(
                    "account-player-1" to account.toRequestIdentity(),
                    "anonymous-player-1" to OnlineRequestIdentity(
                        playerId = "player-1",
                        kind = OnlinePrincipalKind.ANONYMOUS,
                        accountId = null,
                    ),
                ),
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                )
            }

            val missingIdentity = enqueue(
                identityKey = null,
                body = json.encodeToString(
                    PublicRankedQueueEnterRequestDto(
                        playerName = "Jogador 1",
                    ),
                ),
            )
            val anonymousIdentity = enqueue(
                identityKey = "anonymous-player-1",
                body = json.encodeToString(
                    PublicRankedQueueEnterRequestDto(
                        playerName = "Jogador 1",
                    ),
                ),
            )

            assertEquals(
                HttpStatusCode.Unauthorized,
                missingIdentity.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                anonymousIdentity.status,
            )
            assertEquals(
                PublicRankedQueueStatus.NOT_QUEUED,
                store.getPublicRankedQueueStatus(
                    identity = account.toRequestIdentity(),
                ).status,
            )
        }

    @Test
    fun enqueue_uses_only_session_identity_and_is_idempotent() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-player-1" },
            )
            val account = requireNotNull(
                store.promoteAccount(
                    playerId = "player-1",
                ),
            )
            requireNotNull(
                store.updateAccountProfile(
                    accountId = account.accountId,
                    publicDisplayName = "Jogador Teste",
                    tableName = "P01",
                ),
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = TestHeaderIdentityResolver(
                        mapOf(
                            "account-player-1" to
                                account.toRequestIdentity(),
                        ),
                    ),
                )
            }

            val maliciousBody = """
                {
                  "playerName": "P01",
                  "playerId": "attacker-player",
                  "accountId": "attacker-account",
                  "roomId": "attacker-room",
                  "roomCode": "9999",
                  "matchMode": "PRIVATE_UNRANKED",
                  "seatIndex": 3
                }
            """.trimIndent()

            val firstResponse = enqueue(
                identityKey = "account-player-1",
                body = maliciousBody,
            )
            val repeatedResponse = enqueue(
                identityKey = "account-player-1",
                body = maliciousBody,
            )

            assertEquals(HttpStatusCode.OK, firstResponse.status)
            assertEquals(HttpStatusCode.OK, repeatedResponse.status)

            val (first, firstBody) = readResponse(firstResponse)
            val (repeated, repeatedBody) =
                readResponse(repeatedResponse)

            assertEquals(
                PublicRankedQueueHttpStatus.WAITING,
                first.status,
            )
            assertEquals(1, first.queuePosition)
            assertEquals(first, repeated)
            assertSafeResponseBody(firstBody)
            assertSafeResponseBody(repeatedBody)

            val status = store.getPublicRankedQueueStatus(
                identity = account.toRequestIdentity(),
            )
            assertEquals(PublicRankedQueueStatus.QUEUED, status.status)
            assertEquals(1, status.queuePosition)
        }

    @Test
    fun matched_http_response_exposes_match_only_without_room_selection_data() =
        testApplication {
            var accountSequence = 0
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = {
                    accountSequence++
                    "account-$accountSequence"
                },
            )
            val accounts = (1..4).associateWith { playerNumber ->
                requireNotNull(
                    store.promoteAccount(
                        playerId = "player-$playerNumber",
                    ),
                )
            }
            accounts.forEach { (playerNumber, account) ->
                requireNotNull(
                    store.updateAccountProfile(
                        accountId = account.accountId,
                        publicDisplayName = "Jogador Teste",
                        tableName = rankedTableCode(playerNumber),
                    ),
                )
            }
            val resolver = TestHeaderIdentityResolver(
                accounts.map { (playerNumber, account) ->
                    "account-$playerNumber" to account.toRequestIdentity()
                }.toMap(),
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                )
            }

            var fourthResponse: HttpResponse? = null
            (1..4).forEach { playerNumber ->
                val response = enqueue(
                    identityKey = "account-$playerNumber",
                    body = json.encodeToString(
                        PublicRankedQueueEnterRequestDto(
                            playerName = rankedTableCode(playerNumber),
                        ),
                    ),
                )
                assertEquals(HttpStatusCode.OK, response.status)

                if (playerNumber == 4) {
                    fourthResponse = response
                }
            }

            val matchedResponse = requireNotNull(fourthResponse)
            val (matched, matchedBody) =
                readResponse(matchedResponse)

            assertEquals(
                PublicRankedQueueHttpStatus.MATCHED,
                matched.status,
            )
            assertNotNull(matched.matchId)
            assertNotNull(matched.localSeatIndex)
            assertSafeResponseBody(matchedBody)

            (1..4).forEach { playerNumber ->
                val pollingResponse = client.get(
                    urlString =
                        "/${OnlineRemoteRoutes.RANKED_QUEUE}",
                ) {
                    header(
                        TEST_IDENTITY_HEADER,
                        "account-$playerNumber",
                    )
                }

                assertEquals(
                    HttpStatusCode.OK,
                    pollingResponse.status,
                )

                val (polling, pollingBody) =
                    readResponse(pollingResponse)
                assertEquals(
                    PublicRankedQueueHttpStatus.MATCHED,
                    polling.status,
                )
                assertNotNull(polling.matchId)
                assertNotNull(polling.localSeatIndex)
                assertSafeResponseBody(pollingBody)
            }

            val cancelAfterMatch = client.delete(
                urlString =
                    "/${OnlineRemoteRoutes.RANKED_QUEUE}",
            ) {
                header(
                    TEST_IDENTITY_HEADER,
                    "account-1",
                )
            }

            assertEquals(
                HttpStatusCode.Conflict,
                cancelAfterMatch.status,
            )
            val (cancelledMatch, cancelledMatchBody) =
                readResponse(cancelAfterMatch)
            assertEquals(
                PublicRankedQueueHttpStatus.MATCHED,
                cancelledMatch.status,
            )
            assertSafeResponseBody(cancelledMatchBody)
        }

    @Test
    fun cancellation_is_idempotent_before_match_formation() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-player-1" },
            )
            val account = requireNotNull(
                store.promoteAccount(
                    playerId = "player-1",
                ),
            )
            requireNotNull(
                store.updateAccountProfile(
                    accountId = account.accountId,
                    publicDisplayName = "Jogador Teste",
                    tableName = "P01",
                ),
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = TestHeaderIdentityResolver(
                        mapOf(
                            "account-player-1" to
                                account.toRequestIdentity(),
                        ),
                    ),
                )
            }

            val enqueueResponse = enqueue(
                identityKey = "account-player-1",
                body = json.encodeToString(
                    PublicRankedQueueEnterRequestDto(
                        playerName = "P01",
                    ),
                ),
            )
            assertEquals(HttpStatusCode.OK, enqueueResponse.status)

            repeat(2) {
                val cancellation = client.delete(
                    urlString =
                        "/${OnlineRemoteRoutes.RANKED_QUEUE}",
                ) {
                    header(
                        TEST_IDENTITY_HEADER,
                        "account-player-1",
                    )
                }

                assertEquals(HttpStatusCode.OK, cancellation.status)
                assertEquals(
                    PublicRankedQueueHttpStatus.NOT_QUEUED,
                    readResponse(cancellation).first.status,
                )
            }

            val status = client.get(
                urlString =
                    "/${OnlineRemoteRoutes.RANKED_QUEUE}",
            ) {
                header(
                    TEST_IDENTITY_HEADER,
                    "account-player-1",
                )
            }

            assertEquals(HttpStatusCode.OK, status.status)
            assertEquals(
                PublicRankedQueueHttpStatus.NOT_QUEUED,
                readResponse(status).first.status,
            )
        }

    @Test
    fun queue_mutation_route_is_rate_limited_by_authenticated_identity() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-player-1" },
            )
            val account = requireNotNull(
                store.promoteAccount(
                    playerId = "player-1",
                ),
            )
            requireNotNull(
                store.updateAccountProfile(
                    accountId = account.accountId,
                    publicDisplayName = "Jogador Teste",
                    tableName = "P01",
                ),
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = TestHeaderIdentityResolver(
                        mapOf(
                            "account-player-1" to
                                account.toRequestIdentity(),
                        ),
                    ),
                    rateLimitPolicy = OnlineServerRateLimitPolicy(
                        authenticatedMutationRequestLimit = 1,
                    ),
                )
            }

            val body = json.encodeToString(
                PublicRankedQueueEnterRequestDto(
                    playerName = "P01",
                ),
            )
            val first = enqueue(
                identityKey = "account-player-1",
                body = body,
            )
            val second = enqueue(
                identityKey = "account-player-1",
                body = body,
            )

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(
                HttpStatusCode.TooManyRequests,
                second.status,
            )
        }

    private suspend fun ApplicationTestBuilder.enqueue(
        identityKey: String?,
        body: String,
    ): HttpResponse {
        return client.post(
            urlString = "/${OnlineRemoteRoutes.RANKED_QUEUE}",
        ) {
            if (identityKey != null) {
                header(
                    TEST_IDENTITY_HEADER,
                    identityKey,
                )
            }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private suspend fun readResponse(
        response: HttpResponse,
    ): Pair<PublicRankedQueueHttpResponseDto, String> {
        val body = response.bodyAsText()
        return json.decodeFromString<
                PublicRankedQueueHttpResponseDto
                >(body) to body
    }

    private fun assertSafeResponseBody(
        body: String,
    ) {
        listOf(
            "roomCode",
            "roomId",
            "accountId",
            "playerId",
            "players",
            "matchMode",
            "reason",
        ).forEach { forbiddenField ->
            assertFalse(
                "HTTP ranked queue response leaked $forbiddenField: $body",
                body.contains(forbiddenField),
            )
        }
    }

    private fun rankedTableCode(
        playerNumber: Int,
    ): String = "P0$playerNumber"

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "ranked-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )

    private class TestHeaderIdentityResolver(
        private val identitiesByKey: Map<String, OnlineRequestIdentity>,
    ) : OnlineRequestIdentityResolver {
        override fun resolve(
            call: ApplicationCall,
        ): OnlineRequestIdentity? {
            val key = call.request.headers[
                TEST_IDENTITY_HEADER,
            ] ?: return null

            return identitiesByKey[key]
        }
    }

    companion object {
        private const val TEST_IDENTITY_HEADER =
            "X-Test-Online-Identity"
    }
}
