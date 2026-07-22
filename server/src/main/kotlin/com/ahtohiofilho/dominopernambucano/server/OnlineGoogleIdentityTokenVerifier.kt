package com.ahtohiofilho.dominopernambucano.server

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val GOOGLE_WEB_CLIENT_ID_ENVIRONMENT_VARIABLE =
    "DOMINO_GOOGLE_WEB_CLIENT_ID"

private const val MAX_GOOGLE_ID_TOKEN_CHARACTERS = 16_384
private val ACCEPTED_GOOGLE_ISSUERS = setOf(
    "accounts.google.com",
    "https://accounts.google.com",
)

sealed interface OnlineGoogleIdentityVerificationResult {
    data class Verified(
        val subject: String,
    ) : OnlineGoogleIdentityVerificationResult

    data object Invalid : OnlineGoogleIdentityVerificationResult

    data object Unavailable : OnlineGoogleIdentityVerificationResult
}

fun interface OnlineGoogleIdentityTokenVerifier {
    suspend fun verify(
        idToken: String,
    ): OnlineGoogleIdentityVerificationResult
}

internal class GoogleApiOnlineIdentityTokenVerifier(
    audience: String,
) : OnlineGoogleIdentityTokenVerifier {
    private val verifier = GoogleIdTokenVerifier.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
    )
        .setAudience(
            listOf(
                audience.trim().also { normalizedAudience ->
                    require(normalizedAudience.isNotBlank()) {
                        "O client ID Google do servidor nao pode ser vazio."
                    }
                },
            ),
        )
        .build()

    override suspend fun verify(
        idToken: String,
    ): OnlineGoogleIdentityVerificationResult = withContext(Dispatchers.IO) {
        val normalizedToken = idToken.trim()
        if (
            normalizedToken.isBlank() ||
            normalizedToken.length > MAX_GOOGLE_ID_TOKEN_CHARACTERS
        ) {
            return@withContext OnlineGoogleIdentityVerificationResult.Invalid
        }

        val verifiedToken = try {
            verifier.verify(normalizedToken)
        } catch (_: IOException) {
            return@withContext OnlineGoogleIdentityVerificationResult.Unavailable
        } catch (_: Exception) {
            return@withContext OnlineGoogleIdentityVerificationResult.Invalid
        } ?: return@withContext OnlineGoogleIdentityVerificationResult.Invalid

        val issuer = verifiedToken.payload.issuer?.trim()
        val subject = verifiedToken.payload.subject?.trim()

        if (
            issuer !in ACCEPTED_GOOGLE_ISSUERS ||
            subject.isNullOrBlank()
        ) {
            return@withContext OnlineGoogleIdentityVerificationResult.Invalid
        }

        OnlineGoogleIdentityVerificationResult.Verified(
            subject = subject,
        )
    }
}

internal fun createDefaultOnlineGoogleIdentityTokenVerifier(
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): OnlineGoogleIdentityTokenVerifier {
    val audience = readEnvironmentVariable(
        GOOGLE_WEB_CLIENT_ID_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }

    return if (audience == null) {
        OnlineGoogleIdentityTokenVerifier {
            OnlineGoogleIdentityVerificationResult.Unavailable
        }
    } else {
        GoogleApiOnlineIdentityTokenVerifier(
            audience = audience,
        )
    }
}
