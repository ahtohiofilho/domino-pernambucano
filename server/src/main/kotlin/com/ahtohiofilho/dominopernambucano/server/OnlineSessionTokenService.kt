package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val SESSION_SIGNING_SECRET_ENVIRONMENT_VARIABLE =
    "DOMINO_SESSION_SIGNING_SECRET"

private const val HMAC_SHA_256 = "HmacSHA256"
private const val SHA_256 = "SHA-256"
private const val MINIMUM_SIGNING_SECRET_BYTES = 32
private const val CURRENT_SESSION_TOKEN_VERSION = 2

internal const val DEFAULT_ANONYMOUS_SESSION_TTL_MILLIS =
    1000L * 60L * 60L * 24L * 30L

interface OnlineSessionTokenService {
    fun issueAnonymousSession(): OnlineAnonymousSessionDto

    fun issueAccountSession(
        playerId: String,
        accountId: String,
    ): OnlineAccountSessionDto

    fun resolveAccessToken(
        accessToken: String,
    ): OnlineRequestIdentity?
}

class HmacOnlineSessionTokenService(
    signingSecret: ByteArray,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val sessionTtlMillis: Long =
        DEFAULT_ANONYMOUS_SESSION_TTL_MILLIS,
    /*
     * Mantem a emissao legada por padrao ate existir uma janela de rollout
     * em que o binario anterior tambem reconheca tokens v2. O resolver ja
     * aceita os dois formatos, permitindo ativacao coordenada no futuro.
     */
    private val emitVersion2Tokens: Boolean = false,
    private val playerIdFactory: () -> String = {
        "anonymous-${UUID.randomUUID()}"
    },
    private val principalIdFactory: () -> String = {
        "principal-${UUID.randomUUID()}"
    },
    private val sessionIdFactory: () -> String = {
        "session-${UUID.randomUUID()}"
    },
    private val json: Json = Json {
        encodeDefaults = true
    },
) : OnlineSessionTokenService {
    private val secret = signingSecret.copyOf()

    init {
        require(secret.size >= MINIMUM_SIGNING_SECRET_BYTES) {
            "A chave de assinatura de sessão deve ter pelo menos " +
                "$MINIMUM_SIGNING_SECRET_BYTES bytes."
        }
        require(sessionTtlMillis > 0L) {
            "A validade da sessão deve ser positiva."
        }
    }

    override fun issueAnonymousSession(): OnlineAnonymousSessionDto {
        val now = nowEpochMillis()
        val playerId = requireIdentifier(
            value = playerIdFactory(),
            fieldName = "playerId",
        )
        val expiresAtEpochMillis = now + sessionTtlMillis

        val accessToken = if (emitVersion2Tokens) {
            val principalId = requireIdentifier(
                value = principalIdFactory(),
                fieldName = "principalId",
            )
            val sessionId = requireIdentifier(
                value = sessionIdFactory(),
                fieldName = "sessionId",
            )
            val payload = OnlineSessionTokenPayloadV2(
                tokenVersion = CURRENT_SESSION_TOKEN_VERSION,
                principalId = principalId,
                sessionId = sessionId,
                principalKind = OnlinePrincipalKind.ANONYMOUS,
                playerId = playerId,
                accountId = null,
                issuedAtEpochMillis = now,
                expiresAtEpochMillis = expiresAtEpochMillis,
            )

            encodeAndSign(
                payload = json.encodeToString(payload),
            )
        } else {
            val payload = OnlineSessionTokenPayloadV1(
                playerId = playerId,
                expiresAtEpochMillis = expiresAtEpochMillis,
            )

            encodeAndSign(
                payload = json.encodeToString(payload),
            )
        }

        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = accessToken,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }

    override fun issueAccountSession(
        playerId: String,
        accountId: String,
    ): OnlineAccountSessionDto {
        val normalizedPlayerId = requireIdentifier(
            value = playerId,
            fieldName = "playerId",
        )
        val normalizedAccountId = requireIdentifier(
            value = accountId,
            fieldName = "accountId",
        )
        val sessionId = requireIdentifier(
            value = sessionIdFactory(),
            fieldName = "sessionId",
        )
        val now = nowEpochMillis()
        val expiresAtEpochMillis = now + sessionTtlMillis
        val payload = OnlineSessionTokenPayloadV2(
            tokenVersion = CURRENT_SESSION_TOKEN_VERSION,
            principalId = normalizedAccountId,
            sessionId = sessionId,
            principalKind = OnlinePrincipalKind.ACCOUNT,
            playerId = normalizedPlayerId,
            accountId = normalizedAccountId,
            issuedAtEpochMillis = now,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )

        return OnlineAccountSessionDto(
            accountId = normalizedAccountId,
            playerId = normalizedPlayerId,
            accessToken = encodeAndSign(
                payload = json.encodeToString(payload),
            ),
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }

    override fun resolveAccessToken(
        accessToken: String,
    ): OnlineRequestIdentity? {
        val normalizedToken = accessToken.trim()
        val tokenParts = normalizedToken.split(
            '.',
            limit = 2,
        )

        if (
            tokenParts.size != 2 ||
            tokenParts.any { value -> value.isBlank() }
        ) {
            return null
        }

        val encodedPayload = tokenParts[0]
        val encodedSignature = tokenParts[1]

        val actualSignature = runCatching {
            Base64.getUrlDecoder().decode(encodedSignature)
        }.getOrNull() ?: return null

        val expectedSignature = sign(
            encodedPayload = encodedPayload,
        )

        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            return null
        }

        val decodedPayload = runCatching {
            String(
                Base64.getUrlDecoder().decode(encodedPayload),
                Charsets.UTF_8,
            )
        }.getOrNull() ?: return null

        val tokenVersion = runCatching {
            json.parseToJsonElement(decodedPayload)
                .jsonObject["tokenVersion"]
                ?.jsonPrimitive
                ?.intOrNull
        }.getOrNull()

        return when (tokenVersion) {
            null -> resolveLegacyPayload(
                decodedPayload = decodedPayload,
                accessToken = normalizedToken,
            )

            CURRENT_SESSION_TOKEN_VERSION -> resolveVersion2Payload(
                decodedPayload = decodedPayload,
            )

            else -> null
        }
    }

    private fun resolveLegacyPayload(
        decodedPayload: String,
        accessToken: String,
    ): OnlineRequestIdentity? {
        val payload = runCatching {
            json.decodeFromString<OnlineSessionTokenPayloadV1>(
                decodedPayload,
            )
        }.getOrNull() ?: return null

        val playerId = payload.playerId.trim()
        if (
            playerId.isBlank() ||
            nowEpochMillis() >= payload.expiresAtEpochMillis
        ) {
            return null
        }

        return OnlineRequestIdentity(
            playerId = playerId,
            principalId = "legacy-principal:$playerId",
            sessionId = legacySessionId(
                accessToken = accessToken,
            ),
            kind = OnlinePrincipalKind.ANONYMOUS,
            accountId = null,
        )
    }

    private fun resolveVersion2Payload(
        decodedPayload: String,
    ): OnlineRequestIdentity? {
        val payload = runCatching {
            json.decodeFromString<OnlineSessionTokenPayloadV2>(
                decodedPayload,
            )
        }.getOrNull() ?: return null

        val principalId = payload.principalId.trim()
        val sessionId = payload.sessionId.trim()
        val playerId = payload.playerId.trim()
        val accountId = payload.accountId?.trim()
        val now = nowEpochMillis()

        if (
            payload.tokenVersion != CURRENT_SESSION_TOKEN_VERSION ||
            principalId.isBlank() ||
            sessionId.isBlank() ||
            playerId.isBlank() ||
            payload.issuedAtEpochMillis > payload.expiresAtEpochMillis ||
            now >= payload.expiresAtEpochMillis
        ) {
            return null
        }

        val normalizedAccountId = when (payload.principalKind) {
            OnlinePrincipalKind.ANONYMOUS -> {
                if (accountId != null) {
                    return null
                }
                null
            }

            OnlinePrincipalKind.ACCOUNT -> {
                accountId?.takeIf { value ->
                    value.isNotBlank()
                } ?: return null
            }
        }

        return OnlineRequestIdentity(
            playerId = playerId,
            principalId = principalId,
            sessionId = sessionId,
            kind = payload.principalKind,
            accountId = normalizedAccountId,
        )
    }

    private fun encodeAndSign(
        payload: String,
    ): String {
        val encodedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                payload.toByteArray(Charsets.UTF_8),
            )
        val encodedSignature = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                sign(
                    encodedPayload = encodedPayload,
                ),
            )

        return "$encodedPayload.$encodedSignature"
    }

    private fun sign(
        encodedPayload: String,
    ): ByteArray {
        return Mac.getInstance(HMAC_SHA_256)
            .apply {
                init(
                    SecretKeySpec(
                        secret,
                        HMAC_SHA_256,
                    ),
                )
            }
            .doFinal(
                encodedPayload.toByteArray(Charsets.UTF_8),
            )
    }

    private fun legacySessionId(
        accessToken: String,
    ): String {
        val digest = MessageDigest.getInstance(SHA_256)
            .digest(
                accessToken.toByteArray(Charsets.UTF_8),
            )
        val encodedDigest = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)

        return "legacy-session:$encodedDigest"
    }

    private fun requireIdentifier(
        value: String,
        fieldName: String,
    ): String {
        return value.trim().also { normalizedValue ->
            require(normalizedValue.isNotBlank()) {
                "O $fieldName emitido para a sessão não pode ser vazio."
            }
        }
    }
}

