package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH
import com.ahtohiofilho.dominopernambucano.online.isValidOnlineAccountTableCode
import com.ahtohiofilho.dominopernambucano.online.normalizeOnlineAccountTableCodeInput
import com.ahtohiofilho.dominopernambucano.online.normalizeOnlinePublicDisplayName
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun RankedTableIdentityConfirmationDialog(
    initialPublicDisplayName: String,
    initialTableCode: String,
    requiresPublicDisplayName: Boolean,
    suggestedFromOffline: Boolean,
    actionInProgress: Boolean,
    feedbackMessage: String?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var publicDisplayName by remember(
        initialPublicDisplayName,
        requiresPublicDisplayName,
    ) {
        mutableStateOf(
            if (requiresPublicDisplayName) {
                ""
            } else {
                initialPublicDisplayName
            },
        )
    }

    var tableCode by remember(initialTableCode) {
        mutableStateOf(
            normalizeOnlineAccountTableCodeInput(initialTableCode),
        )
    }

    val normalizedCode =
        normalizeOnlineAccountTableCodeInput(tableCode)
    val normalizedPublicDisplayName =
        try {
            normalizeOnlinePublicDisplayName(
                rawName = publicDisplayName,
            )
        } catch (_: IllegalArgumentException) {
            null
        }

    val publicDisplayNameReady =
        !requiresPublicDisplayName ||
            normalizedPublicDisplayName != null

    val publicDisplayNameInvalid =
        requiresPublicDisplayName &&
            publicDisplayName.isNotBlank() &&
            normalizedPublicDisplayName == null

    val canConfirm =
        isValidOnlineAccountTableCode(normalizedCode) &&
            publicDisplayNameReady &&
            !actionInProgress

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = DominoSemanticColors.brandText,
        unfocusedTextColor = DominoSemanticColors.brandText,
        focusedBorderColor = DominoColorTokens.AccentYellow,
        unfocusedBorderColor =
            DominoSemanticColors.brandText.copy(alpha = 0.62f),
        cursorColor = DominoColorTokens.AccentYellow,
        focusedLabelColor = DominoColorTokens.AccentYellow,
        unfocusedLabelColor =
            DominoSemanticColors.brandSupportingText,
        focusedSupportingTextColor =
            DominoSemanticColors.brandSupportingText,
        unfocusedSupportingTextColor =
            DominoSemanticColors.brandSupportingText,
        focusedContainerColor =
            DominoSemanticColors.brandBackground.copy(alpha = 0.36f),
        unfocusedContainerColor =
            DominoSemanticColors.brandBackground.copy(alpha = 0.24f),
    )

    Dialog(
        onDismissRequest = {
            if (!actionInProgress) {
                onDismiss()
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = DominoSemanticColors.brandSurface,
            contentColor = DominoSemanticColors.brandText,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 24.dp,
                        vertical = 24.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (requiresPublicDisplayName) {
                            R.string
                                .ranked_table_identity_complete_title
                        } else {
                            R.string.ranked_table_identity_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = DominoSemanticColors.brandText,
                )

                Text(
                    text = stringResource(
                        if (requiresPublicDisplayName) {
                            R.string
                                .ranked_table_identity_complete_description
                        } else {
                            R.string.ranked_table_identity_description
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color =
                        DominoSemanticColors.brandSupportingText,
                )

                if (requiresPublicDisplayName) {
                    OutlinedTextField(
                        value = publicDisplayName,
                        onValueChange = { value ->
                            publicDisplayName =
                                value.take(
                                    MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH,
                                )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !actionInProgress,
                        singleLine = true,
                        label = {
                            Text(
                                text = stringResource(
                                    R.string
                                        .ranked_table_identity_public_name,
                                ),
                            )
                        },
                        supportingText = {
                            Text(
                                text = stringResource(
                                    if (publicDisplayNameInvalid) {
                                        R.string
                                            .ranked_table_identity_public_name_invalid
                                    } else {
                                        R.string
                                            .ranked_table_identity_public_name_hint
                                    },
                                ),
                            )
                        },
                        isError = publicDisplayNameInvalid,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            capitalization =
                                KeyboardCapitalization.Words,
                        ),
                    )
                }

                Text(
                    text = stringResource(
                        R.string.ranked_table_identity_preview_label,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        DominoSemanticColors.brandSupportingText,
                )

                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(
                            DominoSemanticColors.brandBackground,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = normalizedCode.ifBlank { "Â·Â·Â·" },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = DominoColorTokens.AccentYellow,
                    )
                }

                OutlinedTextField(
                    value = tableCode,
                    onValueChange = { value ->
                        tableCode =
                            normalizeOnlineAccountTableCodeInput(
                                value,
                            )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !actionInProgress,
                    singleLine = true,
                    label = {
                        Text(
                            text = stringResource(
                                R.string.ranked_table_identity_label,
                            ),
                        )
                    },
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.ranked_table_identity_hint,
                            ),
                        )
                    },
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization =
                            KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                )

                if (suggestedFromOffline) {
                    Text(
                        text = stringResource(
                            R.string.ranked_table_identity_offline_suggestion,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color =
                            DominoSemanticColors.brandSupportingText,
                    )
                }

                Text(
                    text = stringResource(
                        R.string.ranked_table_identity_change_later,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color =
                        DominoSemanticColors.brandSupportingText,
                )

                feedbackMessage
                    ?.takeIf { message -> message.isNotBlank() }
                    ?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = {
                        onConfirm(
                            normalizedPublicDisplayName
                                ?: publicDisplayName,
                            normalizedCode,
                        )
                    },
                    enabled = canConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            DominoColorTokens.AccentYellow,
                        contentColor =
                            DominoSemanticColors.brandBackground,
                        disabledContainerColor =
                            DominoColorTokens.AccentYellow.copy(
                                alpha = 0.68f,
                            ),
                        disabledContentColor =
                            DominoSemanticColors.brandBackground.copy(
                                alpha = 0.82f,
                            ),
                    ),
                ) {
                    Text(
                        text = stringResource(
                            if (actionInProgress) {
                                R.string.ranked_table_identity_saving
                            } else {
                                R.string.ranked_table_identity_confirm
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    enabled = !actionInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            DominoSemanticColors.brandBackground,
                        contentColor =
                            DominoSemanticColors.brandText,
                        disabledContainerColor =
                            DominoSemanticColors.brandBackground.copy(
                                alpha = 0.52f,
                            ),
                        disabledContentColor =
                            DominoSemanticColors.brandText.copy(
                                alpha = 0.50f,
                            ),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.common_back),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
