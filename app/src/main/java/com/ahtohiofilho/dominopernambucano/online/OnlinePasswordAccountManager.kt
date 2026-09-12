package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.CancellationException

enum class OnlinePasswordVerificationPurpose {
    CREATE_ACCOUNT,
    RESET_PASSWORD,
}

enum class OnlinePasswordAccountFailureReason {
    INVALID_EMAIL,
    INVALID_PASSWORD,
    INVALID_CODE,
    INVALID_CREDENTIALS,
    ACCOUNT_EXISTS,
    ACCOUNT_NOT_FOUND,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    SESSION_CONFLICT,
    SESSION_EXPIRED,
    LOCAL_PERSISTENCE,
    UNKNOWN,
}

sealed interface OnlinePasswordAccountActionResult {
    data object Success : OnlinePasswordAccountActionResult

    data class CodeRequested(
        val email: String,
        val purpose: OnlinePasswordVerificationPurpose,
    ) : OnlinePasswordAccountActionResult

    data class Failure(
        val reason: OnlinePasswordAccountFailureReason,
    ) : OnlinePasswordAccountActionResult
}

class OnlinePasswordAccountManager(
    private val available: Boolean,
    private val apiClient: RemoteOnlineApiClient?,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
    private val onlineParticipationBindingRepository:
        OnlineParticipationBindingRepository? = null,
) {
    val isAvailable: Boolean
        get() = available && apiClient != null

    suspend fun requestVerificationCode(
        rawEmail: String,
        purpose: OnlinePasswordVerificationPurpose,
    ): OnlinePasswordAccountActionResult {
        val client = apiClient
            ?: return unavailableFailure()
        if (!available) {
            return unavailableFailure()
        }

        val email = normalizeOnlineEmailAddressOrNull(rawEmail)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_EMAIL,
            )

        return try {
            val response = client.requestEmailCode(
                OnlineEmailCodeRequestDto(email = email),
            )
            if (!response.accepted) {
                failure(OnlinePasswordAccountFailureReason.UNKNOWN)
            } else {
                OnlinePasswordAccountActionResult.CodeRequested(
                    email = email,
                    purpose = purpose,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: OnlineEmailIdentityException) {
            val reason = if (
                error.reason ==
                OnlineEmailIdentityFailureReason.ACCOUNT_NOT_FOUND
            ) {
                OnlinePasswordAccountFailureReason.SERVICE_UNAVAILABLE
            } else {
                error.reason.toPasswordAccountFailure()
            }
            failure(reason)
        } catch (_: Throwable) {
            failure(OnlinePasswordAccountFailureReason.UNKNOWN)
        }
    }

    suspend fun signIn(
        rawEmail: String,
        rawPassword: String,
    ): OnlinePasswordAccountActionResult {
        val client = apiClient
            ?: return unavailableFailure()
        if (!available) {
            return unavailableFailure()
        }

        val email = normalizeOnlineEmailAddressOrNull(rawEmail)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_EMAIL,
            )
        val password = normalizeOnlinePasswordOrNull(rawPassword)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_PASSWORD,
            )

        return try {
            prepareForAccountRecovery()
            sessionCredentialRepository.recoverAccountCredential {
                client.loginEmailPassword(
                    OnlinePasswordLoginRequestDto(
                        email = email,
                        password = password,
                    ),
                )
            }
            OnlinePasswordAccountActionResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (_: OnlineAccountRecoveryBlockedByAnonymousSessionException) {
            failure(OnlinePasswordAccountFailureReason.SESSION_CONFLICT)
        } catch (_: OnlineSessionCredentialPersistenceException) {
            failure(OnlinePasswordAccountFailureReason.LOCAL_PERSISTENCE)
        } catch (error: OnlinePasswordIdentityException) {
            val reason = when (error.reason) {
                OnlinePasswordIdentityFailureReason.UNAUTHORIZED,
                OnlinePasswordIdentityFailureReason.ACCOUNT_NOT_FOUND ->
                    OnlinePasswordAccountFailureReason.INVALID_CREDENTIALS

                else -> error.reason.toPasswordAccountFailure()
            }
            failure(reason)
        } catch (_: IllegalArgumentException) {
            failure(OnlinePasswordAccountFailureReason.SESSION_CONFLICT)
        } catch (_: Throwable) {
            failure(OnlinePasswordAccountFailureReason.UNKNOWN)
        }
    }

    suspend fun createAccount(
        rawEmail: String,
        rawPassword: String,
        rawCode: String,
    ): OnlinePasswordAccountActionResult {
        val client = apiClient
            ?: return unavailableFailure()
        if (!available) {
            return unavailableFailure()
        }

        val email = normalizeOnlineEmailAddressOrNull(rawEmail)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_EMAIL,
            )
        val password = normalizeOnlinePasswordOrNull(rawPassword)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_PASSWORD,
            )
        val code = normalizeOnlineEmailCodeOrNull(rawCode)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_CODE,
            )

        return try {
            ensureLinkableCredential(client)
            sessionCredentialRepository.promoteCurrentCredential {
                    accessToken ->
                client.registerEmailPassword(
                    request = OnlinePasswordRegisterRequestDto(
                        email = email,
                        password = password,
                        code = code,
                    ),
                    accessToken = accessToken,
                )
            }
            OnlinePasswordAccountActionResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (_: OnlineAccountSessionExpiredException) {
            failure(OnlinePasswordAccountFailureReason.SESSION_EXPIRED)
        } catch (_: OnlineSessionCredentialPersistenceException) {
            failure(OnlinePasswordAccountFailureReason.LOCAL_PERSISTENCE)
        } catch (error: OnlinePasswordIdentityException) {
            val reason = when (error.reason) {
                OnlinePasswordIdentityFailureReason.UNAUTHORIZED ->
                    OnlinePasswordAccountFailureReason.INVALID_CODE
                else -> error.reason.toPasswordAccountFailure()
            }
            failure(reason)
        } catch (_: Throwable) {
            failure(OnlinePasswordAccountFailureReason.UNKNOWN)
        }
    }

    suspend fun resetPassword(
        rawEmail: String,
        rawPassword: String,
        rawCode: String,
    ): OnlinePasswordAccountActionResult {
        val client = apiClient
            ?: return unavailableFailure()
        if (!available) {
            return unavailableFailure()
        }

        val email = normalizeOnlineEmailAddressOrNull(rawEmail)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_EMAIL,
            )
        val password = normalizeOnlinePasswordOrNull(rawPassword)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_PASSWORD,
            )
        val code = normalizeOnlineEmailCodeOrNull(rawCode)
            ?: return failure(
                OnlinePasswordAccountFailureReason.INVALID_CODE,
            )

        return try {
            prepareForAccountRecovery()
            sessionCredentialRepository.recoverAccountCredential {
                client.resetEmailPassword(
                    OnlinePasswordResetRequestDto(
                        email = email,
                        password = password,
                        code = code,
                    ),
                )
            }
            OnlinePasswordAccountActionResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (_: OnlineAccountRecoveryBlockedByAnonymousSessionException) {
            failure(OnlinePasswordAccountFailureReason.SESSION_CONFLICT)
        } catch (_: OnlineSessionCredentialPersistenceException) {
            failure(OnlinePasswordAccountFailureReason.LOCAL_PERSISTENCE)
        } catch (error: OnlinePasswordIdentityException) {
            val reason = when (error.reason) {
                OnlinePasswordIdentityFailureReason.UNAUTHORIZED ->
                    OnlinePasswordAccountFailureReason.INVALID_CODE
                else -> error.reason.toPasswordAccountFailure()
            }
            failure(reason)
        } catch (_: IllegalArgumentException) {
            failure(OnlinePasswordAccountFailureReason.SESSION_CONFLICT)
        } catch (_: Throwable) {
            failure(OnlinePasswordAccountFailureReason.UNKNOWN)
        }
    }

    private suspend fun ensureLinkableCredential(
        client: RemoteOnlineApiClient,
    ) {
        val storedCredential = sessionCredentialRepository
            .getStoredCredentialOrNull()
        val validCredential = sessionCredentialRepository
            .getValidCredentialOrNull()

        if (validCredential != null) {
            return
        }

        if (
            storedCredential?.sessionKind ==
            OnlineSessionKind.ACCOUNT
        ) {
            throw OnlineAccountSessionExpiredException()
        }

        sessionCredentialRepository.getOrCreateUsableCredential(
            createAnonymousSession = {
                client.createAnonymousSession()
            },
            refreshAccountSession = {
                throw IllegalStateException(
                    "Não há conta válida para renovar durante o cadastro.",
                )
            },
        )
    }

    private fun prepareForAccountRecovery() {
        val storedCredential = sessionCredentialRepository
            .getStoredCredentialOrNull()
            ?: return

        if (
            storedCredential.sessionKind != OnlineSessionKind.ANONYMOUS
        ) {
            return
        }

        val pendingBinding = onlineParticipationBindingRepository
            ?.getValidBindingOrNull()
        if (pendingBinding?.playerId == storedCredential.playerId) {
            throw OnlineAccountRecoveryBlockedByAnonymousSessionException()
        }

        if (!sessionCredentialRepository.clear()) {
            throw OnlineSessionCredentialPersistenceException()
        }
    }
}

