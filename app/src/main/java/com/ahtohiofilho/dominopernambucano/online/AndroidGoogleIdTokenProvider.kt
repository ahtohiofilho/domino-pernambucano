package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

interface GoogleIdTokenProvider {
    suspend fun requestIdToken(): String

    suspend fun requestAuthorizedIdTokenOrNull(): String? = null
}

class GoogleCredentialSelectionCancelledException(
    cause: Throwable? = null,
) : IllegalStateException("A seleção da conta Google foi cancelada.", cause)

class GoogleCredentialUnavailableException(
    cause: Throwable? = null,
) : IllegalStateException(
    "Não foi possível obter uma credencial Google neste dispositivo.",
    cause,
)

class AndroidGoogleIdTokenProvider(
    private val activityContext: Context,
    private val config: GoogleSignInConfig,
    private val credentialManager: CredentialManager =
        CredentialManager.create(activityContext),
) : GoogleIdTokenProvider {
    override suspend fun requestIdToken(): String {
        check(config.isConfigured) {
            "O login Google não está configurado neste build."
        }

        val googleOption = GetSignInWithGoogleOption.Builder(
            serverClientId = config.webClientId,
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        return requestGoogleIdToken(
            request = request,
            noCredentialIsError = true,
        ) ?: throw GoogleCredentialUnavailableException()
    }

    override suspend fun requestAuthorizedIdTokenOrNull(): String? {
        if (!config.isConfigured) {
            return null
        }

        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(config.webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        return requestGoogleIdToken(
            request = request,
            noCredentialIsError = false,
        )
    }

    private suspend fun requestGoogleIdToken(
        request: GetCredentialRequest,
        noCredentialIsError: Boolean,
    ): String? {
        val response = try {
            credentialManager.getCredential(
                context = activityContext,
                request = request,
            )
        } catch (error: GetCredentialCancellationException) {
            if (noCredentialIsError) {
                throw GoogleCredentialSelectionCancelledException(error)
            }
            return null
        } catch (error: NoCredentialException) {
            if (noCredentialIsError) {
                throw GoogleCredentialUnavailableException(error)
            }
            return null
        } catch (error: GetCredentialException) {
            if (noCredentialIsError) {
                throw GoogleCredentialUnavailableException(error)
            }
            return null
        }

        val credential = response.credential as? CustomCredential
            ?: return if (noCredentialIsError) {
                throw GoogleCredentialUnavailableException()
            } else {
                null
            }

        if (
            credential.type !=
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return if (noCredentialIsError) {
                throw GoogleCredentialUnavailableException()
            } else {
                null
            }
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (error: GoogleIdTokenParsingException) {
            return if (noCredentialIsError) {
                throw GoogleCredentialUnavailableException(error)
            } else {
                null
            }
        }

        return googleCredential.idToken
            .trim()
            .takeIf(String::isNotBlank)
            ?: if (noCredentialIsError) {
                throw GoogleCredentialUnavailableException()
            } else {
                null
            }
    }
}
