package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueEnterRequestDto
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionRankedClientGateHttpRouteTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun versioned_gate_rejects_missing_and_wrong_human_versions() =
        testApplication {
            val fixture = humanFixture()
            application {
                module(
                    store = fixture.store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = fixture.resolver,
                    productionRankedClientGate =
                        ProductionRankedClientGate.versionedForTest(
                            requiredClientVersionCode = 123,
                        ),
                )
            }

            assertEquals(
                HttpStatusCode.UpgradeRequired,
                enqueue(
                    identityKey = fixture.identityKey,
                    clientVersionCode = null,
                ).status,
            )
            assertEquals(
                HttpStatusCode.UpgradeRequired,
                enqueue(
                    identityKey = fixture.identityKey,
                    clientVersionCode = "122",
                ).status,
            )
            assertEquals(
                PublicRankedQueueStatus.NOT_QUEUED,
                fixture.store.getPublicRankedQueueStatus(
                    identity = fixture.account.toRequestIdentity(),
                ).status,
            )
        }

    @Test
    fun versioned_gate_accepts_exact_human_version() =
        testApplication {
            val fixture = humanFixture()
            application {
                module(
                    store = fixture.store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = fixture.resolver,
                    productionRankedClientGate =
                        ProductionRankedClientGate.versionedForTest(
                            requiredClientVersionCode = 123,
                        ),
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                enqueue(
                    identityKey = fixture.identityKey,
                    clientVersionCode = "123",
                ).status,
            )
            assertEquals(
                PublicRankedQueueStatus.QUEUED,
                fixture.store.getPublicRankedQueueStatus(
                    identity = fixture.account.toRequestIdentity(),
                ).status,
            )
        }

    @Test
    fun versioned_gate_allows_server_managed_synthetic_without_android_version() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-synthetic-1" },
            )
            val account = requireNotNull(
                store.promoteSyntheticAccount(
                    playerId = "synthetic-player-1",
                ),
            )
            requireNotNull(
                store.updateAccountProfile(
                    accountId = account.accountId,
                    publicDisplayName = "Sintetico Teste",
                    tableName = "S01",
                ),
            )
            val identityKey = "synthetic-identity"
            val resolver = TestHeaderIdentityResolver(
                mapOf(
                    identityKey to account.toRequestIdentity(),
                ),
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = resolver,
                    productionRankedClientGate =
                        ProductionRankedClientGate.versionedForTest(
                            requiredClientVersionCode = 123,
                        ),
                )
            }

            assertEquals(
                HttpStatusCode.OK,
                enqueue(
                    identityKey = identityKey,
                    clientVersionCode = null,
                    playerName = "S01",
                ).status,
            )
        }

    @Test
    fun closed_gate_rejects_even_exact_version_without_queue_mutation() =
        testApplication {
            val fixture = humanFixture()
            application {
                module(
                    store = fixture.store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    identityResolver = fixture.resolver,
                    productionRankedClientGate =
                        ProductionRankedClientGate.closedForTest(),
                )
            }

            assertEquals(
                HttpStatusCode.ServiceUnavailable,
                enqueue(
                    identityKey = fixture.identityKey,
                    clientVersionCode = "123",
                ).status,
            )
            assertEquals(
                PublicRankedQueueStatus.NOT_QUEUED,
                fixture.store.getPublicRankedQueueStatus(
                    identity = fixture.account.toRequestIdentity(),
                ).status,
            )
        }

    private fun humanFixture(): HumanFixture {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "account-human-1" },
        )
        val account = requireNotNull(
            store.promoteAccount(
                playerId = "human-player-1",
            ),
        )
        requireNotNull(
            store.updateAccountProfile(
                accountId = account.accountId,
                publicDisplayName = "Humano Teste",
                tableName = "H01",
            ),
        )

        val identityKey = "human-identity"
        return HumanFixture(
            store = store,
            account = account,
            identityKey = identityKey,
            resolver = TestHeaderIdentityResolver(
                mapOf(
                    identityKey to account.toRequestIdentity(),
                ),
            ),
        )
    }

    private suspend fun ApplicationTestBuilder.enqueue(
        identityKey: String,
        clientVersionCode: String?,
        playerName: String = "H01",
    ) = client.post(
        urlString = "/${OnlineRemoteRoutes.RANKED_QUEUE}",
    ) {
        header(TEST_IDENTITY_HEADER, identityKey)
        clientVersionCode?.let { version ->
            header(
                OnlineRemoteHeaders.CLIENT_VERSION_CODE,
                version,
            )
        }
        contentType(ContentType.Application.Json)
        setBody(
            json.encodeToString(
                PublicRankedQueueEnterRequestDto(
                    playerName = playerName,
                ),
            ),
        )
    }

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "gate-test:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )

    private data class HumanFixture(
        val store: InMemoryOnlineServerStore,
        val account: OnlineServerAccount,
        val identityKey: String,
        val resolver: OnlineRequestIdentityResolver,
    )

    private class TestHeaderIdentityResolver(
        private val identitiesByKey:
            Map<String, OnlineRequestIdentity>,
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