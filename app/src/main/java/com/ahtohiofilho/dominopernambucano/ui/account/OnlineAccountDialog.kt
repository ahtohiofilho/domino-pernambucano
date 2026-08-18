package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH
import com.ahtohiofilho.dominopernambucano.online.ONLINE_ACCOUNT_TABLE_CODE_LENGTH
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.isValidOnlineAccountTableCode
import com.ahtohiofilho.dominopernambucano.ui.components.DominoOutlinedTextField
import com.ahtohiofilho.dominopernambucano.ui.components.DominoPrimaryButton
import com.ahtohiofilho.dominopernambucano.ui.components.DominoTextAction
import com.ahtohiofilho.dominopernambucano.ui.components.DominoTextFieldTone
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

internal const val OnlineAccountDialogTag = "online_account_dialog"
internal const val OnlineAccountTitleTag = "online_account_title"
internal const val OnlineAccountPrimaryActionTag =
    "online_account_primary_action"
internal const val OnlineAccountDismissActionTag =
    "online_account_dismiss_action"
internal const val OnlineAccountPublicNameFieldTag =
    "online_account_public_name_field"
internal const val OnlineAccountTableNameFieldTag =
    "online_account_table_name_field"
internal const val OnlineAccountProfileStatusTag =
    "online_account_profile_status"

internal fun onlineAccountDialogVisibleFeedback(
    status: OnlineGoogleAccountStatus,
    feedbackMessage: String?,
): String? {
    return feedbackMessage.takeUnless {
        status == OnlineGoogleAccountStatus.CONNECTED
    }
}

