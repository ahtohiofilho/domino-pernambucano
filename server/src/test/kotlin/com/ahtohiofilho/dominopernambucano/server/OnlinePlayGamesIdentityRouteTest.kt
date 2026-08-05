package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayGamesIdentityRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlinePlayGamesIdentityRouteTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun link_and_recovery_preserve_canonical_account_and_player() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-1" },
            )
            val tokenService = tokenService(
                playerIds = ArrayDeque(
                    listOf(
                        "player-1",
                        "player-must-not-be-created",
                    ),
                ),
            )
            val anonymousSession = tokenService.issueAnonymousSession()

            application {
                module(
                    store = store,
                    serverEnvironment =
                        OnlineServerEnvironment.PRODUCTION,
                    sessionTokenService = tokenService,
                    playGamesIdentityVerifier = verifier(
                        "valid-play-games-code" to
                            "play-games-player-1",
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.linkPlayGames(
                    serverAuthCode = "valid-play-games-code",
                    accessToken = null,
                ).status,
            )

            val linkResponse = client.linkPlayGames(
                serverAuthCode = "valid-play-games-code",
                accessToken = anonymousSession.accessToken,
            )
            val linkedSession =
                json.decodeFromString<OnlineAccountSessionDto>(
                    linkResponse.bodyAsText(),
                )

            assertEquals(HttpStatusCode.OK, linkResponse.status)
            assertEquals("account-1", linkedSession.accountId)
            assertEquals("player-1", linkedSession.playerId)

            val retryResponse = client.linkPlayGames(
                serverAuthCode = "valid-play-games-code",
                accessToken = linkedSession.accessToken,
            )
            val recoveryResponse = client.recoverPlayGames(
                serverAuthCode = "valid-play-games-code",
            )
            val recoveredSession =
                json.decodeFromString<OnlineAccountSessionDto>(
                    recoveryResponse.bodyAsText(),
                )

            assertEquals(HttpStatusCode.OK, retryResponse.status)
            assertEquals(HttpStatusCode.OK, recoveryResponse.status)
            assertEquals(
                linkedSession.accountId,
                recoveredSession.accountId,
            )
            assertEquals(
                linkedSession.playerId,
                recoveredSession.playerId,
            )
            assertEquals(1, store.snapshotPersistentState().accounts.size)
            assertEquals(
                listOf(OnlineExternalIdentityProvider.PLAY_GAMES),
                store.snapshotPersistentState()
                    .externalIdentities
                    .map { identity -> identity.provider },
            )
        }

    @Test
    fun invalid_unknown_unavailable_and_conflicting_identities_fail_closed() =
        testApplication {
            var playerSequence = 0
            var accountSequence = 0
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = {
                    accountSequence += 1
                    "account-$accountSequence"
                },
            )
            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { index ->
                    (index + 71).toByte()
                },
                nowEpochMillis = { 1_000L },
                sessionTtlMillis = 5_000L,
                playerIdFactory = {
                    playerSequence += 1
                    "player-$playerSequence"
                },
                sessionIdFactory = {
                    "session-$playerSequence"
                },
            )
            val firstAnonymous = tokenService.issueAnonymousSession()
            val secondAnonymous = tokenService.issueAnonymousSession()

            application {
                module(
                    store = store,
                    serverEnvironment =
                        OnlineServerEnvironment.PRODUCTION,
                    sessionTokenService = tokenService,
                    playGamesIdentityVerifier =
                        OnlinePlayGamesIdentityVerifier { code ->
                            when (code) {
                                "valid-code" -> {
                                    OnlinePlayGamesIdentityVerificationResult
                                        .Verified("play-games-player-1")
                                }

                                "unknown-code" -> {
                                    OnlinePlayGamesIdentityVerificationResult
                                        .Verified("unknown-player")
                                }

                                "unavailable-code" -> {
                                    OnlinePlayGamesIdentityVerificationResult
                                        .Unavailable
                                }

                                else -> {
                                    OnlinePlayGamesIdentityVerificationResult
                                        .Invalid
                                }
                            }
                        },
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                client.linkPlayGames(
                    serverAuthCode = "valid-code",
                    accessToken = firstAnonymous.accessToken,
                ).status,
            )
            assertEquals(
                HttpStatusCode.Conflict,
                client.linkPlayGames(
                    serverAuthCode = "valid-code",
                    accessToken = secondAnonymous.accessToken,
                ).status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.recoverPlayGames("invalid-code").status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.recoverPlayGames("unknown-code").status,
            )
            assertEquals(
                HttpStatusCode.ServiceUnavailable,
                client.recoverPlayGames("unavailable-code").status,
            )
            assertEquals(1, store.snapshotPersistentState().accounts.size)
        }

    private fun tokenService(
        playerIds: ArrayDeque<String>,
    ) = HmacOnlineSessionTokenService(
        signingSecret = ByteArray(32) { index ->
            (index + 67).toByte()
        },
        nowEpochMillis = { 1_000L },
        sessionTtlMillis = 5_000L,
        playerIdFactory = { playerIds.removeFirst() },
        sessionIdFactory = { "session-1" },
    )

    private fun verifier(
        vararg identities: Pair<String, String>,
    ): OnlinePlayGamesIdentityVerifier {
        val playersByCode = identities.toMap()

        return OnlinePlayGamesIdentityVerifier { code ->
            playersByCode[code]
                ?.let { playerId ->
                    OnlinePlayGamesIdentityVerificationResult.Verified(
                        playerId = playerId,
                    )
                }
                ?: OnlinePlayGamesIdentityVerificationResult.Invalid
        }
    }

    private suspend fun HttpClient.linkPlayGames(
        serverAuthCode: String,
        accessToken: String?,
    ) = post(
        urlString =
            "/${OnlineRemoteRoutes.LINK_PLAY_GAMES_IDENTITY}",
    ) {
        contentType(ContentType.Application.Json)
        accessToken?.let { token ->
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        setBody(
            json.encodeToString(
                OnlinePlayGamesIdentityRequestDto(
                    serverAuthCode = serverAuthCode,
                ),
            ),
        )
    }

    private suspend fun HttpClient.recoverPlayGames(
        serverAuthCode: String,
    ) = post(
        urlString =
            "/${OnlineRemoteRoutes.RECOVER_PLAY_GAMES_ACCOUNT}",
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                OnlinePlayGamesIdentityRequestDto(
                    serverAuthCode = serverAuthCode,
                ),
            ),
        )
    }
}