internal fun normalizeOnlinePasswordOrNull(
    rawPassword: String,
): String? {
    return rawPassword.takeIf { password ->
        password.length in
            ONLINE_PASSWORD_MIN_LENGTH..ONLINE_PASSWORD_MAX_LENGTH &&
            password.none { character ->
                character == '\u0000'
            }
    }
}

private fun OnlineEmailIdentityFailureReason.toPasswordAccountFailure():
    OnlinePasswordAccountFailureReason {
    return when (this) {
        OnlineEmailIdentityFailureReason.INVALID_CODE ->
            OnlinePasswordAccountFailureReason.INVALID_CODE
        OnlineEmailIdentityFailureReason.ACCOUNT_NOT_FOUND ->
            OnlinePasswordAccountFailureReason.ACCOUNT_NOT_FOUND
        OnlineEmailIdentityFailureReason.IDENTITY_CONFLICT ->
            OnlinePasswordAccountFailureReason.ACCOUNT_EXISTS
        OnlineEmailIdentityFailureReason.RATE_LIMITED ->
            OnlinePasswordAccountFailureReason.RATE_LIMITED
        OnlineEmailIdentityFailureReason.SERVICE_UNAVAILABLE ->
            OnlinePasswordAccountFailureReason.SERVICE_UNAVAILABLE
    }
}

