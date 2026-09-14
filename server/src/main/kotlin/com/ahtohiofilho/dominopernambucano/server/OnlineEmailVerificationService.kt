package com.ahtohiofilho.dominopernambucano.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException

data class OnlineEmailVerificationPolicy(
    val codeLengthDigits: Int = 6,
    val codeTtlMillis: Long = 10L * 60L * 1_000L,
    val maxVerifyAttempts: Int = 5,
    val requestCooldownMillis: Long = 60L * 1_000L,
) {
    init {
        require(codeLengthDigits in 4..9)
        require(codeTtlMillis > 0L)
        require(maxVerifyAttempts > 0)
        require(requestCooldownMillis > 0L)
    }

    companion object {
        val Default = OnlineEmailVerificationPolicy()
    }
}

interface OnlineEmailVerificationCodeSender {
    suspend fun sendVerificationCode(
        email: String,
        code: String,
    )
}

class OnlineEmailVerificationDeliveryException(
    cause: Throwable,
) : RuntimeException(
    "Não foi possível entregar o código de verificação.",
    cause,
)

sealed interface OnlineEmailVerificationResult {
    data class Verified(
        val subject: String,
    ) : OnlineEmailVerificationResult

    data object Rejected : OnlineEmailVerificationResult
}

class OnlineEmailVerificationReservation internal constructor(
    val subject: String,
    internal val reservationId: Long,
)

/**
 * Núcleo efêmero da prova de titularidade por e-mail.
 *
 * Nenhum e-mail, código em claro ou token de sessão é persistido. O mapa de
 * desafios usa apenas o SHA-256 do e-mail canônico como chave e HMAC-SHA256 do
 * código como prova armazenada em memória.
 */
