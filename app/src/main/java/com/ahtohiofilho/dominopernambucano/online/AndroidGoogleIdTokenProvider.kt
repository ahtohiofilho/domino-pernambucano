package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

interface GoogleIdTokenProvider {
    suspend fun requestIdToken(): String
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

        val response = try {
            credentialManager.getCredential(
                context = activityContext,
                request = request,
            )
        } catch (error: GetCredentialCancellationException) {
            throw GoogleCredentialSelectionCancelledException(error)
        } catch (error: GetCredentialException) {
            throw GoogleCredentialUnavailableException(error)
        }

        val credential = response.credential as? CustomCredential
            ?: throw GoogleCredentialUnavailableException()

        if (
            credential.type !=
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw GoogleCredentialUnavailableException()
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (error: GoogleIdTokenParsingException) {
            throw GoogleCredentialUnavailableException(error)
        }

        return googleCredential.idToken
            .trim()
            .takeIf(String::isNotBlank)
            ?: throw GoogleCredentialUnavailableException()
    }
}