@Composable
fun OnlineAccountDialog(
    status: OnlineGoogleAccountStatus,
    actionInProgress: Boolean,
    feedbackMessage: String?,
    profileState: OnlineAccountProfileUiState,
    onPublicDisplayNameChange: (String) -> Unit,
    onTableNameChange: (String) -> Unit,
    onSaveProfileClick: () -> Unit,
    onRetryProfileClick: () -> Unit,
    onConnectGoogleClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val presentation = status.toPresentation(
        strings = OnlineAccountPresentationStrings(
            googleUnavailable = stringResource(
                R.string.account_google_unavailable,
            ),
            googleRecoverDescription = stringResource(
                R.string.account_google_recover_description,
            ),
            googleContinue = stringResource(
                R.string.account_google_continue,
            ),
            googleLinkDescription = stringResource(
                R.string.account_google_link_description,
            ),
            googleLink = stringResource(
                R.string.account_google_link,
            ),
            profilePreserved = stringResource(
                R.string.account_profile_preserved,
            ),
            googleReauthenticateDescription = stringResource(
                R.string.account_google_reauthenticate_description,
            ),
            googleRecover = stringResource(
                R.string.account_google_recover,
            ),
        ),
    )
    val editor =
        profileState as? OnlineAccountProfileUiState.Editing
    val profileActionInProgress =
        editor?.actionInProgress == true
    val anyActionInProgress =
        actionInProgress || profileActionInProgress
    val visibleFeedbackMessage =
        onlineAccountDialogVisibleFeedback(
            status = status,
            feedbackMessage = feedbackMessage,
        )
    val accountStateDescription = if (anyActionInProgress) {
        stringResource(R.string.account_state_action_in_progress)
    } else {
        when (status) {
            OnlineGoogleAccountStatus.UNAVAILABLE ->
                stringResource(R.string.account_state_unavailable)

            OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL ->
                stringResource(R.string.account_state_disconnected)

            OnlineGoogleAccountStatus.VISITOR ->
                stringResource(R.string.account_state_visitor)

            OnlineGoogleAccountStatus.CONNECTED ->
                stringResource(R.string.account_state_connected)

            OnlineGoogleAccountStatus.RECOVERY_REQUIRED ->
                stringResource(R.string.account_state_recovery_required)
        }
    }

    AlertDialog(
        modifier = Modifier
            .testTag(OnlineAccountDialogTag)
            .semantics {
                stateDescription = accountStateDescription
            },
        onDismissRequest = {
            if (!anyActionInProgress) {
                onDismissRequest()
            }
        },
        title = {
            Text(
                modifier = Modifier
                    .testTag(OnlineAccountTitleTag)
                    .semantics {
                        heading()
                    },
                text = stringResource(R.string.account_title),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.sm,
                ),
            ) {
                Text(text = presentation.message)

                visibleFeedbackMessage?.let { message ->
                    Text(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        text = message,
                    )
                }

                if (
                    status == OnlineGoogleAccountStatus.CONNECTED
                ) {
                    OnlineAccountProfileContent(
                        state = profileState,
                        onPublicDisplayNameChange =
                            onPublicDisplayNameChange,
                        onTableNameChange = onTableNameChange,
                    )
                }
            }
        },
        confirmButton = {
            when {
                status != OnlineGoogleAccountStatus.CONNECTED -> {
                    presentation.actionLabel?.let { actionLabel ->
                        DominoPrimaryButton(
                            modifier = Modifier.testTag(
                                OnlineAccountPrimaryActionTag,
                            ),
                            text = if (actionInProgress) {
                                stringResource(
                                    R.string.account_connecting,
                                )
                            } else {
                                actionLabel
                            },
                            onClick = onConnectGoogleClick,
                            enabled = !anyActionInProgress,
                            loading = actionInProgress,
                            containerColor =
                                DominoSemanticColors.dialogAction,
                            contentColor =
                                DominoSemanticColors.dialogActionContent,
                            disabledContainerColor =
                                DominoSemanticColors.dialogDisabledAction,
                            disabledContentColor =
                                DominoSemanticColors
                                    .dialogDisabledActionContent,
                        )
                    }
                }

                editor != null -> {
                    DominoPrimaryButton(
                        modifier = Modifier.testTag(
                            OnlineAccountPrimaryActionTag,
                        ),
                        text = if (editor.actionInProgress) {
                            stringResource(R.string.account_saving)
                        } else {
                            stringResource(
                                R.string.account_save_profile,
                            )
                        },
                        onClick = onSaveProfileClick,
                        enabled =
                            editor.saveEnabled &&
                                !actionInProgress,
                        loading = editor.actionInProgress,
                        containerColor =
                            DominoSemanticColors.dialogAction,
                        contentColor =
                            DominoSemanticColors.dialogActionContent,
                        disabledContainerColor =
                            DominoSemanticColors.dialogDisabledAction,
                        disabledContentColor =
                            DominoSemanticColors
                                .dialogDisabledActionContent,
                    )
                }

                profileState is
                    OnlineAccountProfileUiState.Failure &&
                    profileState.retryable -> {
                    DominoPrimaryButton(
                        modifier = Modifier.testTag(
                            OnlineAccountPrimaryActionTag,
                        ),
                        text = stringResource(R.string.account_retry),
                        onClick = onRetryProfileClick,
                        enabled = !anyActionInProgress,
                        containerColor =
                            DominoSemanticColors.dialogAction,
                        contentColor =
                            DominoSemanticColors.dialogActionContent,
                        disabledContainerColor =
                            DominoSemanticColors.dialogDisabledAction,
                        disabledContentColor =
                            DominoSemanticColors
                                .dialogDisabledActionContent,
                    )
                }
            }
        },
        dismissButton = {
            DominoTextAction(
                modifier = Modifier.testTag(
                    OnlineAccountDismissActionTag,
                ),
                text = stringResource(R.string.common_close),
                onClick = onDismissRequest,
                enabled = !anyActionInProgress,
                contentColor =
                    DominoSemanticColors.dialogDismissAction,
                disabledContentColor =
                    DominoSemanticColors.dialogDisabledDismissAction,
            )
        },
        containerColor = DominoSemanticColors.dialogSurface,
        titleContentColor = DominoSemanticColors.dialogTitle,
        textContentColor = DominoSemanticColors.dialogBody,
    )
}

