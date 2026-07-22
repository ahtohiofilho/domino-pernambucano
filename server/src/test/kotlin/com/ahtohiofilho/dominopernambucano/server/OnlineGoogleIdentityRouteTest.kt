package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleIdentityRequestDto
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

class OnlineGoogleIdentityRouteTest {
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
                    serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                    sessionTokenService = tokenService,
                    googleIdentityTokenVerifier = verifier(
                        "valid-google-token" to "google-subject-1",
                    ),
                )
            }

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.linkGoogle(
                    idToken = "valid-google-token",
                    accessToken = null,
                ).status,
            )

            val linkResponse = client.linkGoogle(
                idToken = "valid-google-token",
                accessToken = anonymousSession.accessToken,
            )
            val linkedSession = json.decodeFromString<OnlineAccountSessionDto>(
                linkResponse.bodyAsText(),
            )

            assertEquals(HttpStatusCode.OK, linkResponse.status)
            assertEquals("account-1", linkedSession.accountId)
            assertEquals("player-1", linkedSession.playerId)

            val retryResponse = client.linkGoogle(
                idToken = "valid-google-token",
                accessToken = linkedSession.accessToken,
            )
            val recoveryResponse = client.recoverGoogle(
                idToken = "valid-google-token",
            )
            val recoveredSession =
                json.decodeFromString<OnlineAccountSessionDto>(
                    recoveryResponse.bodyAsText(),
                )

            assertEquals(HttpStatusCode.OK, retryResponse.status)
            assertEquals(HttpStatusCode.OK, recoveryResponse.status)
            assertEquals(linkedSession.accountId, recoveredSession.accountId)
            assertEquals(linkedSession.playerId, recoveredSession.playerId)
            assertEquals(1, store.snapshotPersistentState().accounts.size)
            assertEquals(
                1,
                store.snapshotPersistentState().externalIdentities.size,
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
                    (index + 51).toByte()
                },
                nowEpochMillis = { 1_000L },
                sessionTtlMillis = 5_000L,
                playerIdFactory = {
                    playerSequence += 1
                    "player-$playerSequence"
                },
                sessionIdFactory = { "session-$playerSequence" },
            )
            val firstAnonymous = tokenService.issueAnonymousSession()
            val secondAnonymous = tokenService.issueAnonymousSession()

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                    sessionTokenService = tokenService,
                    googleIdentityTokenVerifier =
                        OnlineGoogleIdentityTokenVerifier { token ->
                            when (token) {
                                "valid-token" -> {
                                    OnlineGoogleIdentityVerificationResult
                                        .Verified("google-subject-1")
                                }

                                "unknown-token" -> {
                                    OnlineGoogleIdentityVerificationResult
                                        .Verified("unknown-subject")
                                }

                                "unavailable-token" -> {
                                    OnlineGoogleIdentityVerificationResult
                                        .Unavailable
                                }

                                else -> {
                                    OnlineGoogleIdentityVerificationResult
                                        .Invalid
                                }
                            }
                        },
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                client.linkGoogle(
                    idToken = "valid-token",
                    accessToken = firstAnonymous.accessToken,
                ).status,
            )
            assertEquals(
                HttpStatusCode.Conflict,
                client.linkGoogle(
                    idToken = "valid-token",
                    accessToken = secondAnonymous.accessToken,
                ).status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.recoverGoogle("invalid-token").status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.recoverGoogle("unknown-token").status,
            )
            assertEquals(
                HttpStatusCode.ServiceUnavailable,
                client.recoverGoogle("unavailable-token").status,
            )
            assertEquals(1, store.snapshotPersistentState().accounts.size)
        }

    private fun tokenService(
        playerIds: ArrayDeque<String>,
    ) = HmacOnlineSessionTokenService(
        signingSecret = ByteArray(32) { index ->
            (index + 37).toByte()
        },
        nowEpochMillis = { 1_000L },
        sessionTtlMillis = 5_000L,
        playerIdFactory = { playerIds.removeFirst() },
        sessionIdFactory = { "session-1" },
    )

    private fun verifier(
        vararg identities: Pair<String, String>,
    ): OnlineGoogleIdentityTokenVerifier {
        val subjectsByToken = identities.toMap()
        return OnlineGoogleIdentityTokenVerifier { token ->
            subjectsByToken[token]
                ?.let { subject ->
                    OnlineGoogleIdentityVerificationResult.Verified(subject)
                }
                ?: OnlineGoogleIdentityVerificationResult.Invalid
        }
    }

    private suspend fun HttpClient.linkGoogle(
        idToken: String,
        accessToken: String?,
    ) = post(
        urlString = "/${OnlineRemoteRoutes.LINK_GOOGLE_IDENTITY}",
    ) {
        contentType(ContentType.Application.Json)
        accessToken?.let { token ->
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        setBody(
            json.encodeToString(
                OnlineGoogleIdentityRequestDto(idToken = idToken),
            ),
        )
    }

    private suspend fun HttpClient.recoverGoogle(
        idToken: String,
    ) = post(
        urlString = "/${OnlineRemoteRoutes.RECOVER_GOOGLE_ACCOUNT}",
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                OnlineGoogleIdentityRequestDto(idToken = idToken),
            ),
        )
    }
}
