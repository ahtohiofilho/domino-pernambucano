package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRankingAchievementRemoteClientTest {
    @Test
    fun valid_gallery_uses_bearer_and_decodes_official_achievement() =
        runBlocking {
            var path: String? = null
            var offset: String? = null
            var limit: String? = null
            var authorization: String? = null

            val httpClient = HttpClient(
                MockEngine { request ->
                    path = request.url.encodedPath
                    offset = request.url.parameters["offset"]
                    limit = request.url.parameters["limit"]
                    authorization =
                        request.headers[HttpHeaders.Authorization]

                    respond(
                        content = validGalleryJson(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val result = client(
                httpClient = httpClient,
                credential = credential(),
            ).fetch(
                offset = 0,
                limit = 1,
            ) as OnlineRankingAchievementClientResult.Success

            assertEquals("/ranking/achievements", path)
            assertEquals("0", offset)
            assertEquals("1", limit)
            assertEquals("Bearer gallery-token", authorization)
            assertTrue(result.response.available)
            assertEquals(
                PublicRankingAwardTierDto.DIAMOND,
                result.response.achievements.single().awardTier,
            )

            httpClient.close()
        }

    @Test
    fun missing_credential_hides_gallery_without_remote_request() =
        runBlocking {
            var requested = false
            val httpClient = HttpClient(
                MockEngine {
                    requested = true
                    respond(
                        content = validGalleryJson(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val result = client(
                httpClient = httpClient,
                credential = null,
            ).fetch(
                offset = 0,
                limit = 1,
            ) as OnlineRankingAchievementClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind.AUTHENTICATION_REQUIRED,
                result.kind,
            )
            assertFalse(result.retryable)
            assertFalse(requested)

            httpClient.close()
        }

    @Test
    fun impossible_tier_for_rank_is_rejected_as_protocol_error() =
        runBlocking {
            val httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = validGalleryJson().replace(
                            "\"awardTier\": \"DIAMOND\"",
                            "\"awardTier\": \"GOLD\"",
                        ),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(createOnlineJson())
                }
            }

            val result = client(
                httpClient = httpClient,
                credential = credential(),
            ).fetch(
                offset = 0,
                limit = 1,
            ) as OnlineRankingAchievementClientResult.Failure

            assertEquals(
                OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
                result.kind,
            )
            assertFalse(result.retryable)

            httpClient.close()
        }

    private fun client(
        httpClient: HttpClient,
        credential: OnlineSessionCredential?,
    ): OnlineRankingAchievementRemoteClient {
        return OnlineRankingAchievementRemoteClient(
            remoteApiClient = KtorRemoteOnlineApiClient(
                config = OnlineBackendConfig.remote(
                    baseUrl = "http://localhost:8080",
                ),
                httpClient = httpClient,
            ),
            sessionCredentialRepository =
                OnlineSessionCredentialRepository(
                    store = MemoryCredentialStore(credential),
                    nowEpochMillis = { 1_000L },
                ),
        )
    }

    private fun credential(): OnlineSessionCredential {
        return OnlineSessionCredential(
            sessionKind = OnlineSessionKind.ACCOUNT,
            playerId = "gallery-player",
            accountId = "gallery-account",
            accessToken = "gallery-token",
            expiresAtEpochMillis = 10_000L,
        )
    }

    private fun validGalleryJson(): String {
        return """
            {
              "available": true,
              "totalAchievements": 1,
              "totalChampionships": 1,
              "totalAwardedPlayers": 1,
              "offset": 0,
              "limit": 1,
              "hasMore": false,
              "achievements": [
                {
                  "competitorId": "competitor-abc",
                  "displayName": "Jogador",
                  "cycle": "DAILY",
                  "cycleId": "ranking-v3:daily:2026-09-26",
                  "startsAtEpochMillis": 1000,
                  "endsAtEpochMillis": 2000,
                  "closedAtEpochMillis": 2001,
                  "rank": 1,
                  "awardTier": "DIAMOND",
                  "awardRuleVersion": 1,
                  "diamondCount": 1,
                  "goldCount": 0,
                  "silverCount": 0,
                  "bronzeCount": 0
                }
              ]
            }
        """.trimIndent()
    }

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    private class MemoryCredentialStore(
        initialCredential: OnlineSessionCredential?,
    ) : OnlineSessionCredentialStore {
        private var credential = initialCredential

        override fun read(): OnlineSessionCredential? {
            return credential
        }

        override fun write(
            credential: OnlineSessionCredential,
        ): Boolean {
            this.credential = credential
            return true
        }

        override fun clear(): Boolean {
            credential = null
            return true
        }
    }
}