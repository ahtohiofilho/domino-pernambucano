package com.ahtohiofilho.dominopernambucano.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalPolicyRoutesTest {
    @Test
    fun privacy_policy_is_public_and_identifies_domino_pe() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = { 1_000L },
                    ),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                )
            }

            val response = client.get("/privacy")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Política de Privacidade"))
            assertTrue(body.contains("Dominó PE"))
            assertTrue(body.contains("Google Mobile Ads"))
            assertTrue(body.contains("/account-deletion"))
            assertTrue(body.contains("mailto:"))
            assertFalse(body.contains("__SUPPORT_EMAIL__"))
        }

    @Test
    fun account_deletion_resource_is_public_and_prominent() =
        testApplication {
            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = { 1_000L },
                    ),
                    serverEnvironment = OnlineServerEnvironment.TEST,
                )
            }

            val response = client.get("/account-deletion")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Exclusão de conta e dados"))
            assertTrue(body.contains("Dominó PE"))
            assertTrue(body.contains("Solicitar exclusão por e-mail"))
            assertTrue(body.contains("mailto:"))
            assertTrue(body.contains("/privacy"))
            assertFalse(body.contains("__SUPPORT_EMAIL__"))
        }
}