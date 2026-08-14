package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.OnlineSyntheticAccountRecoveryRequestDto
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyntheticAccountRecoveryRouteTest {
    private val json = Json { encodeDefaults = true }
    private val secret = "0123456789abcdef0123456789abcdef"

    @Test
    fun authorized_recovery_renews_the_same_synthetic_account() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-synthetic" },
            )
            val account = requireNotNull(
                store.promoteSyntheticAccount("player-synthetic"),
            )
            var sessionSequence = 0
            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { index ->
                    (index + 17).toByte()
                },
                nowEpochMillis = { 1_000L },
                sessionTtlMillis = 5_000L,
                playerIdFactory = { "unused-player" },
                sessionIdFactory = {
                    sessionSequence++
                    "synthetic-session-$sessionSequence"
                },
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                    sessionTokenService = tokenService,
                    syntheticProvisioningPolicy =
                        SyntheticProvisioningPolicy.fixedForTest(secret),
                )
            }

            val first = client.recover(account.accountId, secret)
            val second = client.recover(account.accountId, secret)
            val firstSession = json.decodeFromString<OnlineAccountSessionDto>(
                first.bodyAsText(),
            )
            val secondSession = json.decodeFromString<OnlineAccountSessionDto>(
                second.bodyAsText(),
            )

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.OK, second.status)
            assertEquals(account.accountId, firstSession.accountId)
            assertEquals(account.playerId, firstSession.playerId)
            assertEquals(firstSession.accountId, secondSession.accountId)
            assertNotEquals(firstSession.accessToken, secondSession.accessToken)
            assertEquals(1, store.snapshotPersistentState().accounts.size)
        }

    @Test
    fun recovery_fails_closed_for_wrong_secret_or_human_account() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-human" },
            )
            val human = requireNotNull(store.promoteAccount("player-human"))
            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { 7 },
                nowEpochMillis = { 1_000L },
                sessionTtlMillis = 5_000L,
                playerIdFactory = { "unused-player" },
                sessionIdFactory = { "synthetic-session" },
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                    sessionTokenService = tokenService,
                    syntheticProvisioningPolicy =
                        SyntheticProvisioningPolicy.fixedForTest(secret),
                )
            }

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.recover(human.accountId, "wrong-secret").status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.recover(human.accountId, secret).status,
            )
        }

    private suspend fun io.ktor.client.HttpClient.recover(
        accountId: String,
        suppliedSecret: String,
    ) = post(
        urlString = "/${OnlineRemoteRoutes.RECOVER_SYNTHETIC_ACCOUNT}",
    ) {
        contentType(ContentType.Application.Json)
        header(
            OnlineRemoteHeaders.SYNTHETIC_PROVISIONING_SECRET,
            suppliedSecret,
        )
        setBody(
            json.encodeToString(
                OnlineSyntheticAccountRecoveryRequestDto(accountId),
            ),
        )
    }
}
