package com.ahtohiofilho.dominopernambucano.ui.account

import com.ahtohiofilho.dominopernambucano.online.MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH
import com.ahtohiofilho.dominopernambucano.online.ONLINE_ACCOUNT_TABLE_CODE_LENGTH
import com.ahtohiofilho.dominopernambucano.online.isValidOnlineAccountTableCode
import com.ahtohiofilho.dominopernambucano.online.normalizeOnlineAccountTableCodeInput
import com.ahtohiofilho.dominopernambucano.online.normalizeOnlinePublicDisplayName
import com.ahtohiofilho.dominopernambucano.online.suggestOnlineAccountTableCode
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileClient
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentityStore

sealed interface OnlineAccountProfileUiState {
    data object NotAvailable : OnlineAccountProfileUiState

    data object Loading : OnlineAccountProfileUiState

    data class Editing(
        val publicDisplayName: String,
        val tableName: String,
        val established: Boolean,
        val validationFallbackMessage: String,
        val tableCodeValidationMessage: String,
        val actionInProgress: Boolean = false,
        val feedbackMessage: String? = null,
    ) : OnlineAccountProfileUiState {
        val validationMessage: String?
            get() = validateOnlineAccountProfileInput(
                publicDisplayName = publicDisplayName,
                tableName = tableName,
                fallbackMessage = validationFallbackMessage,
                tableCodeMessage = tableCodeValidationMessage,
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
                tableName = normalizeOnlineAccountTableCodeInput(
                    rawName = value,
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

data class OnlineAccountProfileStrings(
    val nameRequired: String,
    val profileSaved: String,
    val reviewData: String,
    val sessionUnavailable: String,
    val connectToEditProfile: String,
    val profileMissing: String,
    val reviewNames: String,
    val rateLimited: String,
    val loadFailed: String,
    val invalidResponse: String,
    val operationFailed: String,
    val tableCodeRequired: String,
    val tableCodeMigrationRequired: String,
)

class OnlineAccountProfileUiCoordinator(
    private val client: OnlineAccountProfileClient,
    private val identityStore: OnlinePlayerIdentityStore,
    private val strings: OnlineAccountProfileStrings,
) {
    suspend fun load(
        fallbackIdentity: OnlinePlayerIdentity,
    ): OnlineAccountProfileLoadOutcome {
        return when (val result = client.fetch()) {
            is OnlineAccountProfileClientResult.Success -> {
                val requiresTableCodeSelection =
                    !isValidOnlineAccountTableCode(
                        rawName = result.profile.tableName,
                    )
                val editorTableName =
                    resolveOnlineAccountEditorTableName(
                        publicDisplayName =
                            result.profile.publicDisplayName,
                        tableName = result.profile.tableName,
                    )

                val synchronizedIdentity = synchronizeIdentity(
                    publicDisplayName =
                        result.profile.publicDisplayName,
                    tableName = editorTableName,
                )

                OnlineAccountProfileLoadOutcome(
                    state = OnlineAccountProfileUiState.Editing(
                        publicDisplayName =
                            result.profile.publicDisplayName,
                        tableName = editorTableName,
                        established = true,
                        validationFallbackMessage =
                            strings.reviewData,
                        tableCodeValidationMessage =
                            strings.tableCodeRequired,
                        feedbackMessage = if (
                            requiresTableCodeSelection
                        ) {
                            strings.tableCodeMigrationRequired
                        } else {
                            null
                        },
                    ),
                    synchronizedIdentity = synchronizedIdentity,
                )
            }

            is OnlineAccountProfileClientResult.Failure -> {
                if (
                    result.kind ==
                    OnlineAccountProfileFailureKind.NOT_ESTABLISHED
                ) {
                    val editorTableName =
                        resolveOnlineAccountEditorTableName(
                            publicDisplayName =
                                fallbackIdentity.displayName,
                            tableName = fallbackIdentity.tableName,
                        )
                    val synchronizedIdentity = if (
                        editorTableName != fallbackIdentity.tableName
                    ) {
                        identityStore.updateTableName(
                            tableName = editorTableName,
                        )
                    } else {
                        null
                    }

                    OnlineAccountProfileLoadOutcome(
                        state = OnlineAccountProfileUiState.Editing(
                            publicDisplayName =
                                fallbackIdentity.displayName,
                            tableName = editorTableName,
                            established = false,
                            validationFallbackMessage =
                                strings.reviewData,
                            tableCodeValidationMessage =
                                strings.tableCodeRequired,
                            feedbackMessage =
                                strings.nameRequired,
                        ),
                        synchronizedIdentity = synchronizedIdentity,
                    )
                } else {
                    OnlineAccountProfileLoadOutcome(
                        state = OnlineAccountProfileUiState.Failure(
                            message = result.kind.toVisibleMessage(
                                strings,
                            ),
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
                tableName = editor.tableName,
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
                        validationFallbackMessage =
                            strings.reviewData,
                        tableCodeValidationMessage =
                            strings.tableCodeRequired,
                        feedbackMessage =
                            strings.profileSaved,
                    ),
                    synchronizedIdentity = synchronizedIdentity,
                )
            }

            is OnlineAccountProfileClientResult.Failure -> {
                OnlineAccountProfileSaveOutcome(
                    state = editor.copy(
                        actionInProgress = false,
                        feedbackMessage =
                            result.kind.toVisibleMessage(strings),
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

private fun resolveOnlineAccountEditorTableName(
    publicDisplayName: String,
    tableName: String,
): String {
    return if (
        isValidOnlineAccountTableCode(
            rawName = tableName,
        )
    ) {
        normalizeOnlineAccountTableCodeInput(
            rawName = tableName,
        )
    } else {
        suggestOnlineAccountTableCode(
            publicDisplayName = publicDisplayName,
        )
    }
}

private fun validateOnlineAccountProfileInput(
    publicDisplayName: String,
    tableName: String,
    fallbackMessage: String,
    tableCodeMessage: String,
): String? {
    try {
        normalizeOnlinePublicDisplayName(
            rawName = publicDisplayName,
        )
    } catch (_: IllegalArgumentException) {
        return fallbackMessage
    }

    if (
        !isValidOnlineAccountTableCode(
            rawName = tableName,
        )
    ) {
        return tableCodeMessage
    }

    return null
}

private fun OnlineAccountProfileFailureKind.toVisibleMessage(
    strings: OnlineAccountProfileStrings,
):
    String {
    return when (this) {
        OnlineAccountProfileFailureKind.AUTHENTICATION_REQUIRED ->
            strings.sessionUnavailable

        OnlineAccountProfileFailureKind.ACCOUNT_REQUIRED ->
            strings.connectToEditProfile

        OnlineAccountProfileFailureKind.NOT_ESTABLISHED ->
            strings.profileMissing

        OnlineAccountProfileFailureKind.INVALID_PROFILE ->
            strings.reviewNames

        OnlineAccountProfileFailureKind.RATE_LIMITED ->
            strings.rateLimited

        OnlineAccountProfileFailureKind.UNAVAILABLE ->
            strings.loadFailed

        OnlineAccountProfileFailureKind.PROTOCOL_ERROR ->
            strings.invalidResponse

        OnlineAccountProfileFailureKind.UNKNOWN ->
            strings.operationFailed
    }
}
