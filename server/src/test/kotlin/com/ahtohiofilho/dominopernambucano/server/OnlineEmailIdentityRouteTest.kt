package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailCodeRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailCodeRequestResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailIdentityRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailIdentityRoutes
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineEmailIdentityRouteTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun routes_are_not_exposed_without_phase_a_service() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(),
                    serverEnvironment =
                        OnlineServerEnvironment.PRODUCTION,
                    sessionTokenService =
                        HmacOnlineSessionTokenService(
                            signingSecret =
                                ByteArray(32) { index ->
                                    (index + 73).toByte()
                                },
                        ),
                )
            }

            assertEquals(
                HttpStatusCode.NotFound,
                client.requestEmailCode(
                    email = "player@example.com",
                ).status,
            )
        }

    @Test
    fun request_link_and_recovery_preserve_canonical_account() =
        testApplication {
            var now = 1_000L
            var playerSequence = 0
            val codes = ArrayDeque(
                listOf(
                    "123456",
                    "654321",
                ),
            )
            val sender = RecordingSender()
            val emailService = OnlineEmailVerificationService(
                sender = sender,
                nowEpochMillis = { now },
                codeGenerator = {
                    codes.removeFirst()
                },
                hashSecret = ByteArray(32) { index ->
                    (index + 31).toByte()
                },
            )
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { now },
                accountIdFactory = { "account-1" },
            )
            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { index ->
                    (index + 41).toByte()
                },
                nowEpochMillis = { now },
                sessionTtlMillis = 60L * 60L * 1_000L,
                playerIdFactory = {
                    playerSequence += 1
                    "player-$playerSequence"
                },
                sessionIdFactory = {
                    "session-$playerSequence"
                },
            )
            val anonymousSession =
                tokenService.issueAnonymousSession()

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    sessionTokenService = tokenService,
                    emailVerificationService = emailService,
                )
            }

            val requestResponse = client.requestEmailCode(
                email = " Player@Example.COM ",
            )
            val requestBody =
                json.decodeFromString<OnlineEmailCodeRequestResponseDto>(
                    requestResponse.bodyAsText(),
                )

            assertEquals(
                HttpStatusCode.Accepted,
                requestResponse.status,
            )
            assertTrue(requestBody.accepted)
            assertEquals(
                listOf(
                    "player@example.com" to "123456",
                ),
                sender.messages,
            )

            val linkResponse = client.linkEmail(
                email = "player@example.com",
                code = "123456",
                accessToken = anonymousSession.accessToken,
            )
            val linkedSession =
                json.decodeFromString<OnlineAccountSessionDto>(
                    linkResponse.bodyAsText(),
                )

            assertEquals(HttpStatusCode.OK, linkResponse.status)
            assertEquals("account-1", linkedSession.accountId)
            assertEquals("player-1", linkedSession.playerId)

            val persistedIdentity =
                store.snapshotPersistentState()
                    .externalIdentities
                    .single()

            assertEquals(
                OnlineExternalIdentityProvider.EMAIL,
                persistedIdentity.provider,
            )
            assertEquals(
                onlineEmailIdentitySubject("player@example.com"),
                persistedIdentity.subject,
            )
            assertNotEquals(
                "player@example.com",
                persistedIdentity.subject,
            )

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.linkEmail(
                    email = "player@example.com",
                    code = "123456",
                    accessToken = linkedSession.accessToken,
                ).status,
            )

            now += 60_000L
            assertEquals(
                HttpStatusCode.Accepted,
                client.requestEmailCode(
                    email = "PLAYER@example.com",
                ).status,
            )

            val recoveryResponse = client.recoverEmail(
                email = "player@example.com",
                code = "654321",
            )
            val recoveredSession =
                json.decodeFromString<OnlineAccountSessionDto>(
                    recoveryResponse.bodyAsText(),
                )

            assertEquals(
                HttpStatusCode.OK,
                recoveryResponse.status,
            )
            assertEquals(
                linkedSession.accountId,
                recoveredSession.accountId,
            )
            assertEquals(
                linkedSession.playerId,
                recoveredSession.playerId,
            )
            assertEquals(
                1,
                store.snapshotPersistentState().accounts.size,
            )
            assertEquals(
                1,
                store.snapshotPersistentState()
                    .externalIdentities
                    .size,
            )

            val messageCount = sender.messages.size
            val invalidResponse = client.requestEmailCode(
                email = "not-an-email",
            )
            val invalidBody =
                json.decodeFromString<OnlineEmailCodeRequestResponseDto>(
                    invalidResponse.bodyAsText(),
                )

            assertEquals(
                HttpStatusCode.Accepted,
                invalidResponse.status,
            )
            assertEquals(requestBody, invalidBody)
            assertEquals(messageCount, sender.messages.size)
        }

    @Test
    fun unknown_account_and_invalid_code_fail_closed() =
        testApplication {
            val sender = RecordingSender()
            val emailService = OnlineEmailVerificationService(
                sender = sender,
                codeGenerator = { "123456" },
                hashSecret = ByteArray(32) { index ->
                    (index + 61).toByte()
                },
            )

            application {
                module(
                    store = InMemoryOnlineServerStore(),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    emailVerificationService = emailService,
                )
            }

            client.requestEmailCode(
                email = "unknown@example.com",
            )

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.recoverEmail(
                    email = "unknown@example.com",
                    code = "000000",
                ).status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.recoverEmail(
                    email = "unknown@example.com",
                    code = "123456",
                ).status,
            )
        }

    private suspend fun HttpClient.requestEmailCode(
        email: String,
    ) = post(
        urlString =
            "/${OnlineEmailIdentityRoutes.REQUEST_CODE}",
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                OnlineEmailCodeRequestDto(
                    email = email,
                ),
            ),
        )
    }

    private suspend fun HttpClient.linkEmail(
        email: String,
        code: String,
        accessToken: String?,
    ) = post(
        urlString =
            "/${OnlineEmailIdentityRoutes.LINK_IDENTITY}",
    ) {
        contentType(ContentType.Application.Json)
        accessToken?.let { token ->
            header(
                HttpHeaders.Authorization,
                "Bearer $token",
            )
        }
        setBody(
            json.encodeToString(
                OnlineEmailIdentityRequestDto(
                    email = email,
                    code = code,
                ),
            ),
        )
    }

    private suspend fun HttpClient.recoverEmail(
        email: String,
        code: String,
    ) = post(
        urlString =
            "/${OnlineEmailIdentityRoutes.RECOVER_ACCOUNT}",
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                OnlineEmailIdentityRequestDto(
                    email = email,
                    code = code,
                ),
            ),
        )
    }

    private class RecordingSender :
        OnlineEmailVerificationCodeSender {
        val messages =
            mutableListOf<Pair<String, String>>()

        override suspend fun sendVerificationCode(
            email: String,
            code: String,
        ) {
            messages += email to code
        }
    }
}