private fun OnlinePasswordIdentityFailureReason.toPasswordAccountFailure():
    OnlinePasswordAccountFailureReason {
    return when (this) {
        OnlinePasswordIdentityFailureReason.INVALID_REQUEST ->
            OnlinePasswordAccountFailureReason.INVALID_PASSWORD
        OnlinePasswordIdentityFailureReason.UNAUTHORIZED ->
            OnlinePasswordAccountFailureReason.INVALID_CREDENTIALS
        OnlinePasswordIdentityFailureReason.ACCOUNT_NOT_FOUND ->
            OnlinePasswordAccountFailureReason.ACCOUNT_NOT_FOUND
        OnlinePasswordIdentityFailureReason.ACCOUNT_EXISTS ->
            OnlinePasswordAccountFailureReason.ACCOUNT_EXISTS
        OnlinePasswordIdentityFailureReason.RATE_LIMITED ->
            OnlinePasswordAccountFailureReason.RATE_LIMITED
        OnlinePasswordIdentityFailureReason.SERVICE_UNAVAILABLE ->
            OnlinePasswordAccountFailureReason.SERVICE_UNAVAILABLE
    }
}

private fun failure(
    reason: OnlinePasswordAccountFailureReason,
): OnlinePasswordAccountActionResult.Failure {
    return OnlinePasswordAccountActionResult.Failure(reason)
}

private fun unavailableFailure(): OnlinePasswordAccountActionResult.Failure {
    return failure(OnlinePasswordAccountFailureReason.SERVICE_UNAVAILABLE)
}
