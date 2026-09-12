package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlinePasswordAccountActionResult
import com.ahtohiofilho.dominopernambucano.online.OnlinePasswordAccountFailureReason
import com.ahtohiofilho.dominopernambucano.online.OnlinePasswordAccountManager
import com.ahtohiofilho.dominopernambucano.online.OnlinePasswordVerificationPurpose
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing
import kotlinx.coroutines.launch

internal const val RankedAccountEntryScreenTag =
    "ranked_account_entry_screen"
internal const val RankedAccountEntryGoogleActionTag =
    "ranked_account_entry_google_action"
internal const val RankedAccountEntryModeActionTag =
    "ranked_account_entry_mode_action"
internal const val RankedAccountEntryEmailTag =
    "ranked_account_entry_email"
internal const val RankedAccountEntryPasswordTag =
    "ranked_account_entry_password"
internal const val RankedAccountEntryPrimaryActionTag =
    "ranked_account_entry_primary_action"
internal const val RankedAccountEntryForgotPasswordTag =
    "ranked_account_entry_forgot_password"

private val AuthFieldShape = RoundedCornerShape(16.dp)
private val AuthActionShape = RoundedCornerShape(16.dp)

enum class RankedAccountEntryMode {
    SIGN_IN,
    CREATE_ACCOUNT,
    VERIFY_CREATE_ACCOUNT,
    FORGOT_PASSWORD,
    RESET_PASSWORD,
}

