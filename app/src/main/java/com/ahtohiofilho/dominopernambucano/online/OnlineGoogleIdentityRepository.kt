package com.ahtohiofilho.dominopernambucano.online

class OnlineGoogleIdentityRepository(
    private val apiClient: RemoteOnlineApiClient,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
) {
    suspend fun connectGoogleIdentity(
        idToken: String,
    ): OnlineSessionCredential {
        val storedCredential = sessionCredentialRepository
            .getStoredCredentialOrNull()
        val usableCredential = sessionCredentialRepository
            .getValidCredentialOrNull()

        if (
            usableCredential?.sessionKind ==
            OnlineSessionKind.ANONYMOUS
        ) {
            return linkGoogleIdentity(idToken)
        }

        return try {
            recoverGoogleAccount(idToken)
        } catch (error: OnlineGoogleIdentityException) {
            if (
                storedCredential?.sessionKind == OnlineSessionKind.ACCOUNT ||
                error.reason !=
                OnlineGoogleIdentityFailureReason.ACCOUNT_NOT_FOUND
            ) {
                throw error
            }

            sessionCredentialRepository.getOrCreateUsableCredential(
                createAnonymousSession = {
                    apiClient.createAnonymousSession()
                },
                refreshAccountSession = {
                    apiClient.promoteAccount()
                },
            )

            linkGoogleIdentity(idToken)
        }
    }

    suspend fun linkGoogleIdentity(
        idToken: String,
    ): OnlineSessionCredential {
        val request = idToken.toGoogleIdentityRequest()

        return sessionCredentialRepository.promoteCurrentCredential {
                accessToken ->
            apiClient.linkGoogleIdentity(
                request = request,
                accessToken = accessToken,
            )
        }
    }

    suspend fun recoverGoogleAccount(
        idToken: String,
    ): OnlineSessionCredential {
        val request = idToken.toGoogleIdentityRequest()

        return sessionCredentialRepository.recoverAccountCredential {
            apiClient.recoverGoogleAccount(
                request = request,
            )
        }
    }
}

private fun String.toGoogleIdentityRequest():
    OnlineGoogleIdentityRequestDto {
    val normalizedToken = trim()
    require(normalizedToken.isNotBlank()) {
        "O ID token Google não pode estar vazio."
    }

    return OnlineGoogleIdentityRequestDto(
        idToken = normalizedToken,
    )
}
