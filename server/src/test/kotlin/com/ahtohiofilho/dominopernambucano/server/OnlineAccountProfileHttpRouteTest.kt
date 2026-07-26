package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileUpdateRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import io.ktor.http.contentType
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OnlineAccountProfileHttpRouteTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun account_can_create_and_read_its_authoritative_profile() =
        testApplication {
            var now = 1_000L
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { now },
                accountIdFactory = { "account-1" },
            )
            requireNotNull(
                store.promoteAccount(
                    playerId = "player-1",
                ),
            )
            val resolver = headerResolver(
                "account" to OnlineRequestIdentity(
                    playerId = "player-1",
                    principalId = "principal-1",
                    sessionId = "session-1",
                    kind = OnlinePrincipalKind.ACCOUNT,
                    accountId = "account-1",
                ),
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                    nowEpochMillis = { now },
                )
            }

            val before = client.get(profileUrl()) {
                header(TEST_IDENTITY_HEADER, "account")
            }

            assertEquals(HttpStatusCode.NotFound, before.status)

            now = 2_000L
            val update = client.put(profileUrl()) {
                header(TEST_IDENTITY_HEADER, "account")
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        OnlineAccountProfileUpdateRequestDto(
                            publicDisplayName =
                                "  Antônio   Filho  ",
                            tableName = "afi",
                        ),
                    ),
                )
            }
            val updateBody = update.bodyAsText()
            val updated =
                json.decodeFromString<OnlineAccountProfileResponseDto>(
                    updateBody,
                )

            assertEquals(HttpStatusCode.OK, update.status)
            assertEquals("Antônio Filho", updated.publicDisplayName)
            assertEquals("AFI", updated.tableName)
            assertEquals(2_000L, updated.updatedAtEpochMillis)
            assertFalse(updateBody.contains("accountId"))
            assertFalse(updateBody.contains("playerId"))

            val read = client.get(profileUrl()) {
                header(TEST_IDENTITY_HEADER, "account")
            }
            val persisted =
                json.decodeFromString<OnlineAccountProfileResponseDto>(
                    read.bodyAsText(),
                )

            assertEquals(HttpStatusCode.OK, read.status)
            assertEquals(updated, persisted)
        }

    @Test
    fun anonymous_identity_cannot_read_or_mutate_account_profile() =
        testApplication {
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
                    store = InMemoryOnlineServerStore(),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                )
            }

            val read = client.get(profileUrl()) {
                header(TEST_IDENTITY_HEADER, "visitor")
            }
            val update = client.put(profileUrl()) {
                header(TEST_IDENTITY_HEADER, "visitor")
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        OnlineAccountProfileUpdateRequestDto(
                            publicDisplayName = "Antônio Filho",
                            tableName = "AFI",
                        ),
                    ),
                )
            }

            assertEquals(HttpStatusCode.Forbidden, read.status)
            assertEquals(HttpStatusCode.Forbidden, update.status)
        }

    @Test
    fun invalid_profile_is_rejected_without_persisting_partial_state() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                accountIdFactory = { "account-1" },
            )
            requireNotNull(
                store.promoteAccount(
                    playerId = "player-1",
                ),
            )
            val resolver = headerResolver(
                "account" to OnlineRequestIdentity(
                    playerId = "player-1",
                    principalId = "principal-1",
                    sessionId = "session-1",
                    kind = OnlinePrincipalKind.ACCOUNT,
                    accountId = "account-1",
                ),
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                )
            }

            val response = client.put(profileUrl()) {
                header(TEST_IDENTITY_HEADER, "account")
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        OnlineAccountProfileUpdateRequestDto(
                            publicDisplayName = "Antônio",
                            tableName = "A F!",
                        ),
                    ),
                )
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                null,
                store.getAccountProfile(
                    accountId = "account-1",
                ),
            )
        }

    private fun profileUrl(): String {
        return "/${OnlineRemoteRoutes.ACCOUNT_PROFILE}"
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
