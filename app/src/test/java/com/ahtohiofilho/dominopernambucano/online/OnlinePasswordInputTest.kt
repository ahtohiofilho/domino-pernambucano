package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlinePasswordInputTest {
    @Test
    fun accepts_password_inside_supported_bounds_without_rewriting_it() {
        val password = "  eight+chars  "

        assertEquals(
            password,
            normalizeOnlinePasswordOrNull(password),
        )
    }

    @Test
    fun rejects_short_and_nul_passwords() {
        assertNull(normalizeOnlinePasswordOrNull("1234567"))
        assertNull(normalizeOnlinePasswordOrNull("abcdefgh\u0000"))
    }
}
