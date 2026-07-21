package com.ahtohiofilho.dominopernambucano.server

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineSessionTokenServiceTest {
    private val signingSecret = ByteArray(32) { index ->
        (index + 11).toByte()
    }
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun newly_issued_session_remains_legacy_and_resolves_abstract_principal() {
        val service = HmacOnlineSessionTokenService(
            signingSecret = signingSecret,
            nowEpochMillis = {
                1_000L
            },
            sessionTtlMillis = 5_000L,
            playerIdFactory = {
                "player-1"
            },
        )

        val session = service.issueAnonymousSession()
        val legacyPayload = json.decodeFromString<LegacyPayload>(
            decodedPayload(
                accessToken = session.accessToken,
            ),
        )
        val identity = service.resolveAccessToken(
            accessToken = session.accessToken,
        )

        assertEquals("player-1", legacyPayload.playerId)
        assertEquals(6_000L, legacyPayload.expiresAtEpochMillis)
        assertNotNull(identity)
        assertEquals("player-1", identity?.playerId)
        assertEquals(
            "legacy-principal:player-1",
            identity?.principalId,
        )
        assertNotEquals(
            identity?.principalId,
            identity?.sessionId,
        )
        assertEquals(OnlinePrincipalKind.ANONYMOUS, identity?.kind)
        assertNull(identity?.accountId)
    }

    @Test
    fun version2_emission_can_be_enabled_and_resolves_versioned_principal() {
        val service = HmacOnlineSessionTokenService(
            signingSecret = signingSecret,
            nowEpochMillis = {
                1_000L
            },
            sessionTtlMillis = 5_000L,
            emitVersion2Tokens = true,
            playerIdFactory = {
                "player-v2"
            },
            principalIdFactory = {
                "principal-v2"
            },
            sessionIdFactory = {
                "session-v2"
            },
        )

        val session = service.issueAnonymousSession()
        val identity = service.resolveAccessToken(
            accessToken = session.accessToken,
        )

        assertNotNull(identity)
        assertEquals("player-v2", identity?.playerId)
        assertEquals("principal-v2", identity?.principalId)
        assertEquals("session-v2", identity?.sessionId)
        assertEquals(OnlinePrincipalKind.ANONYMOUS, identity?.kind)
        assertNull(identity?.accountId)
    }

    @Test
    fun legacy_token_without_version_remains_accepted() {
        val service = HmacOnlineSessionTokenService(
            signingSecret = signingSecret,
            nowEpochMillis = {
                1_000L
            },
        )
        val token = signedToken(
            payload = json.encodeToString(
                LegacyPayload(
                    playerId = "legacy-player",
                    expiresAtEpochMillis = 5_000L,
                ),
            ),
        )

        val identity = service.resolveAccessToken(
            accessToken = token,
        )

        assertNotNull(identity)
        assertEquals("legacy-player", identity?.playerId)
        assertEquals(
            "legacy-principal:legacy-player",
            identity?.principalId,
        )
        assertNotEquals(
            identity?.principalId,
            identity?.sessionId,
        )
        assertEquals(OnlinePrincipalKind.ANONYMOUS, identity?.kind)
        assertNull(identity?.accountId)
    }

    @Test
    fun versioned_account_token_resolves_optional_account_id() {
        val service = HmacOnlineSessionTokenService(
            signingSecret = signingSecret,
            nowEpochMillis = {
                1_000L
            },
        )
        val token = signedToken(
            payload = version2Payload(
                principalKind = "ACCOUNT",
                accountId = "account-1",
            ),
        )

        val identity = service.resolveAccessToken(
            accessToken = token,
        )

        assertNotNull(identity)
        assertEquals("player-account", identity?.playerId)
        assertEquals("principal-account", identity?.principalId)
        assertEquals("session-account", identity?.sessionId)
        assertEquals(OnlinePrincipalKind.ACCOUNT, identity?.kind)
        assertEquals("account-1", identity?.accountId)
    }

    @Test
    fun unsupported_version_and_tampered_signature_are_rejected() {
        val service = HmacOnlineSessionTokenService(
            signingSecret = signingSecret,
            nowEpochMillis = {
                1_000L
            },
        )
        val unsupportedToken = signedToken(
            payload = buildJsonObject {
                put("tokenVersion", 99)
                put("principalId", "principal-unsupported")
                put("sessionId", "session-unsupported")
                put("principalKind", "ANONYMOUS")
                put("playerId", "player-unsupported")
                put("issuedAtEpochMillis", 500L)
                put("expiresAtEpochMillis", 5_000L)
            }.toString(),
        )
        val issuedToken = service.issueAnonymousSession().accessToken
        val issuedTokenParts = issuedToken.split('.', limit = 2)
        val signature = issuedTokenParts[1]
        val tamperedSignature =
            (if (signature.first() == 'A') "B" else "A") +
                signature.drop(1)
        val tamperedToken =
            "${issuedTokenParts[0]}.$tamperedSignature"

        assertNull(
            service.resolveAccessToken(
                accessToken = unsupportedToken,
            ),
        )
        assertNull(
            service.resolveAccessToken(
                accessToken = tamperedToken,
            ),
        )
    }

    @Test
    fun expired_legacy_and_version2_tokens_are_rejected() {
        val service = HmacOnlineSessionTokenService(
            signingSecret = signingSecret,
            nowEpochMillis = {
                5_000L
            },
        )
        val legacyToken = signedToken(
            payload = json.encodeToString(
                LegacyPayload(
                    playerId = "expired-legacy",
                    expiresAtEpochMillis = 5_000L,
                ),
            ),
        )
        val version2Token = signedToken(
            payload = version2Payload(
                principalKind = "ACCOUNT",
                accountId = "account-expired",
                expiresAtEpochMillis = 5_000L,
            ),
        )

        assertNull(
            service.resolveAccessToken(
                accessToken = legacyToken,
            ),
        )
        assertNull(
            service.resolveAccessToken(
                accessToken = version2Token,
            ),
        )
    }

    @Test
    fun invalid_kind_and_account_combinations_are_rejected() {
        val service = HmacOnlineSessionTokenService(
            signingSecret = signingSecret,
            nowEpochMillis = {
                1_000L
            },
        )
        val anonymousWithAccount = signedToken(
            payload = version2Payload(
                principalKind = "ANONYMOUS",
                accountId = "unexpected-account",
            ),
        )
        val accountWithoutAccountId = signedToken(
            payload = version2Payload(
                principalKind = "ACCOUNT",
                accountId = null,
            ),
        )

        assertNull(
            service.resolveAccessToken(
                accessToken = anonymousWithAccount,
            ),
        )
        assertNull(
            service.resolveAccessToken(
                accessToken = accountWithoutAccountId,
            ),
        )
    }

    private fun version2Payload(
        principalKind: String,
        accountId: String?,
        expiresAtEpochMillis: Long = 5_000L,
    ): String {
        return buildJsonObject {
            put("tokenVersion", 2)
            put("principalId", "principal-account")
            put("sessionId", "session-account")
            put("principalKind", principalKind)
            put("playerId", "player-account")
            if (accountId != null) {
                put("accountId", accountId)
            }
            put("issuedAtEpochMillis", 500L)
            put("expiresAtEpochMillis", expiresAtEpochMillis)
        }.toString()
    }

    private fun decodedPayload(
        accessToken: String,
    ): String {
        val encodedPayload = accessToken.split(
            '.',
            limit = 2,
        ).first()

        return String(
            Base64.getUrlDecoder().decode(encodedPayload),
            Charsets.UTF_8,
        )
    }

    private fun signedToken(
        payload: String,
    ): String {
        val encodedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                payload.toByteArray(Charsets.UTF_8),
            )
        val signature = Mac.getInstance("HmacSHA256")
            .apply {
                init(
                    SecretKeySpec(
                        signingSecret,
                        "HmacSHA256",
                    ),
                )
            }
            .doFinal(
                encodedPayload.toByteArray(Charsets.UTF_8),
            )
        val encodedSignature = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(signature)

        return "$encodedPayload.$encodedSignature"
    }

    @kotlinx.serialization.Serializable
    private data class LegacyPayload(
        val playerId: String,
        val expiresAtEpochMillis: Long,
    )
}