@Composable
private fun OnlineAccountProfileContent(
    state: OnlineAccountProfileUiState,
    onPublicDisplayNameChange: (String) -> Unit,
    onTableNameChange: (String) -> Unit,
) {
    when (state) {
        OnlineAccountProfileUiState.NotAvailable -> {
            Text(
                text = stringResource(
                    R.string.account_profile_unavailable,
                ),
            )
        }

        OnlineAccountProfileUiState.Loading -> {
            Text(
                modifier = Modifier
                    .testTag(OnlineAccountProfileStatusTag)
                    .semantics {
                        progressBarRangeInfo =
                            ProgressBarRangeInfo.Indeterminate
                    },
                text = stringResource(
                    R.string.account_profile_loading,
                ),
            )
        }

        is OnlineAccountProfileUiState.Failure -> {
            Text(
                modifier = Modifier
                    .testTag(OnlineAccountProfileStatusTag)
                    .semantics {
                        liveRegion = LiveRegionMode.Assertive
                    },
                text = state.message,
            )
        }

        is OnlineAccountProfileUiState.Editing -> {
            val tableCodeValid =
                isValidOnlineAccountTableCode(
                    rawName = state.tableName,
                )
            val tableCodeHasInput = state.tableName.isNotBlank()
            val tableCodeSupportingText = if (tableCodeValid) {
                stringResource(
                    R.string.account_short_name_valid,
                )
            } else {
                stringResource(
                    R.string.account_short_name_hint,
                    state.tableName.length,
                    ONLINE_ACCOUNT_TABLE_CODE_LENGTH,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.xs,
                ),
            ) {
                Text(
                    text = stringResource(
                        R.string.account_profile_names_explanation,
                    ),
                )

                DominoOutlinedTextField(
                    value = state.publicDisplayName,
                    onValueChange = onPublicDisplayNameChange,
                    modifier = Modifier.testTag(
                        OnlineAccountPublicNameFieldTag,
                    ),
                    enabled = !state.actionInProgress,
                    label = stringResource(
                        R.string.account_public_name,
                    ),
                    supportingText = stringResource(
                        R.string.account_character_count,
                        state.publicDisplayName.length,
                        MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization =
                            KeyboardCapitalization.Words,
                    ),
                    tone = DominoTextFieldTone.OnLight,
                )

                DominoOutlinedTextField(
                    value = state.tableName,
                    onValueChange = onTableNameChange,
                    modifier = Modifier.testTag(
                        OnlineAccountTableNameFieldTag,
                    ),
                    enabled = !state.actionInProgress,
                    label = stringResource(
                        R.string.account_table_code,
                    ),
                    supportingText = tableCodeSupportingText,
                    keyboardOptions = KeyboardOptions(
                        capitalization =
                            KeyboardCapitalization.Characters,
                    ),
                    tone = DominoTextFieldTone.OnLight,
                    isError =
                        tableCodeHasInput && !tableCodeValid,
                    success = tableCodeValid,
                )

                state.validationMessage
                    ?.takeUnless { message ->
                        message ==
                            state.tableCodeValidationMessage
                    }
                    ?.let { message ->
                        Text(
                            modifier = Modifier.semantics {
                                liveRegion =
                                    LiveRegionMode.Assertive
                            },
                            text = message,
                        )
                    }

                state.feedbackMessage?.let { message ->
                    Text(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        text = message,
                    )
                }
            }
        }
    }
}

@Preview(
    name = "Conta desconectada",
    showBackground = true,
    backgroundColor = 0xFF08275C,
)
@Composable
private fun OnlineAccountDialogDisconnectedPreview() {
    DominoPernambucanoTheme {
        OnlineAccountDialog(
            status = OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
            actionInProgress = false,
            feedbackMessage = null,
            profileState = OnlineAccountProfileUiState.NotAvailable,
            onPublicDisplayNameChange = {},
            onTableNameChange = {},
            onSaveProfileClick = {},
            onRetryProfileClick = {},
            onConnectGoogleClick = {},
            onDismissRequest = {},
        )
    }
}

@Preview(
    name = "Conta conectada carregando",
    showBackground = true,
    backgroundColor = 0xFF08275C,
)
@Composable
private fun OnlineAccountDialogLoadingPreview() {
    DominoPernambucanoTheme {
        OnlineAccountDialog(
            status = OnlineGoogleAccountStatus.CONNECTED,
            actionInProgress = false,
            feedbackMessage = null,
            profileState = OnlineAccountProfileUiState.Loading,
            onPublicDisplayNameChange = {},
            onTableNameChange = {},
            onSaveProfileClick = {},
            onRetryProfileClick = {},
            onConnectGoogleClick = {},
            onDismissRequest = {},
        )
    }
}

private data class OnlineAccountPresentation(
    val message: String,
    val actionLabel: String?,
)

private data class OnlineAccountPresentationStrings(
    val googleUnavailable: String,
    val googleRecoverDescription: String,
    val googleContinue: String,
    val googleLinkDescription: String,
    val googleLink: String,
    val profilePreserved: String,
    val googleReauthenticateDescription: String,
    val googleRecover: String,
)

private fun OnlineGoogleAccountStatus.toPresentation(
    strings: OnlineAccountPresentationStrings,
):
    OnlineAccountPresentation {
    return when (this) {
        OnlineGoogleAccountStatus.UNAVAILABLE ->
            OnlineAccountPresentation(
                message = strings.googleUnavailable,
                actionLabel = null,
            )

        OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL ->
            OnlineAccountPresentation(
                message = strings.googleRecoverDescription,
                actionLabel = strings.googleContinue,
            )

        OnlineGoogleAccountStatus.VISITOR ->
            OnlineAccountPresentation(
                message = strings.googleLinkDescription,
                actionLabel = strings.googleLink,
            )

        OnlineGoogleAccountStatus.CONNECTED ->
            OnlineAccountPresentation(
                message = strings.profilePreserved,
                actionLabel = null,
            )

        OnlineGoogleAccountStatus.RECOVERY_REQUIRED ->
            OnlineAccountPresentation(
                message = strings.googleReauthenticateDescription,
                actionLabel = strings.googleRecover,
            )
    }
}
