package com.ahtohiofilho.dominopernambucano.server

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val GOOGLE_WEB_CLIENT_ID_ENVIRONMENT_VARIABLE =
    "DOMINO_GOOGLE_WEB_CLIENT_ID"

internal const val GOOGLE_WEB_CLIENT_IDS_ENVIRONMENT_VARIABLE =
    "DOMINO_GOOGLE_WEB_CLIENT_IDS"

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

internal fun readConfiguredGoogleWebClientIds(
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): List<String> {
    val legacyClientId = readEnvironmentVariable(
        GOOGLE_WEB_CLIENT_ID_ENVIRONMENT_VARIABLE,
    )

    val additionalClientIds = readEnvironmentVariable(
        GOOGLE_WEB_CLIENT_IDS_ENVIRONMENT_VARIABLE,
    )
        ?.split(',')
        .orEmpty()

    return buildList {
        add(legacyClientId.orEmpty())
        addAll(additionalClientIds)
    }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}

internal class GoogleApiOnlineIdentityTokenVerifier(
    audiences: Collection<String>,
) : OnlineGoogleIdentityTokenVerifier {
    constructor(audience: String) : this(
        audiences = listOf(audience),
    )

    private val normalizedAudiences = audiences
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .also { configuredAudiences ->
            require(configuredAudiences.isNotEmpty()) {
                "Ao menos um client ID Google do servidor deve ser configurado."
            }
        }

    private val verifier = GoogleIdTokenVerifier.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
    )
        .setAudience(normalizedAudiences)
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
    val audiences = readConfiguredGoogleWebClientIds(
        readEnvironmentVariable = readEnvironmentVariable,
    )

    return if (audiences.isEmpty()) {
        OnlineGoogleIdentityTokenVerifier {
            OnlineGoogleIdentityVerificationResult.Unavailable
        }
    } else {
        GoogleApiOnlineIdentityTokenVerifier(
            audiences = audiences,
        )
    }
}