class OnlineEmailVerificationService(
    private val sender: OnlineEmailVerificationCodeSender,
    private val policy: OnlineEmailVerificationPolicy =
        OnlineEmailVerificationPolicy.Default,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    codeGenerator: (() -> String)? = null,
    hashSecret: ByteArray = createOnlineEmailVerificationHashSecret(),
) {
    private data class Challenge(
        val codeDigest: ByteArray,
        val expiresAtEpochMillis: Long,
        val failedAttempts: Int = 0,
        val reservationId: Long? = null,
    )

    private data class PendingDispatch(
        val canonicalEmail: String,
        val subject: String,
        val code: String,
        val challenge: Challenge,
        val nextRequestAtEpochMillis: Long,
    )

    private val lock = Any()
    private val secureRandom = SecureRandom()
    private val hashSecret = hashSecret.copyOf()
    private val codeGenerator = codeGenerator ?: {
        val bound = decimalBound(policy.codeLengthDigits)
        secureRandom.nextInt(bound)
            .toString()
            .padStart(policy.codeLengthDigits, '0')
    }
    private val challengesBySubject =
        mutableMapOf<String, Challenge>()
    private val nextRequestAtBySubject =
        mutableMapOf<String, Long>()
    private var nextReservationId = 1L

    init {
        require(this.hashSecret.size >= 32) {
            "O segredo de hash de verificação deve ter ao menos 32 bytes."
        }
    }

    suspend fun requestCode(
        rawEmail: String,
    ) {
        val canonicalEmail =
            canonicalizeOnlineEmailAddress(rawEmail) ?: return
        val subject = onlineEmailIdentitySubjectFromCanonical(
            canonicalEmail,
        )
        val now = nowEpochMillis()
        require(now >= 0L)

        val dispatch = synchronized(lock) {
            pruneExpiredState(now)

            val nextRequestAt =
                nextRequestAtBySubject[subject] ?: 0L
            if (now < nextRequestAt) {
                null
            } else {
                val code = codeGenerator()
                require(
                    code.length == policy.codeLengthDigits &&
                        code.all { character -> character.isDigit() }
                ) {
                    "O gerador deve produzir somente o código numérico configurado."
                }

                val challenge = Challenge(
                    codeDigest = digestCode(
                        subject = subject,
                        code = code,
                    ),
                    expiresAtEpochMillis =
                        now + policy.codeTtlMillis,
                )
                val nextAllowedAt =
                    now + policy.requestCooldownMillis

                challengesBySubject[subject] = challenge
                nextRequestAtBySubject[subject] = nextAllowedAt

                PendingDispatch(
                    canonicalEmail = canonicalEmail,
                    subject = subject,
                    code = code,
                    challenge = challenge,
                    nextRequestAtEpochMillis = nextAllowedAt,
                )
            }
        } ?: return

        try {
            sender.sendVerificationCode(
                email = dispatch.canonicalEmail,
                code = dispatch.code,
            )
        } catch (error: CancellationException) {
            rollbackDispatch(dispatch)
            throw error
        } catch (error: Exception) {
            rollbackDispatch(dispatch)
            throw OnlineEmailVerificationDeliveryException(error)
        }
    }

    fun verifyCode(
        rawEmail: String,
        rawCode: String,
    ): OnlineEmailVerificationResult {
        val canonicalEmail =
            canonicalizeOnlineEmailAddress(rawEmail)
                ?: return OnlineEmailVerificationResult.Rejected
        val code = rawCode.trim()
        if (
            code.length != policy.codeLengthDigits ||
            !code.all { character -> character.isDigit() }
        ) {
            return OnlineEmailVerificationResult.Rejected
        }

        val subject = onlineEmailIdentitySubjectFromCanonical(
            canonicalEmail,
        )
        val now = nowEpochMillis()
        require(now >= 0L)

        return synchronized(lock) {
            pruneExpiredState(now)

            val challenge = challengesBySubject[subject]
                ?: return@synchronized OnlineEmailVerificationResult.Rejected
            if (challenge.reservationId != null) {
                return@synchronized OnlineEmailVerificationResult.Rejected
            }

            val actualDigest = digestCode(
                subject = subject,
                code = code,
            )
            val matches = MessageDigest.isEqual(
                challenge.codeDigest,
                actualDigest,
            )

            if (!matches) {
                val failedAttempts = challenge.failedAttempts + 1
                if (failedAttempts >= policy.maxVerifyAttempts) {
                    challengesBySubject.remove(subject)
                } else {
                    challengesBySubject[subject] = challenge.copy(
                        failedAttempts = failedAttempts,
                    )
                }

                return@synchronized OnlineEmailVerificationResult.Rejected
            }

            challengesBySubject.remove(subject)
            OnlineEmailVerificationResult.Verified(
                subject = subject,
            )
        }
    }


    fun reserveCode(
        rawEmail: String,
        rawCode: String,
    ): OnlineEmailVerificationReservation? {
        val canonicalEmail =
            canonicalizeOnlineEmailAddress(rawEmail)
                ?: return null
        val code = rawCode.trim()
        if (
            code.length != policy.codeLengthDigits ||
            !code.all { character -> character.isDigit() }
        ) {
            return null
        }

        val subject = onlineEmailIdentitySubjectFromCanonical(
            canonicalEmail,
        )
        val now = nowEpochMillis()
        require(now >= 0L)

        return synchronized(lock) {
            pruneExpiredState(now)

            val challenge = challengesBySubject[subject]
                ?: return@synchronized null
            if (challenge.reservationId != null) {
                return@synchronized null
            }

            val actualDigest = digestCode(
                subject = subject,
                code = code,
            )
            val matches = MessageDigest.isEqual(
                challenge.codeDigest,
                actualDigest,
            )

            if (!matches) {
                val failedAttempts = challenge.failedAttempts + 1
                if (failedAttempts >= policy.maxVerifyAttempts) {
                    challengesBySubject.remove(subject)
                } else {
                    challengesBySubject[subject] = challenge.copy(
                        failedAttempts = failedAttempts,
                    )
                }
                return@synchronized null
            }

            val reservationId = nextReservationId
            nextReservationId =
                if (reservationId == Long.MAX_VALUE) 1L
                else reservationId + 1L

            challengesBySubject[subject] = challenge.copy(
                reservationId = reservationId,
            )
            OnlineEmailVerificationReservation(
                subject = subject,
                reservationId = reservationId,
            )
        }
    }

    fun completeReservation(
        reservation: OnlineEmailVerificationReservation,
        consume: Boolean,
    ): Boolean {
        val now = nowEpochMillis()
        require(now >= 0L)

        return synchronized(lock) {
            pruneExpiredState(now)

            val challenge = challengesBySubject[reservation.subject]
                ?: return@synchronized false
            if (challenge.reservationId != reservation.reservationId) {
                return@synchronized false
            }

            if (consume) {
                challengesBySubject.remove(reservation.subject)
            } else {
                challengesBySubject[reservation.subject] = challenge.copy(
                    reservationId = null,
                )
            }
            true
        }
    }
    private fun rollbackDispatch(
        dispatch: PendingDispatch,
    ) {
        synchronized(lock) {
            val currentChallenge =
                challengesBySubject[dispatch.subject]
            if (currentChallenge === dispatch.challenge) {
                challengesBySubject.remove(dispatch.subject)
            }

            if (
                nextRequestAtBySubject[dispatch.subject] ==
                dispatch.nextRequestAtEpochMillis
            ) {
                nextRequestAtBySubject.remove(dispatch.subject)
            }
        }
    }

    private fun pruneExpiredState(
        nowEpochMillis: Long,
    ) {
        val challengeIterator =
            challengesBySubject.entries.iterator()
        while (challengeIterator.hasNext()) {
            val entry = challengeIterator.next()
            if (entry.value.expiresAtEpochMillis <= nowEpochMillis) {
                challengeIterator.remove()
            }
        }

        val cooldownIterator =
            nextRequestAtBySubject.entries.iterator()
        while (cooldownIterator.hasNext()) {
            val entry = cooldownIterator.next()
            if (entry.value <= nowEpochMillis) {
                cooldownIterator.remove()
            }
        }
    }

    private fun digestCode(
        subject: String,
        code: String,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(
            SecretKeySpec(
                hashSecret,
                "HmacSHA256",
            ),
        )
        return mac.doFinal(
            "$subject:$code".toByteArray(
                StandardCharsets.UTF_8,
            ),
        )
    }
}

