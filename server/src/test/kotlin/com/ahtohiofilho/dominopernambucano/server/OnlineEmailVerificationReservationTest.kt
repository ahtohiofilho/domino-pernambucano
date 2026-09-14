package com.ahtohiofilho.dominopernambucano.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineEmailVerificationReservationTest {
    @Test
    fun released_reservation_keeps_code_retriable() = runBlocking {
        val service = service()
        service.requestCode("player@example.com")

        val first = service.reserveCode(
            rawEmail = "player@example.com",
            rawCode = "123456",
        )
        assertNotNull(first)

        assertNull(
            service.reserveCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
            ),
        )

        assertTrue(
            service.completeReservation(
                reservation = requireNotNull(first),
                consume = false,
            ),
        )

        assertNotNull(
            service.reserveCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
            ),
        )
    }

    @Test
    fun consumed_reservation_makes_code_single_use() = runBlocking {
        val service = service()
        service.requestCode("player@example.com")

        val reservation = requireNotNull(
            service.reserveCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
            ),
        )

        assertTrue(
            service.completeReservation(
                reservation = reservation,
                consume = true,
            ),
        )

        assertNull(
            service.reserveCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
            ),
        )
        assertEquals(
            OnlineEmailVerificationResult.Rejected,
            service.verifyCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
            ),
        )
    }

    @Test
    fun invalid_code_still_exhausts_attempt_limit() = runBlocking {
        val service = OnlineEmailVerificationService(
            sender = NoopSender,
            policy = OnlineEmailVerificationPolicy(
                codeLengthDigits = 6,
                codeTtlMillis = 10_000L,
                maxVerifyAttempts = 2,
                requestCooldownMillis = 60_000L,
            ),
            nowEpochMillis = { 1_000L },
            codeGenerator = { "123456" },
            hashSecret = ByteArray(32) { index ->
                (index + 7).toByte()
            },
        )
        service.requestCode("player@example.com")

        assertNull(
            service.reserveCode(
                rawEmail = "player@example.com",
                rawCode = "000000",
            ),
        )
        assertNull(
            service.reserveCode(
                rawEmail = "player@example.com",
                rawCode = "000000",
            ),
        )
        assertNull(
            service.reserveCode(
                rawEmail = "player@example.com",
                rawCode = "123456",
            ),
        )
    }

    private fun service() = OnlineEmailVerificationService(
        sender = NoopSender,
        nowEpochMillis = { 1_000L },
        codeGenerator = { "123456" },
        hashSecret = ByteArray(32) { index ->
            (index + 17).toByte()
        },
    )

    private data object NoopSender : OnlineEmailVerificationCodeSender {
        override suspend fun sendVerificationCode(
            email: String,
            code: String,
        ) = Unit
    }
}