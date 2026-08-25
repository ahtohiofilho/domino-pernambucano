package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAccountDeletionHttpRouteTest {
    @Test
    fun human_account_can_delete_and_old_token_is_immediately_invalid() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                accountIdFactory = { "account-delete" },
            )
            val account = requireNotNull(
                store.promoteAccount(
                    playerId = "player-delete",
                ),
            )
            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { 7 },
                nowEpochMillis = { 1_000L },
            )
            val session = tokenService.issueAccountSession(
                playerId = account.playerId,
                accountId = account.accountId,
            )
            val traceDirectory = Files.createTempDirectory(
                "domino-account-deletion-trace",
            ).toFile()

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    sessionTokenService = tokenService,
                    traceArchive = OnlineTraceArchive(
                        outputDirectory = traceDirectory,
                    ),
                )
            }

            val deletion = client.delete(
                "/${OnlineRemoteRoutes.DELETE_ACCOUNT}",
            ) {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${session.accessToken}",
                )
            }

            assertEquals(HttpStatusCode.NoContent, deletion.status)
            assertFalse(
                store.isAccountIdentityActive(
                    accountId = account.accountId,
                    playerId = account.playerId,
                ),
            )

            val staleTokenRead = client.get(
                "/${OnlineRemoteRoutes.ACCOUNT_PROFILE}",
            ) {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${session.accessToken}",
                )
            }

            assertEquals(
                HttpStatusCode.Unauthorized,
                staleTokenRead.status,
            )

            traceDirectory.deleteRecursively()
        }

    @Test
    fun synthetic_account_cannot_use_human_deletion_route() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                accountIdFactory = { "synthetic-account" },
            )
            val account = requireNotNull(
                store.promoteSyntheticAccount(
                    playerId = "synthetic-player",
                ),
            )
            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { 8 },
                nowEpochMillis = { 1_000L },
            )
            val session = tokenService.issueAccountSession(
                playerId = account.playerId,
                accountId = account.accountId,
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    sessionTokenService = tokenService,
                )
            }

            val deletion = client.delete(
                "/${OnlineRemoteRoutes.DELETE_ACCOUNT}",
            ) {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${session.accessToken}",
                )
            }

            assertEquals(HttpStatusCode.Forbidden, deletion.status)
            assertTrue(
                store.isAccountIdentityActive(
                    accountId = account.accountId,
                    playerId = account.playerId,
                ),
            )
        }

    @Test
    fun active_room_blocks_account_deletion() =
        testApplication {
            val store = InMemoryOnlineServerStore(
                accountIdFactory = { "active-account" },
            )
            val account = requireNotNull(
                store.promoteAccount(
                    playerId = "active-player",
                ),
            )
            val createdRoom = store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = account.playerId,
                    playerName = "Ativo",
                ),
            )
            assertTrue(createdRoom.accepted)

            val tokenService = HmacOnlineSessionTokenService(
                signingSecret = ByteArray(32) { 9 },
                nowEpochMillis = { 1_000L },
            )
            val session = tokenService.issueAccountSession(
                playerId = account.playerId,
                accountId = account.accountId,
            )

            application {
                module(
                    store = store,
                    serverEnvironment = OnlineServerEnvironment.TEST,
                    sessionTokenService = tokenService,
                )
            }

            val deletion = client.delete(
                "/${OnlineRemoteRoutes.DELETE_ACCOUNT}",
            ) {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${session.accessToken}",
                )
            }

            assertEquals(HttpStatusCode.Conflict, deletion.status)
            assertTrue(
                store.isAccountIdentityActive(
                    accountId = account.accountId,
                    playerId = account.playerId,
                ),
            )
        }
}