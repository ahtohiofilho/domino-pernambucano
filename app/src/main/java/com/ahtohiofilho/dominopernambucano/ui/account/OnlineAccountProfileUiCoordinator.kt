package com.ahtohiofilho.dominopernambucano.ui.account

import com.ahtohiofilho.dominopernambucano.online.MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH
import com.ahtohiofilho.dominopernambucano.online.MAX_ONLINE_TABLE_NAME_LENGTH
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileClient
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentityStore
import com.ahtohiofilho.dominopernambucano.online.createOnlineAccountProfile

sealed interface OnlineAccountProfileUiState {
    data object NotAvailable : OnlineAccountProfileUiState

    data object Loading : OnlineAccountProfileUiState

    data class Editing(
        val publicDisplayName: String,
        val tableName: String,
        val established: Boolean,
        val actionInProgress: Boolean = false,
        val feedbackMessage: String? = null,
    ) : OnlineAccountProfileUiState {
        val validationMessage: String?
            get() = validateOnlineAccountProfileInput(
                publicDisplayName = publicDisplayName,
                tableName = tableName,
            )

        val saveEnabled: Boolean
            get() = !actionInProgress && validationMessage == null

        fun withPublicDisplayName(
            value: String,
        ): Editing {
            return copy(
                publicDisplayName =
                    value.take(
                        MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH,
                    ),
                feedbackMessage = null,
            )
        }

        fun withTableName(
            value: String,
        ): Editing {
            return copy(
                tableName = value.take(
                    MAX_ONLINE_TABLE_NAME_LENGTH,
                ),
                feedbackMessage = null,
            )
        }
    }

    data class Failure(
        val message: String,
        val retryable: Boolean,
    ) : OnlineAccountProfileUiState
}

data class OnlineAccountProfileLoadOutcome(
    val state: OnlineAccountProfileUiState,
    val synchronizedIdentity: OnlinePlayerIdentity?,
)

data class OnlineAccountProfileSaveOutcome(
    val state: OnlineAccountProfileUiState.Editing,
    val synchronizedIdentity: OnlinePlayerIdentity?,
)

class OnlineAccountProfileUiCoordinator(
    private val client: OnlineAccountProfileClient,
    private val identityStore: OnlinePlayerIdentityStore,
) {
    suspend fun load(
        fallbackIdentity: OnlinePlayerIdentity,
    ): OnlineAccountProfileLoadOutcome {
        return when (val result = client.fetch()) {
            is OnlineAccountProfileClientResult.Success -> {
                val synchronizedIdentity = synchronizeIdentity(
                    publicDisplayName =
                        result.profile.publicDisplayName,
                    tableName = result.profile.tableName,
                )

                OnlineAccountProfileLoadOutcome(
                    state = OnlineAccountProfileUiState.Editing(
                        publicDisplayName =
                            result.profile.publicDisplayName,
                        tableName = result.profile.tableName,
                        established = true,
                    ),
                    synchronizedIdentity = synchronizedIdentity,
                )
            }

            is OnlineAccountProfileClientResult.Failure -> {
                if (
                    result.kind ==
                    OnlineAccountProfileFailureKind.NOT_ESTABLISHED
                ) {
                    OnlineAccountProfileLoadOutcome(
                        state = OnlineAccountProfileUiState.Editing(
                            publicDisplayName =
                                fallbackIdentity.displayName,
                            tableName = fallbackIdentity.tableName,
                            established = false,
                            feedbackMessage =
                                "Complete seu nome público para criar o perfil da conta.",
                        ),
                        synchronizedIdentity = null,
                    )
                } else {
                    OnlineAccountProfileLoadOutcome(
                        state = OnlineAccountProfileUiState.Failure(
                            message = result.kind.toVisibleMessage(),
                            retryable = result.retryable,
                        ),
                        synchronizedIdentity = null,
                    )
                }
            }
        }
    }

    suspend fun save(
        editor: OnlineAccountProfileUiState.Editing,
    ): OnlineAccountProfileSaveOutcome {
        val validationMessage = editor.validationMessage

        if (validationMessage != null) {
            return OnlineAccountProfileSaveOutcome(
                state = editor.copy(
                    actionInProgress = false,
                    feedbackMessage = validationMessage,
                ),
                synchronizedIdentity = null,
            )
        }

        return when (
            val result = client.update(
                publicDisplayName = editor.publicDisplayName,
                tableName = editor.tableName.takeIf {
                    value -> value.isNotBlank()
                },
            )
        ) {
            is OnlineAccountProfileClientResult.Success -> {
                val synchronizedIdentity = synchronizeIdentity(
                    publicDisplayName =
                        result.profile.publicDisplayName,
                    tableName = result.profile.tableName,
                )

                OnlineAccountProfileSaveOutcome(
                    state = OnlineAccountProfileUiState.Editing(
                        publicDisplayName =
                            result.profile.publicDisplayName,
                        tableName = result.profile.tableName,
                        established = true,
                        feedbackMessage =
                            "Perfil salvo. O ranking e a mesa usarão estes nomes.",
                    ),
                    synchronizedIdentity = synchronizedIdentity,
                )
            }

            is OnlineAccountProfileClientResult.Failure -> {
                OnlineAccountProfileSaveOutcome(
                    state = editor.copy(
                        actionInProgress = false,
                        feedbackMessage =
                            result.kind.toVisibleMessage(),
                    ),
                    synchronizedIdentity = null,
                )
            }
        }
    }

    private fun synchronizeIdentity(
        publicDisplayName: String,
        tableName: String,
    ): OnlinePlayerIdentity {
        identityStore.updateDisplayName(
            displayName = publicDisplayName,
        )

        return identityStore.updateTableName(
            tableName = tableName,
        )
    }
}

private fun validateOnlineAccountProfileInput(
    publicDisplayName: String,
    tableName: String,
): String? {
    return try {
        createOnlineAccountProfile(
            publicDisplayName = publicDisplayName,
            tableName = tableName.takeIf {
                value -> value.isNotBlank()
            },
            updatedAtEpochMillis = 0L,
        )
        null
    } catch (error: IllegalArgumentException) {
        error.message ?: "Revise os dados do perfil."
    }
}

private fun OnlineAccountProfileFailureKind.toVisibleMessage():
    String {
    return when (this) {
        OnlineAccountProfileFailureKind.AUTHENTICATION_REQUIRED ->
            "Sua sessão online não está disponível. Reconecte a conta."

        OnlineAccountProfileFailureKind.ACCOUNT_REQUIRED ->
            "Conecte uma conta para editar o perfil público."

        OnlineAccountProfileFailureKind.NOT_ESTABLISHED ->
            "O perfil da conta ainda não foi criado."

        OnlineAccountProfileFailureKind.INVALID_PROFILE ->
            "Revise o nome público e o nome de mesa."

        OnlineAccountProfileFailureKind.RATE_LIMITED ->
            "Muitas tentativas em pouco tempo. Aguarde e tente novamente."

        OnlineAccountProfileFailureKind.UNAVAILABLE ->
            "Não foi possível carregar o perfil agora. Tente novamente."

        OnlineAccountProfileFailureKind.PROTOCOL_ERROR ->
            "O servidor retornou uma resposta de perfil inválida."

        OnlineAccountProfileFailureKind.UNKNOWN ->
            "Não foi possível concluir a operação de perfil."
    }
}