@Composable
fun RankedAccountEntryScreen(
    mode: RankedAccountEntryMode,
    actionInProgress: Boolean,
    feedbackMessage: String?,
    googleAvailable: Boolean = true,
    passwordAccountManager: OnlinePasswordAccountManager,
    onContinueGoogleClick: () -> Unit,
    onAuthenticated: suspend () -> Unit,
    onModeChange: (RankedAccountEntryMode) -> Unit,
    onBackClick: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localActionInProgress by rememberSaveable {
        mutableStateOf(false)
    }
    var localFeedback by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var verifiedEmail by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val coroutineScope = rememberCoroutineScope()
    val busy = actionInProgress || localActionInProgress

    val title = when (mode) {
        RankedAccountEntryMode.SIGN_IN ->
            stringResource(R.string.ranked_auth_sign_in_title)
        RankedAccountEntryMode.CREATE_ACCOUNT ->
            stringResource(R.string.ranked_auth_create_title)
        RankedAccountEntryMode.VERIFY_CREATE_ACCOUNT ->
            stringResource(R.string.ranked_auth_verify_create_title)
        RankedAccountEntryMode.FORGOT_PASSWORD ->
            stringResource(R.string.ranked_auth_forgot_title)
        RankedAccountEntryMode.RESET_PASSWORD ->
            stringResource(R.string.ranked_auth_reset_title)
    }

    val description = when (mode) {
        RankedAccountEntryMode.SIGN_IN ->
            stringResource(R.string.ranked_auth_sign_in_description)
        RankedAccountEntryMode.CREATE_ACCOUNT ->
            stringResource(R.string.ranked_auth_create_description)
        RankedAccountEntryMode.VERIFY_CREATE_ACCOUNT ->
            stringResource(
                R.string.ranked_auth_verify_create_description,
                verifiedEmail ?: email,
            )
        RankedAccountEntryMode.FORGOT_PASSWORD ->
            stringResource(R.string.ranked_auth_forgot_description)
        RankedAccountEntryMode.RESET_PASSWORD ->
            stringResource(
                R.string.ranked_auth_reset_description,
                verifiedEmail ?: email,
            )
    }

    val passwordMismatchMessage = stringResource(
        R.string.ranked_auth_password_mismatch,
    )
    val failureMessages = mapOf(
        OnlinePasswordAccountFailureReason.INVALID_EMAIL to
            stringResource(R.string.ranked_auth_invalid_email),
        OnlinePasswordAccountFailureReason.INVALID_PASSWORD to
            stringResource(R.string.ranked_auth_invalid_password),
        OnlinePasswordAccountFailureReason.INVALID_CODE to
            stringResource(R.string.ranked_auth_invalid_code),
        OnlinePasswordAccountFailureReason.INVALID_CREDENTIALS to
            stringResource(R.string.ranked_auth_invalid_credentials),
        OnlinePasswordAccountFailureReason.ACCOUNT_EXISTS to
            stringResource(R.string.ranked_auth_account_exists),
        OnlinePasswordAccountFailureReason.ACCOUNT_NOT_FOUND to
            stringResource(R.string.ranked_auth_account_not_found),
        OnlinePasswordAccountFailureReason.RATE_LIMITED to
            stringResource(R.string.ranked_auth_rate_limited),
        OnlinePasswordAccountFailureReason.SERVICE_UNAVAILABLE to
            stringResource(R.string.ranked_auth_service_unavailable),
        OnlinePasswordAccountFailureReason.SESSION_CONFLICT to
            stringResource(R.string.ranked_auth_session_conflict),
        OnlinePasswordAccountFailureReason.SESSION_EXPIRED to
            stringResource(R.string.ranked_auth_session_expired),
        OnlinePasswordAccountFailureReason.LOCAL_PERSISTENCE to
            stringResource(R.string.ranked_auth_local_persistence),
        OnlinePasswordAccountFailureReason.UNKNOWN to
            stringResource(R.string.ranked_auth_unknown_failure),
    )
    val visibleFeedback = localFeedback ?: feedbackMessage

    fun switchMode(nextMode: RankedAccountEntryMode) {
        if (busy) return
        localFeedback = null
        verificationCode = ""
        confirmPassword = ""
        password = ""
        passwordVisible = false
        if (
            nextMode == RankedAccountEntryMode.SIGN_IN ||
            nextMode == RankedAccountEntryMode.CREATE_ACCOUNT
        ) {
            verifiedEmail = null
        }
        onModeChange(nextMode)
    }

    fun runAction(
        block: suspend () -> OnlinePasswordAccountActionResult,
        onCodeRequested: ((String) -> Unit)? = null,
    ) {
        if (busy) return
        localActionInProgress = true
        localFeedback = null
        coroutineScope.launch {
            try {
                when (val result = block()) {
                    OnlinePasswordAccountActionResult.Success -> {
                        localFeedback = null
                        onAuthenticated()
                    }
                    is OnlinePasswordAccountActionResult.CodeRequested -> {
                        verifiedEmail = result.email
                        localFeedback = null
                        onCodeRequested?.invoke(result.email)
                    }
                    is OnlinePasswordAccountActionResult.Failure -> {
                        localFeedback = failureMessages[result.reason]
                    }
                }
            } finally {
                localActionInProgress = false
            }
        }
    }

    DominoScreenScaffold(
        title = title,
        layout = DominoScreenLayout.Centered,
        onBackClick = {
            if (!busy) {
                when (mode) {
                    RankedAccountEntryMode.SIGN_IN,
                    RankedAccountEntryMode.CREATE_ACCOUNT -> onBackClick()
                    RankedAccountEntryMode.VERIFY_CREATE_ACCOUNT ->
                        switchMode(RankedAccountEntryMode.CREATE_ACCOUNT)
                    RankedAccountEntryMode.FORGOT_PASSWORD,
                    RankedAccountEntryMode.RESET_PASSWORD ->
                        switchMode(RankedAccountEntryMode.SIGN_IN)
                }
            }
        },
        backContentDescription = stringResource(R.string.common_back),
        statusMessage = visibleFeedback,
        contentModifier = Modifier.testTag(RankedAccountEntryScreenTag),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            color = DominoSemanticColors.dialogSurface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.sm,
                ),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(78.dp),
                )

                Text(
                    text = description,
                    color = DominoSemanticColors.dialogBody,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                Spacer(
                    modifier = Modifier.height(
                        MaterialTheme.dominoSpacing.xs,
                    ),
                )

                when (mode) {
            RankedAccountEntryMode.SIGN_IN -> {
                AuthEmailField(
                    email = email,
                    onEmailChange = {
                        email = it
                        localFeedback = null
                    },
                    enabled = !busy,
                )
                AuthPasswordField(
                    password = password,
                    onPasswordChange = {
                        password = it
                        localFeedback = null
                    },
                    passwordVisible = passwordVisible,
                    onToggleVisibility = {
                        passwordVisible = !passwordVisible
                    },
                    enabled = !busy,
                )

                Text(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .testTag(RankedAccountEntryForgotPasswordTag)
                        .clickable(
                            enabled = !busy,
                            role = Role.Button,
                        ) {
                            switchMode(RankedAccountEntryMode.FORGOT_PASSWORD)
                        },
                    text = stringResource(R.string.ranked_auth_forgot_password),
                    color = DominoSemanticColors.dialogAction,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                AuthPrimaryButton(
                    text = if (localActionInProgress) {
                        stringResource(R.string.ranked_auth_signing_in)
                    } else {
                        stringResource(R.string.ranked_auth_primary_sign_in)
                    },
                    enabled = !busy && passwordAccountManager.isAvailable,
                    onClick = {
                        runAction(
                            block = {
                                passwordAccountManager.signIn(
                                    rawEmail = email,
                                    rawPassword = password,
                                )
                            },
                        )
                    },
                )

                AuthDivider()
                GoogleButton(
                    enabled = !busy && googleAvailable,
                    actionInProgress = actionInProgress,
                    onClick = onContinueGoogleClick,
                )
                AuthModePrompt(
                    prompt = stringResource(R.string.ranked_auth_new_prompt),
                    action = stringResource(R.string.ranked_auth_join_action),
                    enabled = !busy,
                    onClick = {
                        switchMode(RankedAccountEntryMode.CREATE_ACCOUNT)
                    },
                )
            }

            RankedAccountEntryMode.CREATE_ACCOUNT -> {
                AuthEmailField(
                    email = email,
                    onEmailChange = {
                        email = it
                        localFeedback = null
                    },
                    enabled = !busy,
                )
                AuthPasswordField(
                    password = password,
                    onPasswordChange = {
                        password = it
                        localFeedback = null
                    },
                    passwordVisible = passwordVisible,
                    onToggleVisibility = {
                        passwordVisible = !passwordVisible
                    },
                    enabled = !busy,
                    supportingText = stringResource(
                        R.string.ranked_auth_password_hint,
                    ),
                )
                AuthPasswordField(
                    password = confirmPassword,
                    onPasswordChange = {
                        confirmPassword = it
                        localFeedback = null
                    },
                    passwordVisible = passwordVisible,
                    onToggleVisibility = {
                        passwordVisible = !passwordVisible
                    },
                    enabled = !busy,
                    label = stringResource(
                        R.string.ranked_auth_confirm_password,
                    ),
                    testTag = "ranked_account_entry_confirm_password",
                )

                AuthPrimaryButton(
                    text = if (localActionInProgress) {
                        stringResource(R.string.ranked_auth_sending_code)
                    } else {
                        stringResource(R.string.ranked_auth_primary_continue)
                    },
                    enabled = !busy && passwordAccountManager.isAvailable,
                    onClick = {
                        if (password != confirmPassword) {
                            localFeedback = passwordMismatchMessage
                        } else {
                            runAction(
                                block = {
                                    passwordAccountManager
                                        .requestVerificationCode(
                                            rawEmail = email,
                                            purpose =
                                                OnlinePasswordVerificationPurpose
                                                    .CREATE_ACCOUNT,
                                        )
                                },
                                onCodeRequested = {
                                    onModeChange(
                                        RankedAccountEntryMode
                                            .VERIFY_CREATE_ACCOUNT,
                                    )
                                },
                            )
                        }
                    },
                )

                AuthDivider()
                GoogleButton(
                    enabled = !busy && googleAvailable,
                    actionInProgress = actionInProgress,
                    onClick = onContinueGoogleClick,
                )
                AuthModePrompt(
                    prompt = stringResource(
                        R.string.ranked_auth_already_prompt,
                    ),
                    action = stringResource(
                        R.string.ranked_auth_sign_in_action,
                    ),
                    enabled = !busy,
                    onClick = {
                        switchMode(RankedAccountEntryMode.SIGN_IN)
                    },
                )
            }

            RankedAccountEntryMode.VERIFY_CREATE_ACCOUNT -> {
                AuthCodeField(
                    code = verificationCode,
                    onCodeChange = {
                        verificationCode = it.filter(Char::isDigit).take(6)
                        localFeedback = null
                    },
                    enabled = !busy,
                )
                AuthPrimaryButton(
                    text = if (localActionInProgress) {
                        stringResource(R.string.ranked_auth_creating)
                    } else {
                        stringResource(R.string.ranked_auth_finish_create)
                    },
                    enabled = !busy && passwordAccountManager.isAvailable,
                    onClick = {
                        runAction(
                            block = {
                                passwordAccountManager.createAccount(
                                    rawEmail = verifiedEmail ?: email,
                                    rawPassword = password,
                                    rawCode = verificationCode,
                                )
                            },
                        )
                    },
                )
                AuthModePrompt(
                    prompt = "",
                    action = stringResource(
                        R.string.ranked_auth_back_to_sign_in,
                    ),
                    enabled = !busy,
                    onClick = {
                        switchMode(RankedAccountEntryMode.SIGN_IN)
                    },
                )
            }

            RankedAccountEntryMode.FORGOT_PASSWORD -> {
                AuthEmailField(
                    email = email,
                    onEmailChange = {
                        email = it
                        localFeedback = null
                    },
                    enabled = !busy,
                )
                AuthPrimaryButton(
                    text = if (localActionInProgress) {
                        stringResource(R.string.ranked_auth_sending_code)
                    } else {
                        stringResource(R.string.ranked_auth_send_code)
                    },
                    enabled = !busy && passwordAccountManager.isAvailable,
                    onClick = {
                        runAction(
                            block = {
                                passwordAccountManager
                                    .requestVerificationCode(
                                        rawEmail = email,
                                        purpose =
                                            OnlinePasswordVerificationPurpose
                                                .RESET_PASSWORD,
                                    )
                            },
                            onCodeRequested = {
                                onModeChange(
                                    RankedAccountEntryMode.RESET_PASSWORD,
                                )
                            },
                        )
                    },
                )
                AuthModePrompt(
                    prompt = "",
                    action = stringResource(
                        R.string.ranked_auth_back_to_sign_in,
                    ),
                    enabled = !busy,
                    onClick = {
                        switchMode(RankedAccountEntryMode.SIGN_IN)
                    },
                )
            }

            RankedAccountEntryMode.RESET_PASSWORD -> {
                AuthCodeField(
                    code = verificationCode,
                    onCodeChange = {
                        verificationCode = it.filter(Char::isDigit).take(6)
                        localFeedback = null
                    },
                    enabled = !busy,
                )
                AuthPasswordField(
                    password = password,
                    onPasswordChange = {
                        password = it
                        localFeedback = null
                    },
                    passwordVisible = passwordVisible,
                    onToggleVisibility = {
                        passwordVisible = !passwordVisible
                    },
                    enabled = !busy,
                    supportingText = stringResource(
                        R.string.ranked_auth_password_hint,
                    ),
                )
                AuthPasswordField(
                    password = confirmPassword,
                    onPasswordChange = {
                        confirmPassword = it
                        localFeedback = null
                    },
                    passwordVisible = passwordVisible,
                    onToggleVisibility = {
                        passwordVisible = !passwordVisible
                    },
                    enabled = !busy,
                    label = stringResource(
                        R.string.ranked_auth_confirm_password,
                    ),
                    testTag = "ranked_account_entry_confirm_password",
                )
                AuthPrimaryButton(
                    text = if (localActionInProgress) {
                        stringResource(R.string.ranked_auth_resetting)
                    } else {
                        stringResource(R.string.ranked_auth_reset_action)
                    },
                    enabled = !busy && passwordAccountManager.isAvailable,
                    onClick = {
                        if (password != confirmPassword) {
                            localFeedback = passwordMismatchMessage
                        } else {
                            runAction(
                                block = {
                                    passwordAccountManager.resetPassword(
                                        rawEmail = verifiedEmail ?: email,
                                        rawPassword = password,
                                        rawCode = verificationCode,
                                    )
                                },
                            )
                        }
                    },
                )
                AuthModePrompt(
                    prompt = "",
                    action = stringResource(
                        R.string.ranked_auth_back_to_sign_in,
                    ),
                    enabled = !busy,
                    onClick = {
                        switchMode(RankedAccountEntryMode.SIGN_IN)
                    },
                )
            }
        }
            }
        }
    }
}

