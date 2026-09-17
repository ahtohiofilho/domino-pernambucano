package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val DEFAULT_GOOGLE_CONNECTION_TIMEOUT_MILLIS = 30_000L

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
    private val profileEnrichmentStore:
        OnlineProfileEnrichmentStore? = null,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val connectionTimeoutMillis: Long =
        DEFAULT_GOOGLE_CONNECTION_TIMEOUT_MILLIS,
) {
    init {
        require(connectionTimeoutMillis > 0L) {
            "O timeout da conexão Google deve ser positivo."
        }
    }

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

    suspend fun restoreAuthorizedAccount(): Boolean {
        if (currentStatus() == OnlineGoogleAccountStatus.CONNECTED) {
            return true
        }
        if (currentStatus() != OnlineGoogleAccountStatus.RECOVERY_REQUIRED) {
            return false
        }

        val tokenProvider = googleIdTokenProvider ?: return false
        val identityRepository = googleIdentityRepository ?: return false
        if (!available) {
            return false
        }

        return try {
            withTimeoutOrNull(connectionTimeoutMillis) {
                val googleCredential = tokenProvider
                    .requestAuthorizedIdentityCredentialOrNull()
                    ?: return@withTimeoutOrNull false

                val accountCredential =
                    identityRepository.recoverGoogleAccount(
                        googleCredential.idToken,
                    )

                persistGoogleProfileEnrichment(
                    googleCredential = googleCredential,
                    accountCredential = accountCredential,
                )

                currentStatus() == OnlineGoogleAccountStatus.CONNECTED
            } ?: false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
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
            val connected = withTimeoutOrNull(connectionTimeoutMillis) {
                val googleCredential =
                    tokenProvider.requestIdentityCredential()

                val accountCredential =
                    identityRepository.connectGoogleIdentity(
                        googleCredential.idToken,
                    )

                persistGoogleProfileEnrichment(
                    googleCredential = googleCredential,
                    accountCredential = accountCredential,
                )

                true
            } ?: false

            if (!connected) {
                return OnlineGoogleAccountActionResult.Failure(
                    message =
                        "Não foi possível conectar sua conta agora. Tente novamente.",
                )
            }

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

    private fun persistGoogleProfileEnrichment(
        googleCredential: GoogleIdentityCredential,
        accountCredential: OnlineSessionCredential,
    ) {
        val store = profileEnrichmentStore ?: return

        if (accountCredential.sessionKind != OnlineSessionKind.ACCOUNT) {
            return
        }

        val accountId = accountCredential.accountId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return

        val playerId = accountCredential.playerId
            .trim()
            .takeIf(String::isNotBlank)
            ?: return

        try {
            store.writeGoogleProfile(
                accountId = accountId,
                playerId = playerId,
                profilePhotoUri = googleCredential.profilePictureUri,
            )
        } catch (_: Throwable) {
            /*
             * Profile enrichment is deliberately non-authoritative.
             * A local photo-cache failure must never turn successful
             * authentication into an authentication failure.
             */
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
