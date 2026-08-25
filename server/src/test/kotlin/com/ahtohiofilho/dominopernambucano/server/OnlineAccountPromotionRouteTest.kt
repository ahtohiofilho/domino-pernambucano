package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OnlineAccountPromotionRouteTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun promotion_requires_auth_and_is_idempotent_for_anonymous_and_account_tokens() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-1" },
            )
            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { index ->
                    (index + 23).toByte()
                },
                nowEpochMillis = { 1_000L },
                sessionTtlMillis = 5_000L,
                playerIdFactory = { "player-1" },
                sessionIdFactory = { "account-session-1" },
            )
            val anonymousSession = tokenService.issueAnonymousSession()

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                    sessionTokenService = tokenService,
                )
            }

            val missingIdentityResponse = client.post(
                urlString = "/${OnlineRemoteRoutes.PROMOTE_ACCOUNT}",
            )

            assertEquals(
                HttpStatusCode.Unauthorized,
                missingIdentityResponse.status,
            )

            val firstResponse = client.promote(
                accessToken = anonymousSession.accessToken,
            )
            val firstSession =
                json.decodeFromString<OnlineAccountSessionDto>(
                    firstResponse.bodyAsText(),
                )
            val accountIdentity = tokenService.resolveAccessToken(
                firstSession.accessToken,
            )

            assertEquals(HttpStatusCode.OK, firstResponse.status)
            assertEquals(anonymousSession.playerId, firstSession.playerId)
            assertEquals("account-1", firstSession.accountId)
            assertNotNull(accountIdentity)
            assertEquals(OnlinePrincipalKind.ACCOUNT, accountIdentity?.kind)
            assertEquals("account-1", accountIdentity?.accountId)

            val anonymousRetry = client.promote(
                accessToken = anonymousSession.accessToken,
            )
            val accountRetry = client.promote(
                accessToken = firstSession.accessToken,
            )

            assertEquals(HttpStatusCode.OK, anonymousRetry.status)
            assertEquals(HttpStatusCode.OK, accountRetry.status)
            assertEquals(
                "account-1",
                json.decodeFromString<OnlineAccountSessionDto>(
                    anonymousRetry.bodyAsText(),
                ).accountId,
            )
            assertEquals(
                "account-1",
                json.decodeFromString<OnlineAccountSessionDto>(
                    accountRetry.bodyAsText(),
                ).accountId,
            )
            assertEquals(1, store.snapshotPersistentState().accounts.size)

            val conflictingAccountSession =
                tokenService.issueAccountSession(
                    playerId = anonymousSession.playerId,
                    accountId = "account-other",
                )
            val conflictingRetry = client.promote(
                accessToken = conflictingAccountSession.accessToken,
            )

            // Store-aware bearer validation rejects an ACCOUNT token whose
            // canonical account is not active before the mutation route.
            assertEquals(HttpStatusCode.Unauthorized, conflictingRetry.status)
            assertEquals(1, store.snapshotPersistentState().accounts.size)
        }

    private suspend fun io.ktor.client.HttpClient.promote(
        accessToken: String,
    ) = post(
        urlString = "/${OnlineRemoteRoutes.PROMOTE_ACCOUNT}",
    ) {
        header(
            HttpHeaders.Authorization,
            "Bearer $accessToken",
        )
    }
}