@Composable
private fun AuthEmailField(
    email: String,
    onEmailChange: (String) -> Unit,
    enabled: Boolean,
) {
    AuthTextField(
        value = email,
        onValueChange = onEmailChange,
        label = stringResource(R.string.ranked_auth_email),
        enabled = enabled,
        keyboardType = KeyboardType.Email,
        testTag = RankedAccountEntryEmailTag,
    )
}

@Composable
private fun AuthPasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean,
    label: String? = null,
    supportingText: String? = null,
    testTag: String = RankedAccountEntryPasswordTag,
) {
    val resolvedLabel = label ?: stringResource(
        R.string.ranked_auth_password,
    )
    val resolvedSupportingText = supportingText

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        enabled = enabled,
        singleLine = true,
        label = { Text(resolvedLabel) },
        supportingText = if (resolvedSupportingText != null) {
            { Text(resolvedSupportingText) }
        } else {
            null
        },
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
        ),
        trailingIcon = {
            TextButton(
                enabled = enabled,
                onClick = onToggleVisibility,
            ) {
                Text(
                    text = stringResource(
                        if (passwordVisible) {
                            R.string.ranked_auth_hide_password
                        } else {
                            R.string.ranked_auth_show_password
                        },
                    ),
                )
            }
        },
        shape = AuthFieldShape,
        colors = authTextFieldColors(),
    )
}

