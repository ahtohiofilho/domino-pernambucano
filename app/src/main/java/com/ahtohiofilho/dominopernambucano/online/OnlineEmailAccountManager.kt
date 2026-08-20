package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.CancellationException

enum class OnlineEmailAccountIntent {
    LINK,
    RECOVER,
}

enum class OnlineEmailAccountFailureReason {
    INVALID_EMAIL,
    INVALID_CODE,
    ACCOUNT_NOT_FOUND,
    IDENTITY_CONFLICT,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    SESSION_CONFLICT,
    SESSION_EXPIRED,
    LOCAL_PERSISTENCE,
    UNKNOWN,
}

sealed interface OnlineEmailAccountActionResult {
    data class CodeRequested(
        val email: String,
        val intent: OnlineEmailAccountIntent,
    ) : OnlineEmailAccountActionResult

    data class Success(
        val status: OnlineGoogleAccountStatus,
        val intent: OnlineEmailAccountIntent,
    ) : OnlineEmailAccountActionResult

    data class Failure(
        val reason: OnlineEmailAccountFailureReason,
        val requiresNewCode: Boolean = false,
    ) : OnlineEmailAccountActionResult
}

class OnlineEmailAccountManager(
    private val available: Boolean,
    private val apiClient: RemoteOnlineApiClient?,
    private val emailIdentityRepository: OnlineEmailIdentityRepository?,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {
    val isAvailable: Boolean
        get() =
            available &&
                apiClient != null &&
                emailIdentityRepository != null

    fun currentStatus(): OnlineGoogleAccountStatus {
        val credential = sessionCredentialRepository
            .getStoredCredentialOrNull()

        if (credential == null) {
            return if (isAvailable) {
                OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL
            } else {
                OnlineGoogleAccountStatus.UNAVAILABLE
            }
        }

        return when (credential.sessionKind) {
            OnlineSessionKind.ANONYMOUS -> {
                if (nowEpochMillis() < credential.expiresAtEpochMillis) {
                    OnlineGoogleAccountStatus.VISITOR
                } else {
                    sessionCredentialRepository.getValidCredentialOrNull()
                    if (isAvailable) {
                        OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL
                    } else {
                        OnlineGoogleAccountStatus.UNAVAILABLE
                    }
                }
            }

            OnlineSessionKind.ACCOUNT -> {
                if (nowEpochMillis() < credential.expiresAtEpochMillis) {
                    OnlineGoogleAccountStatus.CONNECTED
                } else {
                    OnlineGoogleAccountStatus.RECOVERY_REQUIRED
                }
            }
        }
    }

    suspend fun requestCode(
        rawEmail: String,
        intent: OnlineEmailAccountIntent,
    ): OnlineEmailAccountActionResult {
        val repository = emailIdentityRepository
        if (!isAvailable || repository == null) {
            return OnlineEmailAccountActionResult.Failure(
                reason =
                    OnlineEmailAccountFailureReason.SERVICE_UNAVAILABLE,
            )
        }

        val email = normalizeOnlineEmailAddressOrNull(rawEmail)
            ?: return OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.INVALID_EMAIL,
            )

        return try {
            OnlineEmailAccountActionResult.CodeRequested(
                email = repository.requestVerificationCode(email),
                intent = intent,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: OnlineEmailIdentityException) {
            OnlineEmailAccountActionResult.Failure(
                reason = error.reason.toAccountFailureReason(),
            )
        } catch (_: Throwable) {
            OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.UNKNOWN,
            )
        }
    }

    suspend fun submitCode(
        rawEmail: String,
        rawCode: String,
        intent: OnlineEmailAccountIntent,
    ): OnlineEmailAccountActionResult {
        val repository = emailIdentityRepository
        val client = apiClient
        if (!isAvailable || repository == null || client == null) {
            return OnlineEmailAccountActionResult.Failure(
                reason =
                    OnlineEmailAccountFailureReason.SERVICE_UNAVAILABLE,
            )
        }

        val email = normalizeOnlineEmailAddressOrNull(rawEmail)
            ?: return OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.INVALID_EMAIL,
            )
        val code = normalizeOnlineEmailCodeOrNull(rawCode)
            ?: return OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.INVALID_CODE,
            )

        return try {
            when (intent) {
                OnlineEmailAccountIntent.LINK -> {
                    ensureLinkableCredential(client)
                    repository.linkEmailIdentity(
                        rawEmail = email,
                        rawCode = code,
                    )
                }

                OnlineEmailAccountIntent.RECOVER -> {
                    repository.recoverEmailAccount(
                        rawEmail = email,
                        rawCode = code,
                    )
                }
            }

            OnlineEmailAccountActionResult.Success(
                status = currentStatus(),
                intent = intent,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: OnlineEmailIdentityException) {
            OnlineEmailAccountActionResult.Failure(
                reason = error.reason.toAccountFailureReason(),
                requiresNewCode =
                    error.reason ==
                        OnlineEmailIdentityFailureReason.ACCOUNT_NOT_FOUND ||
                        error.reason ==
                        OnlineEmailIdentityFailureReason.IDENTITY_CONFLICT,
            )
        } catch (_: OnlineAccountRecoveryBlockedByAnonymousSessionException) {
            OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.SESSION_CONFLICT,
            )
        } catch (_: OnlineAccountSessionExpiredException) {
            OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.SESSION_EXPIRED,
            )
        } catch (_: OnlineSessionCredentialPersistenceException) {
            OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.LOCAL_PERSISTENCE,
                requiresNewCode = true,
            )
        } catch (_: Throwable) {
            OnlineEmailAccountActionResult.Failure(
                reason = OnlineEmailAccountFailureReason.UNKNOWN,
                requiresNewCode = true,
            )
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
                    "A Phase B não renova conta durante vinculação por e-mail.",
                )
            },
        )
    }
}

private fun OnlineEmailIdentityFailureReason.toAccountFailureReason():
    OnlineEmailAccountFailureReason {
    return when (this) {
        OnlineEmailIdentityFailureReason.INVALID_CODE ->
            OnlineEmailAccountFailureReason.INVALID_CODE
        OnlineEmailIdentityFailureReason.ACCOUNT_NOT_FOUND ->
            OnlineEmailAccountFailureReason.ACCOUNT_NOT_FOUND
        OnlineEmailIdentityFailureReason.IDENTITY_CONFLICT ->
            OnlineEmailAccountFailureReason.IDENTITY_CONFLICT
        OnlineEmailIdentityFailureReason.RATE_LIMITED ->
            OnlineEmailAccountFailureReason.RATE_LIMITED
        OnlineEmailIdentityFailureReason.SERVICE_UNAVAILABLE ->
            OnlineEmailAccountFailureReason.SERVICE_UNAVAILABLE
    }
}
