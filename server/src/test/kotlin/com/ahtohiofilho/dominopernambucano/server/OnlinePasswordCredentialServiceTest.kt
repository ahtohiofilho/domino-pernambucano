package com.ahtohiofilho.dominopernambucano.server

import java.security.SecureRandom
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePasswordCredentialServiceTest {
    @Test
    fun created_credential_verifies_only_the_original_password() {
        val service = service()
        val credential = service.createCredential("correct-horse")

        assertTrue(
            service.verifyPassword(
                rawPassword = "correct-horse",
                credential = credential,
            ),
        )
        assertFalse(
            service.verifyPassword(
                rawPassword = "wrong-password",
                credential = credential,
            ),
        )
    }

    @Test
    fun repeated_passwords_receive_independent_salts() {
        val service = service()

        val first = service.createCredential("same-password")
        val second = service.createCredential("same-password")

        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.hashBase64, second.hashBase64)
        assertTrue(isValidOnlineServerPasswordCredential(first))
        assertTrue(isValidOnlineServerPasswordCredential(second))
    }

    @Test
    fun missing_credential_never_authenticates() {
        val service = service()

        assertFalse(
            service.verifyPassword(
                rawPassword = "valid-length-password",
                credential = null,
            ),
        )
    }

    private fun service(): OnlinePasswordCredentialService {
        return OnlinePasswordCredentialService(
            iterations = 10_000,
            nowEpochMillis = { 123_456L },
            secureRandom = SecureRandom(),
        )
    }
}
