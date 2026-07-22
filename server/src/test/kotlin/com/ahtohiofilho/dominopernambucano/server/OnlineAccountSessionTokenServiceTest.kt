package com.ahtohiofilho.dominopernambucano.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OnlineAccountSessionTokenServiceTest {
    @Test
    fun issued_account_session_is_version2_and_preserves_player_id() {
        val service = HmacOnlineSessionTokenService(
            signingSecret = ByteArray(32) { index ->
                (index + 17).toByte()
            },
            nowEpochMillis = { 1_000L },
            sessionTtlMillis = 5_000L,
            sessionIdFactory = { "account-session-1" },
        )

        val session = service.issueAccountSession(
            playerId = "player-1",
            accountId = "account-1",
        )
        val identity = service.resolveAccessToken(session.accessToken)

        assertEquals("player-1", session.playerId)
        assertEquals("account-1", session.accountId)
        assertEquals(6_000L, session.expiresAtEpochMillis)
        assertNotNull(identity)
        assertEquals("player-1", identity?.playerId)
        assertEquals("account-1", identity?.principalId)
        assertEquals("account-session-1", identity?.sessionId)
        assertEquals(OnlinePrincipalKind.ACCOUNT, identity?.kind)
        assertEquals("account-1", identity?.accountId)
    }
}
