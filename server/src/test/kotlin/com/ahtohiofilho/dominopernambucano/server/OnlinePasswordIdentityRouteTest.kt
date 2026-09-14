package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailCodeRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailIdentityRoutes
import com.ahtohiofilho.dominopernambucano.online.OnlinePasswordIdentityRoutes
import com.ahtohiofilho.dominopernambucano.online.OnlinePasswordLoginRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePasswordRegisterRequestDto
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

class OnlinePasswordIdentityRouteTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun register_then_login_recovers_the_same_canonical_account() =
        testApplication {
            var now = 1_000L
            var sessionSequence = 0
            val emailService = OnlineEmailVerificationService(
                sender = RecordingPasswordRouteSender(),
                nowEpochMillis = { now },
                codeGenerator = { "123456" },
                hashSecret = ByteArray(32) { index ->
                    (index + 19).toByte()
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
                playerIdFactory = { "player-1" },
                sessionIdFactory = {
                    sessionSequence += 1
                    "session-$sessionSequence"
                },
            )
            val anonymous = tokenService.issueAnonymousSession()

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    sessionTokenService = tokenService,
                    emailVerificationService = emailService,
                )
            }

            assertEquals(
                HttpStatusCode.Accepted,
                client.requestPasswordEmailCode(
                    email = "player@example.com",
                ).status,
            )

            val registerResponse =
                client.registerPasswordAccount(
                    email = "player@example.com",
                    password = "SenhaSegura123!",
                    code = "123456",
                    accessToken = anonymous.accessToken,
                )

            assertEquals(
                HttpStatusCode.OK,
                registerResponse.status,
            )
            val registered =
                json.decodeFromString<OnlineAccountSessionDto>(
                    registerResponse.bodyAsText(),
                )
            assertEquals("account-1", registered.accountId)
            assertEquals("player-1", registered.playerId)

            val loginResponse = client.loginPasswordAccount(
                email = "player@example.com",
                password = "SenhaSegura123!",
            )

            assertEquals(
                HttpStatusCode.OK,
                loginResponse.status,
            )
            val loggedIn =
                json.decodeFromString<OnlineAccountSessionDto>(
                    loginResponse.bodyAsText(),
                )
            assertEquals(registered.accountId, loggedIn.accountId)
            assertEquals(registered.playerId, loggedIn.playerId)
        }

    private suspend fun HttpClient.requestPasswordEmailCode(
        email: String,
    ) = post(
        urlString = "/${OnlineEmailIdentityRoutes.REQUEST_CODE}",
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                OnlineEmailCodeRequestDto(email = email),
            ),
        )
    }

    private suspend fun HttpClient.registerPasswordAccount(
        email: String,
        password: String,
        code: String,
        accessToken: String,
    ) = post(
        urlString = "/${OnlinePasswordIdentityRoutes.REGISTER}",
    ) {
        contentType(ContentType.Application.Json)
        header(
            HttpHeaders.Authorization,
            "Bearer $accessToken",
        )
        setBody(
            json.encodeToString(
                OnlinePasswordRegisterRequestDto(
                    email = email,
                    password = password,
                    code = code,
                ),
            ),
        )
    }

    private suspend fun HttpClient.loginPasswordAccount(
        email: String,
        password: String,
    ) = post(
        urlString = "/${OnlinePasswordIdentityRoutes.LOGIN}",
    ) {
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                OnlinePasswordLoginRequestDto(
                    email = email,
                    password = password,
                ),
            ),
        )
    }

    private class RecordingPasswordRouteSender :
        OnlineEmailVerificationCodeSender {
        override suspend fun sendVerificationCode(
            email: String,
            code: String,
        ) = Unit
    }
}