internal fun canonicalizeOnlineEmailAddress(
    rawEmail: String,
): String? {
    val email = rawEmail
        .trim()
        .lowercase(Locale.ROOT)

    if (
        email.length !in 3..254 ||
        email.any { character -> character.isWhitespace() }
    ) {
        return null
    }

    val atIndex = email.indexOf('@')
    if (
        atIndex <= 0 ||
        atIndex != email.lastIndexOf('@') ||
        atIndex >= email.lastIndex
    ) {
        return null
    }

    val localPart = email.substring(0, atIndex)
    val domainPart = email.substring(atIndex + 1)

    if (
        localPart.length > 64 ||
        domainPart.length > 253 ||
        localPart.startsWith('.') ||
        localPart.endsWith('.') ||
        domainPart.startsWith('.') ||
        domainPart.endsWith('.') ||
        localPart.contains("..") ||
        domainPart.contains("..")
    ) {
        return null
    }

    return email
}

internal fun onlineEmailIdentitySubject(
    rawEmail: String,
): String? {
    val canonicalEmail =
        canonicalizeOnlineEmailAddress(rawEmail) ?: return null
    return onlineEmailIdentitySubjectFromCanonical(
        canonicalEmail,
    )
}

private fun onlineEmailIdentitySubjectFromCanonical(
    canonicalEmail: String,
): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(
            canonicalEmail.toByteArray(
                StandardCharsets.UTF_8,
            ),
        )
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff)
                .toString(16)
                .padStart(2, '0')
        }
}

private fun createOnlineEmailVerificationHashSecret():
    ByteArray {
    return ByteArray(32).also { bytes ->
        SecureRandom().nextBytes(bytes)
    }
}

private fun decimalBound(
    digits: Int,
): Int {
    var bound = 1
    repeat(digits) {
        bound *= 10
    }
    return bound
}
