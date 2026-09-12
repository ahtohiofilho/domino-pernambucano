package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.BuildConfig
import com.ahtohiofilho.dominopernambucano.online.MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH
import com.ahtohiofilho.dominopernambucano.online.ONLINE_ACCOUNT_TABLE_CODE_LENGTH
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailAccountIntent
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
internal const val OnlineAccountEmailFieldTag =
    "online_account_email_field"
internal const val OnlineAccountEmailCodeFieldTag =
    "online_account_email_code_field"
internal const val OnlineAccountEmailPrimaryActionTag =
    "online_account_email_primary_action"

internal enum class OnlineAccountEntryMode {
    CREATE_ACCOUNT,
    SIGN_IN,
}

internal fun defaultOnlineAccountEntryMode(
    status: OnlineGoogleAccountStatus,
): OnlineAccountEntryMode {
    return if (
        status == OnlineGoogleAccountStatus.RECOVERY_REQUIRED
    ) {
        OnlineAccountEntryMode.SIGN_IN
    } else {
        OnlineAccountEntryMode.CREATE_ACCOUNT
    }
}

internal fun accountEntryModeButtonsShouldStack(
    availableWidthDp: Float,
): Boolean {
    return availableWidthDp < 320f
}
internal fun onlineAccountEmailIntentFor(
    mode: OnlineAccountEntryMode,
): OnlineEmailAccountIntent {
    return when (mode) {
        OnlineAccountEntryMode.CREATE_ACCOUNT ->
            OnlineEmailAccountIntent.LINK
        OnlineAccountEntryMode.SIGN_IN ->
            OnlineEmailAccountIntent.RECOVER
    }
}
internal fun onlineEmailAccountAvailableIntents(
    status: OnlineGoogleAccountStatus,
    emailAvailable: Boolean,
): List<OnlineEmailAccountIntent> {
    if (!emailAvailable) {
        return emptyList()
    }

    return when (status) {
        OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL ->
            listOf(
                OnlineEmailAccountIntent.LINK,
                OnlineEmailAccountIntent.RECOVER,
            )

        OnlineGoogleAccountStatus.VISITOR,
        OnlineGoogleAccountStatus.CONNECTED ->
            listOf(OnlineEmailAccountIntent.LINK)

        OnlineGoogleAccountStatus.RECOVERY_REQUIRED ->
            listOf(OnlineEmailAccountIntent.RECOVER)

        OnlineGoogleAccountStatus.UNAVAILABLE ->
            emptyList()
    }
}

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
    googleAvailable: Boolean = true,
    emailAvailable: Boolean = false,
    emailAddress: String = "",
    emailCode: String = "",
    emailIntent: OnlineEmailAccountIntent? = null,
    emailCodeRequested: Boolean = false,
    emailActionInProgress: Boolean = false,
    emailFeedbackMessage: String? = null,
    profileState: OnlineAccountProfileUiState,
    onPublicDisplayNameChange: (String) -> Unit,
    onTableNameChange: (String) -> Unit,
    onSaveProfileClick: () -> Unit,
    onRetryProfileClick: () -> Unit,
    onEmailAddressChange: (String) -> Unit = {},
    onEmailCodeChange: (String) -> Unit = {},
    onEmailStartLinkClick: () -> Unit = {},
    onEmailStartRecoverClick: () -> Unit = {},
    onEmailConfirmCodeClick: () -> Unit = {},
    onEmailResetClick: () -> Unit = {},
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
        actionInProgress ||
            emailActionInProgress ||
            profileActionInProgress
    val visibleFeedbackMessage =
        onlineAccountDialogVisibleFeedback(
            status = status,
            feedbackMessage = feedbackMessage,
        )
    if (status != OnlineGoogleAccountStatus.CONNECTED) {
        OnlineAccountEntrySurface(
            status = status,
            actionInProgress = actionInProgress,
            visibleFeedbackMessage = visibleFeedbackMessage,
            googleAvailable = googleAvailable,
            emailAvailable = emailAvailable,
            emailAddress = emailAddress,
            emailCode = emailCode,
            emailCodeRequested = emailCodeRequested,
            emailActionInProgress = emailActionInProgress,
            emailFeedbackMessage = emailFeedbackMessage,
            onEmailAddressChange = onEmailAddressChange,
            onEmailCodeChange = onEmailCodeChange,
            onEmailStartLinkClick = onEmailStartLinkClick,
            onEmailStartRecoverClick = onEmailStartRecoverClick,
            onEmailConfirmCodeClick = onEmailConfirmCodeClick,
            onEmailResetClick = onEmailResetClick,
            onConnectGoogleClick = onConnectGoogleClick,
            onDismissRequest = onDismissRequest,
        )
        return
    }

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

                emailFeedbackMessage?.let { message ->
                    Text(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        text = message,
                    )
                }

                OnlineEmailAccountContent(
                    status = status,
                    emailAvailable = emailAvailable,
                    emailAddress = emailAddress,
                    emailCode = emailCode,
                    emailIntent = emailIntent,
                    emailCodeRequested = emailCodeRequested,
                    actionInProgress = emailActionInProgress,
                    onEmailAddressChange = onEmailAddressChange,
                    onEmailCodeChange = onEmailCodeChange,
                    onEmailStartLinkClick = onEmailStartLinkClick,
                    onEmailStartRecoverClick =
                        onEmailStartRecoverClick,
                    onEmailConfirmCodeClick =
                        onEmailConfirmCodeClick,
                    onEmailResetClick = onEmailResetClick,
                )

                if (
                    googleAvailable &&
                    emailAvailable &&
                    !emailCodeRequested &&
                    status != OnlineGoogleAccountStatus.CONNECTED
                ) {
                    Text(
                        text = stringResource(
                            R.string.account_email_or_google,
                        ),
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
                googleAvailable &&
                    status != OnlineGoogleAccountStatus.CONNECTED &&
                    !emailCodeRequested -> {
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
                        text = when {
                            editor.actionInProgress ->
                                stringResource(R.string.account_saving)

                            editor.saveSucceeded ->
                                stringResource(
                                    R.string.account_profile_saved_action,
                                )

                            else ->
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
private fun OnlineAccountEntrySurface(
    status: OnlineGoogleAccountStatus,
    actionInProgress: Boolean,
    visibleFeedbackMessage: String?,
    googleAvailable: Boolean,
    emailAvailable: Boolean,
    emailAddress: String,
    emailCode: String,
    emailCodeRequested: Boolean,
    emailActionInProgress: Boolean,
    emailFeedbackMessage: String?,
    onEmailAddressChange: (String) -> Unit,
    onEmailCodeChange: (String) -> Unit,
    onEmailStartLinkClick: () -> Unit,
    onEmailStartRecoverClick: () -> Unit,
    onEmailConfirmCodeClick: () -> Unit,
    onEmailResetClick: () -> Unit,
    onConnectGoogleClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val anyActionInProgress =
        actionInProgress || emailActionInProgress
    val emailEntryVisible =
        emailAvailable || BuildConfig.DEBUG

    var entryModeName by rememberSaveable(status) {
        mutableStateOf(
            defaultOnlineAccountEntryMode(status).name,
        )
    }
    val entryMode = OnlineAccountEntryMode.valueOf(
        entryModeName,
    )

    Dialog(
        onDismissRequest = {
            if (!anyActionInProgress) {
                onDismissRequest()
            }
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .testTag(OnlineAccountDialogTag),
            shape = RoundedCornerShape(24.dp),
            color = DominoSemanticColors.dialogSurface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 28.dp,
                        end = 28.dp,
                        top = 26.dp,
                        bottom = 22.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.sm,
                ),
            ) {
                Image(
                    painter = painterResource(
                        R.drawable.ic_launcher_foreground,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )

                Text(
                    modifier = Modifier
                        .testTag(OnlineAccountTitleTag)
                        .semantics {
                            heading()
                        },
                    text = if (emailCodeRequested) {
                        stringResource(
                            R.string.account_entry_code_title,
                        )
                    } else {
                        stringResource(
                            R.string.account_entry_title,
                        )
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = DominoSemanticColors.dialogTitle,
                    textAlign = TextAlign.Center,
                )

                if (emailCodeRequested) {
                    Text(
                        text = stringResource(
                            R.string.account_entry_code_description,
                            emailAddress,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DominoSemanticColors.dialogBody,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    DominoOutlinedTextField(
                        value = emailCode,
                        onValueChange = onEmailCodeChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                OnlineAccountEmailCodeFieldTag,
                            ),
                        enabled = !emailActionInProgress,
                        label = stringResource(
                            R.string.account_email_code,
                        ),
                        supportingText = stringResource(
                            R.string.account_email_code_hint,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        tone = DominoTextFieldTone.OnLight,
                    )

                    DominoPrimaryButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                OnlineAccountEmailPrimaryActionTag,
                            ),
                        text = if (emailActionInProgress) {
                            stringResource(
                                R.string.account_email_verifying,
                            )
                        } else {
                            stringResource(
                                R.string.account_email_confirm,
                            )
                        },
                        onClick = onEmailConfirmCodeClick,
                        enabled =
                            !emailActionInProgress &&
                                emailCode.length == 6,
                        loading = emailActionInProgress,
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

                    DominoTextAction(
                        text = stringResource(
                            R.string.account_email_use_another,
                        ),
                        onClick = onEmailResetClick,
                        enabled = !emailActionInProgress,
                        contentColor =
                            DominoSemanticColors.dialogDismissAction,
                        disabledContentColor =
                            DominoSemanticColors
                                .dialogDisabledDismissAction,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.account_entry_description,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DominoSemanticColors.dialogBody,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (emailEntryVisible) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val stackModeActions =
                                accountEntryModeButtonsShouldStack(
                                    maxWidth.value,
                                )

                            if (stackModeActions) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement =
                                        Arrangement.spacedBy(8.dp),
                                ) {
                                    OnlineAccountEntryModeButton(
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        selected =
                                            entryMode ==
                                                OnlineAccountEntryMode
                                                    .CREATE_ACCOUNT,
                                        text = stringResource(
                                            R.string
                                                .account_entry_create_account,
                                        ),
                                        enabled =
                                            !emailActionInProgress,
                                        onClick = {
                                            entryModeName =
                                                OnlineAccountEntryMode
                                                    .CREATE_ACCOUNT
                                                    .name
                                        },
                                    )

                                    OnlineAccountEntryModeButton(
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        selected =
                                            entryMode ==
                                                OnlineAccountEntryMode
                                                    .SIGN_IN,
                                        text = stringResource(
                                            R.string
                                                .account_entry_sign_in,
                                        ),
                                        enabled =
                                            !emailActionInProgress,
                                        onClick = {
                                            entryModeName =
                                                OnlineAccountEntryMode
                                                    .SIGN_IN
                                                    .name
                                        },
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(8.dp),
                                ) {
                                    OnlineAccountEntryModeButton(
                                        modifier =
                                            Modifier.weight(1f),
                                        selected =
                                            entryMode ==
                                                OnlineAccountEntryMode
                                                    .CREATE_ACCOUNT,
                                        text = stringResource(
                                            R.string
                                                .account_entry_create_account,
                                        ),
                                        enabled =
                                            !emailActionInProgress,
                                        onClick = {
                                            entryModeName =
                                                OnlineAccountEntryMode
                                                    .CREATE_ACCOUNT
                                                    .name
                                        },
                                    )

                                    OnlineAccountEntryModeButton(
                                        modifier =
                                            Modifier.weight(1f),
                                        selected =
                                            entryMode ==
                                                OnlineAccountEntryMode
                                                    .SIGN_IN,
                                        text = stringResource(
                                            R.string
                                                .account_entry_sign_in,
                                        ),
                                        enabled =
                                            !emailActionInProgress,
                                        onClick = {
                                            entryModeName =
                                                OnlineAccountEntryMode
                                                    .SIGN_IN
                                                    .name
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        DominoOutlinedTextField(
                            value = emailAddress,
                            onValueChange = onEmailAddressChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(
                                    OnlineAccountEmailFieldTag,
                                ),
                            enabled = !emailActionInProgress,
                            label = stringResource(
                                R.string.account_email_address,
                            ),
                            supportingText = null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                            ),
                            tone = DominoTextFieldTone.OnLight,
                        )

                        DominoPrimaryButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(
                                    OnlineAccountEmailPrimaryActionTag,
                                ),
                            text = if (emailActionInProgress) {
                                stringResource(
                                    R.string.account_email_sending_code,
                                )
                            } else {
                                stringResource(
                                    R.string.account_entry_continue,
                                )
                            },
                            onClick = {
                                when (
                                    onlineAccountEmailIntentFor(
                                        entryMode,
                                    )
                                ) {
                                    OnlineEmailAccountIntent.LINK ->
                                        onEmailStartLinkClick()
                                    OnlineEmailAccountIntent.RECOVER ->
                                        onEmailStartRecoverClick()
                                }
                            },
                            enabled =
                                !emailActionInProgress &&
                                    emailAddress.isNotBlank(),
                            loading = emailActionInProgress,
                            containerColor =
                                DominoSemanticColors.dialogAction,
                            contentColor =
                                DominoSemanticColors
                                    .dialogActionContent,
                            disabledContainerColor =
                                DominoSemanticColors
                                    .dialogDisabledAction,
                            disabledContentColor =
                                DominoSemanticColors
                                    .dialogDisabledActionContent,
                        )
                    }

                    if (emailEntryVisible) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme
                                    .colorScheme
                                    .outline
                                    .copy(alpha = 0.45f),
                            )
                            Text(
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                ),
                                text = stringResource(
                                    R.string.account_entry_or,
                                ),
                                style =
                                    MaterialTheme.typography.bodySmall,
                                color =
                                    DominoSemanticColors.dialogBody,
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme
                                    .colorScheme
                                    .outline
                                    .copy(alpha = 0.45f),
                            )
                        }
                    }

                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag(
                                OnlineAccountPrimaryActionTag,
                            ),
                        onClick = onConnectGoogleClick,
                        enabled =
                            googleAvailable &&
                                !anyActionInProgress,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Image(
                            painter = painterResource(
                                R.drawable.ic_google_g,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (actionInProgress) {
                                stringResource(
                                    R.string.account_connecting,
                                )
                            } else {
                                stringResource(
                                    R.string.account_continue_google,
                                )
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                visibleFeedbackMessage?.let { message ->
                    Text(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = DominoSemanticColors.dialogBody,
                        textAlign = TextAlign.Center,
                    )
                }

                emailFeedbackMessage?.let { message ->
                    Text(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = DominoSemanticColors.dialogBody,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

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
                        DominoSemanticColors
                            .dialogDisabledDismissAction,
                )
            }
        }
    }
}

@Composable
private fun OnlineAccountEntryModeButton(
    modifier: Modifier,
    selected: Boolean,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier.height(48.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                DominoSemanticColors.dialogAction
            } else {
                MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.62f,
                )
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) {
                DominoSemanticColors.dialogAction
            } else {
                Color.Transparent
            },
            contentColor = if (selected) {
                DominoSemanticColors.dialogActionContent
            } else {
                DominoSemanticColors.dialogAction
            },
            disabledContentColor =
                DominoSemanticColors.dialogDisabledDismissAction,
        ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
@Composable
private fun OnlineEmailAccountContent(
    status: OnlineGoogleAccountStatus,
    emailAvailable: Boolean,
    emailAddress: String,
    emailCode: String,
    emailIntent: OnlineEmailAccountIntent?,
    emailCodeRequested: Boolean,
    actionInProgress: Boolean,
    onEmailAddressChange: (String) -> Unit,
    onEmailCodeChange: (String) -> Unit,
    onEmailStartLinkClick: () -> Unit,
    onEmailStartRecoverClick: () -> Unit,
    onEmailConfirmCodeClick: () -> Unit,
    onEmailResetClick: () -> Unit,
) {
    val intents = onlineEmailAccountAvailableIntents(
        status = status,
        emailAvailable = emailAvailable,
    )
    if (intents.isEmpty()) {
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(
            MaterialTheme.dominoSpacing.xs,
        ),
    ) {
        Text(
            text = stringResource(R.string.account_email_section),
            fontWeight = FontWeight.SemiBold,
        )

        if (emailCodeRequested && emailIntent != null) {
            Text(
                text = stringResource(
                    R.string.account_email_code_prompt,
                ),
            )

            DominoOutlinedTextField(
                value = emailCode,
                onValueChange = onEmailCodeChange,
                modifier = Modifier.testTag(
                    OnlineAccountEmailCodeFieldTag,
                ),
                enabled = !actionInProgress,
                label = stringResource(
                    R.string.account_email_code,
                ),
                supportingText = stringResource(
                    R.string.account_email_code_hint,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                tone = DominoTextFieldTone.OnLight,
            )

            DominoPrimaryButton(
                modifier = Modifier.testTag(
                    OnlineAccountEmailPrimaryActionTag,
                ),
                text = if (actionInProgress) {
                    stringResource(
                        R.string.account_email_verifying,
                    )
                } else {
                    stringResource(
                        R.string.account_email_confirm,
                    )
                },
                onClick = onEmailConfirmCodeClick,
                enabled =
                    !actionInProgress &&
                        emailCode.length == 6,
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

            DominoTextAction(
                text = stringResource(
                    R.string.account_email_use_another,
                ),
                onClick = onEmailResetClick,
                enabled = !actionInProgress,
                contentColor =
                    DominoSemanticColors.dialogDismissAction,
                disabledContentColor =
                    DominoSemanticColors.dialogDisabledDismissAction,
            )

            return@Column
        }

        DominoOutlinedTextField(
            value = emailAddress,
            onValueChange = onEmailAddressChange,
            modifier = Modifier.testTag(
                OnlineAccountEmailFieldTag,
            ),
            enabled = !actionInProgress,
            label = stringResource(
                R.string.account_email_address,
            ),
            supportingText = stringResource(
                R.string.account_email_privacy_hint,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
            ),
            tone = DominoTextFieldTone.OnLight,
        )

        if (OnlineEmailAccountIntent.LINK in intents) {
            DominoPrimaryButton(
                modifier = Modifier.testTag(
                    OnlineAccountEmailPrimaryActionTag,
                ),
                text = if (actionInProgress) {
                    stringResource(
                        R.string.account_email_sending_code,
                    )
                } else if (
                    status ==
                        OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL
                ) {
                    stringResource(
                        R.string.account_email_create,
                    )
                } else {
                    stringResource(
                        R.string.account_email_link,
                    )
                },
                onClick = onEmailStartLinkClick,
                enabled =
                    !actionInProgress &&
                        emailAddress.isNotBlank(),
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

        if (OnlineEmailAccountIntent.RECOVER in intents) {
            DominoTextAction(
                text = stringResource(
                    R.string.account_email_recover,
                ),
                onClick = onEmailStartRecoverClick,
                enabled =
                    !actionInProgress &&
                        emailAddress.isNotBlank(),
                contentColor =
                    DominoSemanticColors.dialogDismissAction,
                disabledContentColor =
                    DominoSemanticColors.dialogDisabledDismissAction,
            )
        }
    }
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
                            liveRegion = if (state.saveSucceeded) {
                                LiveRegionMode.Assertive
                            } else {
                                LiveRegionMode.Polite
                            }
                        },
                        text = if (state.saveSucceeded) {
                            "\u2713 $message"
                        } else {
                            message
                        },
                        color = if (state.saveSucceeded) {
                            DominoSemanticColors.brandPositive
                        } else {
                            DominoSemanticColors.dialogBody
                        },
                        fontWeight = if (state.saveSucceeded) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
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