@Composable
private fun AuthCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean,
) {
    AuthTextField(
        value = code,
        onValueChange = onCodeChange,
        label = stringResource(R.string.ranked_auth_code),
        enabled = enabled,
        keyboardType = KeyboardType.Number,
        testTag = "ranked_account_entry_code",
    )
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = AuthFieldShape,
        colors = authTextFieldColors(),
    )
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DominoSemanticColors.dialogTitle,
    unfocusedTextColor = DominoSemanticColors.dialogTitle,
    disabledTextColor = DominoSemanticColors.dialogBody.copy(alpha = 0.52f),
    focusedBorderColor = DominoSemanticColors.dialogAction,
    unfocusedBorderColor = DominoSemanticColors.dialogBody.copy(
        alpha = 0.52f,
    ),
    disabledBorderColor = DominoSemanticColors.dialogBody.copy(
        alpha = 0.24f,
    ),
    focusedLabelColor = DominoSemanticColors.dialogAction,
    unfocusedLabelColor = DominoSemanticColors.dialogBody,
    disabledLabelColor = DominoSemanticColors.dialogBody.copy(
        alpha = 0.48f,
    ),
    cursorColor = DominoSemanticColors.dialogAction,
)

@Composable
private fun AuthPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(RankedAccountEntryPrimaryActionTag),
        onClick = onClick,
        enabled = enabled,
        shape = AuthActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = DominoSemanticColors.dialogAction,
            contentColor = DominoSemanticColors.dialogActionContent,
            disabledContainerColor = DominoSemanticColors.dialogDisabledAction,
            disabledContentColor =
                DominoSemanticColors.dialogDisabledActionContent,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AuthDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    DominoSemanticColors.dialogBody.copy(
                        alpha = 0.34f,
                    ),
                ),
        )
        Text(
            modifier = Modifier.padding(horizontal = 12.dp),
            text = stringResource(R.string.ranked_auth_or),
            color = DominoSemanticColors.dialogBody,
            style = MaterialTheme.typography.bodySmall,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    DominoSemanticColors.dialogBody.copy(
                        alpha = 0.34f,
                    ),
                ),
        )
    }
}

@Composable
private fun GoogleButton(
    enabled: Boolean,
    actionInProgress: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(RankedAccountEntryGoogleActionTag),
        onClick = onClick,
        enabled = enabled,
        shape = AuthActionShape,
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFFDADCE0),
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color.White.copy(alpha = 0.62f),
            disabledContentColor = Color(0xFF1F1F1F).copy(alpha = 0.50f),
        ),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_g),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (actionInProgress) {
                stringResource(R.string.account_connecting)
            } else {
                stringResource(R.string.account_continue_google)
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AuthModePrompt(
    prompt: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prompt.isNotBlank()) {
            Text(
                text = prompt,
                color = DominoSemanticColors.dialogBody,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            modifier = Modifier
                .testTag(RankedAccountEntryModeActionTag)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            text = action,
            color = DominoSemanticColors.dialogAction,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
