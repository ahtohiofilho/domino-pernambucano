package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.ONLINE_PASSWORD_MAX_LENGTH
import com.ahtohiofilho.dominopernambucano.online.ONLINE_PASSWORD_MIN_LENGTH
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal const val ONLINE_PASSWORD_ALGORITHM = "PBKDF2WithHmacSHA256"
private const val DEFAULT_PASSWORD_ITERATIONS = 310_000
private const val PASSWORD_SALT_BYTES = 16
private const val PASSWORD_HASH_BITS = 256

class OnlinePasswordCredentialService(
    private val iterations: Int = DEFAULT_PASSWORD_ITERATIONS,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val dummySalt = ByteArray(PASSWORD_SALT_BYTES).also {
        secureRandom.nextBytes(it)
    }

    init {
        require(iterations >= 10_000)
    }

    fun isPasswordAccepted(rawPassword: String): Boolean {
        return rawPassword.length in
            ONLINE_PASSWORD_MIN_LENGTH..ONLINE_PASSWORD_MAX_LENGTH &&
            rawPassword.none { character -> character == '\u0000' }
    }

    fun createCredential(rawPassword: String): OnlineServerPasswordCredential {
        require(isPasswordAccepted(rawPassword)) {
            "A senha não atende à política mínima."
        }

        val salt = ByteArray(PASSWORD_SALT_BYTES).also {
            secureRandom.nextBytes(it)
        }
        val digest = derive(
            rawPassword = rawPassword,
            salt = salt,
            iterations = iterations,
        )

        return OnlineServerPasswordCredential(
            algorithm = ONLINE_PASSWORD_ALGORITHM,
            iterations = iterations,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(digest),
            updatedAtEpochMillis = nowEpochMillis(),
        )
    }

    fun verifyPassword(
        rawPassword: String,
        credential: OnlineServerPasswordCredential?,
    ): Boolean {
        if (!isPasswordAccepted(rawPassword)) {
            return false
        }

        val usableCredential = credential?.takeIf(
            ::isValidOnlineServerPasswordCredential,
        )

        val salt = usableCredential?.let { value ->
            runCatching {
                Base64.getDecoder().decode(value.saltBase64)
            }.getOrNull()
        } ?: dummySalt
        val iterationsToUse = usableCredential?.iterations ?: iterations
        val actual = derive(
            rawPassword = rawPassword,
            salt = salt,
            iterations = iterationsToUse,
        )
        val expected = usableCredential?.let { value ->
            runCatching {
                Base64.getDecoder().decode(value.hashBase64)
            }.getOrNull()
        }

        return expected != null &&
            MessageDigest.isEqual(expected, actual)
    }

    private fun derive(
        rawPassword: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val chars = rawPassword.toCharArray()
        val spec = PBEKeySpec(
            chars,
            salt,
            iterations,
            PASSWORD_HASH_BITS,
        )
        return try {
            SecretKeyFactory.getInstance(ONLINE_PASSWORD_ALGORITHM)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}

internal fun isValidOnlineServerPasswordCredential(
    credential: OnlineServerPasswordCredential,
): Boolean {
    if (
        credential.algorithm != ONLINE_PASSWORD_ALGORITHM ||
        credential.iterations < 10_000 ||
        credential.updatedAtEpochMillis < 0L
    ) {
        return false
    }

    val salt = runCatching {
        Base64.getDecoder().decode(credential.saltBase64)
    }.getOrNull() ?: return false
    val hash = runCatching {
        Base64.getDecoder().decode(credential.hashBase64)
    }.getOrNull() ?: return false

    return salt.size >= PASSWORD_SALT_BYTES &&
        hash.size == PASSWORD_HASH_BITS / 8
}
