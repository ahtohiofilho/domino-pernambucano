package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.CancellationException

enum class OnlinePlayGamesAccountFailureReason {
    NOT_CONFIGURED,
    SIGN_IN_UNAVAILABLE,
    INVALID_PLAY_GAMES_CREDENTIAL,
    ACCOUNT_NOT_FOUND,
    IDENTITY_CONFLICT,
    SERVICE_UNAVAILABLE,
    UNKNOWN,
}

sealed interface OnlinePlayGamesAccountActionResult {
    data class Success(
        val status: OnlineGoogleAccountStatus,
    ) : OnlinePlayGamesAccountActionResult

    data object Cancelled : OnlinePlayGamesAccountActionResult

    data class Failure(
        val reason: OnlinePlayGamesAccountFailureReason,
    ) : OnlinePlayGamesAccountActionResult
}

class OnlinePlayGamesAccountManager(
    private val available: Boolean,
    private val serverAuthCodeProvider:
        PlayGamesServerAuthCodeProvider?,
    private val playGamesIdentityRepository:
        OnlinePlayGamesIdentityRepository?,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {
    fun currentStatus(): OnlineGoogleAccountStatus {
        if (!available) {
            return OnlineGoogleAccountStatus.UNAVAILABLE
        }

        val credential = sessionCredentialRepository
            .getStoredCredentialOrNull()
            ?: return OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL

        return when (credential.sessionKind) {
            OnlineSessionKind.ANONYMOUS -> {
                if (nowEpochMillis() < credential.expiresAtEpochMillis) {
                    OnlineGoogleAccountStatus.VISITOR
                } else {
                    sessionCredentialRepository.getValidCredentialOrNull()
                    OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL
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

    suspend fun connect(): OnlinePlayGamesAccountActionResult {
        val codeProvider = serverAuthCodeProvider
        val identityRepository = playGamesIdentityRepository

        if (!available || codeProvider == null || identityRepository == null) {
            return OnlinePlayGamesAccountActionResult.Failure(
                OnlinePlayGamesAccountFailureReason.NOT_CONFIGURED,
            )
        }

        return try {
            identityRepository.connectPlayGamesIdentity(
                requestServerAuthCode = {
                    codeProvider.requestServerAuthCode()
                },
            )

            OnlinePlayGamesAccountActionResult.Success(
                status = currentStatus(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: PlayGamesSignInCancelledException) {
            OnlinePlayGamesAccountActionResult.Cancelled
        } catch (_: PlayGamesCredentialUnavailableException) {
            OnlinePlayGamesAccountActionResult.Failure(
                OnlinePlayGamesAccountFailureReason
                    .SIGN_IN_UNAVAILABLE,
            )
        } catch (error: OnlinePlayGamesIdentityException) {
            OnlinePlayGamesAccountActionResult.Failure(
                reason = error.reason.toAccountFailureReason(),
            )
        } catch (_: Throwable) {
            OnlinePlayGamesAccountActionResult.Failure(
                OnlinePlayGamesAccountFailureReason.UNKNOWN,
            )
        }
    }
}

internal fun mergeOnlineAccountStatuses(
    vararg statuses: OnlineGoogleAccountStatus,
): OnlineGoogleAccountStatus {
    return statuses.maxByOrNull { status ->
        when (status) {
            OnlineGoogleAccountStatus.UNAVAILABLE -> 0
            OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL -> 1
            OnlineGoogleAccountStatus.VISITOR -> 2
            OnlineGoogleAccountStatus.RECOVERY_REQUIRED -> 3
            OnlineGoogleAccountStatus.CONNECTED -> 4
        }
    } ?: OnlineGoogleAccountStatus.UNAVAILABLE
}

private fun OnlinePlayGamesIdentityFailureReason.toAccountFailureReason():
    OnlinePlayGamesAccountFailureReason {
    return when (this) {
        OnlinePlayGamesIdentityFailureReason
            .INVALID_PLAY_GAMES_CREDENTIAL ->
            OnlinePlayGamesAccountFailureReason
                .INVALID_PLAY_GAMES_CREDENTIAL

        OnlinePlayGamesIdentityFailureReason.ACCOUNT_NOT_FOUND ->
            OnlinePlayGamesAccountFailureReason.ACCOUNT_NOT_FOUND

        OnlinePlayGamesIdentityFailureReason.IDENTITY_CONFLICT ->
            OnlinePlayGamesAccountFailureReason.IDENTITY_CONFLICT

        OnlinePlayGamesIdentityFailureReason.SERVICE_UNAVAILABLE ->
            OnlinePlayGamesAccountFailureReason.SERVICE_UNAVAILABLE
    }
}
