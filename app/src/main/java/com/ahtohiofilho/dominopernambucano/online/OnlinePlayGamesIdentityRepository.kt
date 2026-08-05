package com.ahtohiofilho.dominopernambucano.online

class OnlinePlayGamesIdentityRepository(
    private val apiClient: RemoteOnlineApiClient,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
) {
    suspend fun connectPlayGamesIdentity(
        requestServerAuthCode: suspend () -> String,
    ): OnlineSessionCredential {
        val storedCredential = sessionCredentialRepository
            .getStoredCredentialOrNull()
        val usableCredential = sessionCredentialRepository
            .getValidCredentialOrNull()

        if (
            usableCredential?.sessionKind ==
            OnlineSessionKind.ANONYMOUS
        ) {
            return linkPlayGamesIdentity(
                serverAuthCode = requestServerAuthCode(),
            )
        }

        return try {
            recoverPlayGamesAccount(
                serverAuthCode = requestServerAuthCode(),
            )
        } catch (error: OnlinePlayGamesIdentityException) {
            if (
                storedCredential?.sessionKind ==
                    OnlineSessionKind.ACCOUNT ||
                error.reason !=
                    OnlinePlayGamesIdentityFailureReason.ACCOUNT_NOT_FOUND
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

            linkPlayGamesIdentity(
                serverAuthCode = requestServerAuthCode(),
            )
        }
    }

    suspend fun linkPlayGamesIdentity(
        serverAuthCode: String,
    ): OnlineSessionCredential {
        val request = serverAuthCode.toPlayGamesIdentityRequest()

        return sessionCredentialRepository.promoteCurrentCredential {
                accessToken ->
            apiClient.linkPlayGamesIdentity(
                request = request,
                accessToken = accessToken,
            )
        }
    }

    suspend fun recoverPlayGamesAccount(
        serverAuthCode: String,
    ): OnlineSessionCredential {
        val request = serverAuthCode.toPlayGamesIdentityRequest()

        return sessionCredentialRepository.recoverAccountCredential {
            apiClient.recoverPlayGamesAccount(
                request = request,
            )
        }
    }
}

private fun String.toPlayGamesIdentityRequest():
    OnlinePlayGamesIdentityRequestDto {
    val normalizedCode = trim()
    require(normalizedCode.isNotBlank()) {
        "O código de autorização Play Games não pode estar vazio."
    }

    return OnlinePlayGamesIdentityRequestDto(
        serverAuthCode = normalizedCode,
    )
}
