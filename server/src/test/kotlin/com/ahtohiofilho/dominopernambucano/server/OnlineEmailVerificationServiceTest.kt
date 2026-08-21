package com.ahtohiofilho.dominopernambucano.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnlineEmailVerificationServiceTest {
    @Test
    fun request_normalizes_email_and_enforces_cooldown() = runBlocking {
        var now = 1_000L
        val codes = ArrayDeque(
            listOf(
                "123456",
                "654321",
            ),
        )
        val sender = RecordingSender()
        val service = service(
            sender = sender,
            nowEpochMillis = { now },
            codeGenerator = { codes.removeFirst() },
        )

        service.requestCode("  User@Example.COM  ")
        service.requestCode("user@example.com")

        assertEquals(
            listOf(
                "user@example.com" to "123456",
            ),
            sender.messages,
        )

        now += 60_000L
        service.requestCode("USER@example.com")

        assertEquals(
            listOf(
                "user@example.com" to "123456",
                "user@example.com" to "654321",
            ),
            sender.messages,
        )
    }

    @Test
    fun verification_is_single_use_and_returns_hashed_subject() =
        runBlocking {
            val sender = RecordingSender()
            val service = service(
                sender = sender,
                codeGenerator = { "123456" },
            )

            service.requestCode("Player@Example.com")

            val result = service.verifyCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
            )

            assertTrue(
                result is OnlineEmailVerificationResult.Verified,
            )
            val verified =
                result as OnlineEmailVerificationResult.Verified
            assertEquals(
                onlineEmailIdentitySubject("PLAYER@example.com"),
                verified.subject,
            )
            assertNotEquals(
                "player@example.com",
                verified.subject,
            )
            assertEquals(64, verified.subject.length)
            assertEquals(
                OnlineEmailVerificationResult.Rejected,
                service.verifyCode(
                    rawEmail = "player@example.com",
                    rawCode = "123456",
                ),
            )
        }

    @Test
    fun expiration_and_attempt_limit_fail_closed() = runBlocking {
        var now = 1_000L
        val codes = ArrayDeque(
            listOf(
                "123456",
                "654321",
            ),
        )
        val sender = RecordingSender()
        val service = OnlineEmailVerificationService(
            sender = sender,
            policy = OnlineEmailVerificationPolicy(
                codeLengthDigits = 6,
                codeTtlMillis = 1_000L,
                maxVerifyAttempts = 3,
                requestCooldownMillis = 100L,
            ),
            nowEpochMillis = { now },
            codeGenerator = { codes.removeFirst() },
            hashSecret = ByteArray(32) { index ->
                (index + 1).toByte()
            },
        )

        service.requestCode("player@example.com")

        repeat(3) {
            assertEquals(
                OnlineEmailVerificationResult.Rejected,
                service.verifyCode(
                    rawEmail = "player@example.com",
                    rawCode = "000000",
                ),
            )
        }
        assertEquals(
            OnlineEmailVerificationResult.Rejected,
            service.verifyCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
            ),
        )

        now += 100L
        service.requestCode("player@example.com")
        now += 1_000L

        assertEquals(
            OnlineEmailVerificationResult.Rejected,
            service.verifyCode(
                rawEmail = "player@example.com",
                rawCode = "654321",
            ),
        )
    }

    @Test
    fun delivery_failure_rolls_back_challenge_and_cooldown() = runBlocking {
        val sender = RecordingSender(
            failNext = true,
        )
        val service = service(
            sender = sender,
            codeGenerator = { "123456" },
        )

        try {
            service.requestCode("player@example.com")
            fail("A falha de entrega deveria ser propagada.")
        } catch (_: OnlineEmailVerificationDeliveryException) {
            // Esperado.
        }

        sender.failNext = false
        service.requestCode("player@example.com")

        assertEquals(
            listOf(
                "player@example.com" to "123456",
            ),
            sender.messages,
        )
    }

    @Test
    fun configured_email_routes_are_allowed_only_in_controlled_environments() {
        assertFalse(
            OnlineServerEnvironment.DEVELOPMENT
                .allowsConfiguredEmailIdentityRoutes,
        )
        assertTrue(
            OnlineServerEnvironment.HOMOLOGATION
                .allowsConfiguredEmailIdentityRoutes,
        )
        assertTrue(
            OnlineServerEnvironment.MINIPRODUCTION
                .allowsConfiguredEmailIdentityRoutes,
        )
        assertTrue(
            OnlineServerEnvironment.TEST
                .allowsConfiguredEmailIdentityRoutes,
        )
        assertTrue(
            OnlineServerEnvironment.PRODUCTION
                .allowsConfiguredEmailIdentityRoutes,
        )
    }

    private fun service(
        sender: RecordingSender,
        nowEpochMillis: () -> Long = { 1_000L },
        codeGenerator: () -> String,
    ) = OnlineEmailVerificationService(
        sender = sender,
        nowEpochMillis = nowEpochMillis,
        codeGenerator = codeGenerator,
        hashSecret = ByteArray(32) { index ->
            (index + 17).toByte()
        },
    )

    private class RecordingSender(
        var failNext: Boolean = false,
    ) : OnlineEmailVerificationCodeSender {
        val messages =
            mutableListOf<Pair<String, String>>()

        override suspend fun sendVerificationCode(
            email: String,
            code: String,
        ) {
            if (failNext) {
                failNext = false
                throw IllegalStateException(
                    "delivery unavailable",
                )
            }
            messages += email to code
        }
    }
}
