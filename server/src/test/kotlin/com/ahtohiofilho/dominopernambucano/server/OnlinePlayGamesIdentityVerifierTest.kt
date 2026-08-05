package com.ahtohiofilho.dominopernambucano.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlayGamesIdentityVerifierTest {
    @Test
    fun valid_code_is_exchanged_and_current_player_is_verified() =
        runBlocking {
            val requests = mutableListOf<OnlinePlayGamesHttpRequest>()
            val responses = ArrayDeque(
                listOf(
                    OnlinePlayGamesHttpResponse(
                        statusCode = 200,
                        body = """{"access_token":"access-token-1"}""",
                    ),
                    OnlinePlayGamesHttpResponse(
                        statusCode = 200,
                        body = """{"playerId":"play-games-player-1"}""",
                    ),
                ),
            )
            val verifier = verifier(
                transport = OnlinePlayGamesHttpTransport { request ->
                    requests += request
                    responses.removeFirst()
                },
            )

            assertEquals(
                OnlinePlayGamesIdentityVerificationResult.Verified(
                    playerId = "play-games-player-1",
                ),
                verifier.verify("auth code/+"),
            )

            assertEquals(2, requests.size)
            assertEquals("POST", requests[0].method)
            assertEquals(
                "https://oauth2.googleapis.com/token",
                requests[0].url,
            )
            assertEquals(
                "application/x-www-form-urlencoded",
                requests[0].headers["Content-Type"],
            )
            val tokenBody = requireNotNull(requests[0].body)
            assertTrue(tokenBody.contains("code=auth+code%2F%2B"))
            assertTrue(
                tokenBody.contains(
                    "client_id=server-client.apps.googleusercontent.com",
                ),
            )
            assertTrue(tokenBody.contains("client_secret=secret%2Fvalue"))
            assertTrue(tokenBody.contains("redirect_uri="))
            assertTrue(
                tokenBody.contains("grant_type=authorization_code"),
            )
            assertFalse(tokenBody.contains("refresh_token"))

            assertEquals("GET", requests[1].method)
            assertEquals(
                "https://games.googleapis.com/games/v1/players/me",
                requests[1].url,
            )
            assertEquals(
                "Bearer access-token-1",
                requests[1].headers["Authorization"],
            )
        }

    @Test
    fun invalid_code_and_google_rejections_fail_closed() = runBlocking {
        var calls = 0
        val unusedTransport = OnlinePlayGamesHttpTransport {
            calls += 1
            OnlinePlayGamesHttpResponse(500, "")
        }
        val localVerifier = verifier(unusedTransport)

        assertSame(
            OnlinePlayGamesIdentityVerificationResult.Invalid,
            localVerifier.verify(" "),
        )
        assertSame(
            OnlinePlayGamesIdentityVerificationResult.Invalid,
            localVerifier.verify("x".repeat(8_193)),
        )
        assertEquals(0, calls)

        assertSame(
            OnlinePlayGamesIdentityVerificationResult.Invalid,
            verifier(
                OnlinePlayGamesHttpTransport {
                    OnlinePlayGamesHttpResponse(
                        statusCode = 400,
                        body = """{"error":"invalid_grant"}""",
                    )
                },
            ).verify("rejected-code"),
        )
    }

    @Test
    fun upstream_failures_and_malformed_success_responses_are_unavailable() =
        runBlocking {
            assertSame(
                OnlinePlayGamesIdentityVerificationResult.Unavailable,
                verifier(
                    OnlinePlayGamesHttpTransport {
                        OnlinePlayGamesHttpResponse(
                            statusCode = 503,
                            body = "",
                        )
                    },
                ).verify("valid-shape-code"),
            )

            val malformedResponses = ArrayDeque(
                listOf(
                    OnlinePlayGamesHttpResponse(
                        statusCode = 200,
                        body = """{"access_token":"token"}""",
                    ),
                    OnlinePlayGamesHttpResponse(
                        statusCode = 200,
                        body = """{"displayName":"Jogador"}""",
                    ),
                ),
            )
            assertSame(
                OnlinePlayGamesIdentityVerificationResult.Unavailable,
                verifier(
                    OnlinePlayGamesHttpTransport {
                        malformedResponses.removeFirst()
                    },
                ).verify("another-code"),
            )
        }

    @Test
    fun player_authorization_rejection_is_invalid() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                OnlinePlayGamesHttpResponse(
                    statusCode = 200,
                    body = """{"access_token":"token"}""",
                ),
                OnlinePlayGamesHttpResponse(
                    statusCode = 401,
                    body = """{"error":{"code":401}}""",
                ),
            ),
        )

        assertSame(
            OnlinePlayGamesIdentityVerificationResult.Invalid,
            verifier(
                OnlinePlayGamesHttpTransport {
                    responses.removeFirst()
                },
            ).verify("valid-code"),
        )
    }

    @Test
    fun missing_server_credentials_disable_verification() = runBlocking {
        val verifier = createDefaultOnlinePlayGamesIdentityVerifier {
            null
        }

        assertSame(
            OnlinePlayGamesIdentityVerificationResult.Unavailable,
            verifier.verify("code"),
        )
    }

    private fun verifier(
        transport: OnlinePlayGamesHttpTransport,
    ) = GooglePlayGamesServerIdentityVerifier(
        webClientId =
            "server-client.apps.googleusercontent.com",
        webClientSecret = "secret/value",
        transport = transport,
    )
}
