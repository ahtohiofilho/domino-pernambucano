package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.CancellationException

enum class OnlineGoogleAccountStatus {
    UNAVAILABLE,
    NO_LOCAL_CREDENTIAL,
    VISITOR,
    CONNECTED,
    RECOVERY_REQUIRED,
}

sealed interface OnlineGoogleAccountActionResult {
    data class Success(
        val status: OnlineGoogleAccountStatus,
    ) : OnlineGoogleAccountActionResult

    data object Cancelled : OnlineGoogleAccountActionResult

    data class Failure(
        val message: String,
    ) : OnlineGoogleAccountActionResult
}

class OnlineGoogleAccountManager(
    private val available: Boolean,
    private val googleIdTokenProvider: GoogleIdTokenProvider?,
    private val googleIdentityRepository: OnlineGoogleIdentityRepository?,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {
    fun currentStatus(): OnlineGoogleAccountStatus {
        val credential = sessionCredentialRepository
            .getStoredCredentialOrNull()

        if (credential == null) {
            return if (available) {
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

                    if (available) {
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

    suspend fun connect(): OnlineGoogleAccountActionResult {
        val tokenProvider = googleIdTokenProvider
        val identityRepository = googleIdentityRepository

        if (!available || tokenProvider == null || identityRepository == null) {
            return OnlineGoogleAccountActionResult.Failure(
                message = "O acesso com Google não está disponível neste build.",
            )
        }

        return try {
            val idToken = tokenProvider.requestIdToken()
            identityRepository.connectGoogleIdentity(idToken)

            OnlineGoogleAccountActionResult.Success(
                status = currentStatus(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: GoogleCredentialSelectionCancelledException) {
            OnlineGoogleAccountActionResult.Cancelled
        } catch (error: OnlineGoogleIdentityException) {
            OnlineGoogleAccountActionResult.Failure(
                message = error.reason.toUserMessage(),
            )
        } catch (_: GoogleCredentialUnavailableException) {
            OnlineGoogleAccountActionResult.Failure(
                message = "Não foi possível abrir sua conta Google. Tente novamente.",
            )
        } catch (_: Throwable) {
            OnlineGoogleAccountActionResult.Failure(
                message = "Não foi possível conectar sua conta agora. Tente novamente.",
            )
        }
    }
}

private fun OnlineGoogleIdentityFailureReason.toUserMessage(): String {
    return when (this) {
        OnlineGoogleIdentityFailureReason.INVALID_GOOGLE_CREDENTIAL ->
            "A conta Google não pôde ser confirmada. Tente novamente."

        OnlineGoogleIdentityFailureReason.ACCOUNT_NOT_FOUND ->
            "Essa conta Google ainda não está vinculada ao seu jogador."

        OnlineGoogleIdentityFailureReason.IDENTITY_CONFLICT ->
            "Essa conta Google já está vinculada a outro jogador."

        OnlineGoogleIdentityFailureReason.SERVICE_UNAVAILABLE ->
            "O acesso com Google está temporariamente indisponível."
    }
}