internal fun createDefaultOnlineSessionTokenService(
    serverEnvironment: OnlineServerEnvironment,
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): OnlineSessionTokenService {
    val configuredSecret = readEnvironmentVariable(
        SESSION_SIGNING_SECRET_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.takeIf { value ->
            value.isNotBlank()
        }

    val secret = when {
        configuredSecret != null -> {
            configuredSecret.toByteArray(Charsets.UTF_8)
        }

        serverEnvironment == OnlineServerEnvironment.PRODUCTION -> {
            throw IllegalStateException(
                "$SESSION_SIGNING_SECRET_ENVIRONMENT_VARIABLE deve ser " +
                        "configurada no ambiente de producao.",
            )
        }

        else -> ByteArray(MINIMUM_SIGNING_SECRET_BYTES).also { bytes ->
            SecureRandom().nextBytes(bytes)
        }
    }

    require(secret.size >= MINIMUM_SIGNING_SECRET_BYTES) {
        "$SESSION_SIGNING_SECRET_ENVIRONMENT_VARIABLE deve ter pelo menos " +
            "$MINIMUM_SIGNING_SECRET_BYTES bytes."
    }

    return HmacOnlineSessionTokenService(
        signingSecret = secret,
    )
}

@Serializable
private data class OnlineSessionTokenPayloadV1(
    val playerId: String,
    val expiresAtEpochMillis: Long,
)

@Serializable
private data class OnlineSessionTokenPayloadV2(
    val tokenVersion: Int,
    val principalId: String,
    val sessionId: String,
    val principalKind: OnlinePrincipalKind,
    val playerId: String,
    val accountId: String? = null,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)
