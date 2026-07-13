package com.ahtohiofilho.dominopernambucano.server

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

private const val SESSION_SIGNING_SECRET_ENVIRONMENT_VARIABLE =
    "DOMINO_SESSION_SIGNING_SECRET"

private const val HMAC_SHA_256 = "HmacSHA256"
private const val MINIMUM_SIGNING_SECRET_BYTES = 32

internal const val DEFAULT_ANONYMOUS_SESSION_TTL_MILLIS =
    1000L * 60L * 60L * 24L * 30L

interface OnlineSessionTokenService {
    fun issueAnonymousSession(): OnlineAnonymousSessionDto

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
    private val playerIdFactory: () -> String = {
        "anonymous-${UUID.randomUUID()}"
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
        val playerId = playerIdFactory()
            .trim()
            .also { value ->
                require(value.isNotBlank()) {
                    "O playerId emitido para a sessão não pode ser vazio."
                }
            }

        val expiresAtEpochMillis = now + sessionTtlMillis
        val payload = OnlineSessionTokenPayload(
            playerId = playerId,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )

        val encodedPayload = Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                json.encodeToString(payload).toByteArray(Charsets.UTF_8),
            )

        val encodedSignature = Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                sign(
                    encodedPayload = encodedPayload,
                ),
            )

        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = "$encodedPayload.$encodedSignature",
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }

    override fun resolveAccessToken(
        accessToken: String,
    ): OnlineRequestIdentity? {
        val tokenParts = accessToken
            .trim()
            .split(
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

        val payload = runCatching {
            val decodedPayload = String(
                Base64
                    .getUrlDecoder()
                    .decode(encodedPayload),
                Charsets.UTF_8,
            )

            json.decodeFromString<OnlineSessionTokenPayload>(
                decodedPayload,
            )
        }.getOrNull() ?: return null

        if (
            payload.playerId.isBlank() ||
            nowEpochMillis() >= payload.expiresAtEpochMillis
        ) {
            return null
        }

        return OnlineRequestIdentity(
            playerId = payload.playerId,
        )
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
private data class OnlineSessionTokenPayload(
    val playerId: String,
    val expiresAtEpochMillis: Long,
)
