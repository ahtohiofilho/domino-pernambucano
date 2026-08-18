package com.ahtohiofilho.dominopernambucano.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

internal enum class DominoTextFieldTone {
    OnDark,
    OnLight,
}

@Composable
internal fun DominoOutlinedTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    tone: DominoTextFieldTone = DominoTextFieldTone.OnLight,
    isError: Boolean = false,
    success: Boolean = false,
) {
    val colors = when (tone) {
        DominoTextFieldTone.OnDark ->
            OutlinedTextFieldDefaults.colors(
                focusedTextColor =
                    DominoSemanticColors.primaryTextOnDark,
                unfocusedTextColor =
                    DominoSemanticColors.primaryTextOnDark,
                disabledTextColor =
                    DominoSemanticColors.disabledContentOnDark,
                focusedBorderColor = if (success) {
                    DominoSemanticColors.brandPositive
                } else {
                    DominoColorTokens.PureWhite
                },
                unfocusedBorderColor = if (success) {
                    DominoSemanticColors.brandPositive
                } else {
                    DominoSemanticColors.highContrastBorder
                },
                disabledBorderColor =
                    DominoSemanticColors.lowContrastBorder,
                focusedLabelColor = DominoColorTokens.PureWhite,
                unfocusedLabelColor =
                    DominoSemanticColors.primaryTextOnDark,
                disabledLabelColor =
                    DominoSemanticColors.disabledContentOnDark,
                cursorColor = DominoColorTokens.PureWhite,
            )

        DominoTextFieldTone.OnLight ->
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = DominoSemanticColors.dialogBody,
                unfocusedTextColor = DominoSemanticColors.dialogBody,
                disabledTextColor =
                    DominoSemanticColors.dialogDisabledDismissAction,
                focusedBorderColor = if (success) {
                    DominoSemanticColors.brandPositive
                } else {
                    DominoSemanticColors.dialogAction
                },
                unfocusedBorderColor = if (success) {
                    DominoSemanticColors.brandPositive
                } else {
                    DominoSemanticColors.dialogBody
                },
                disabledBorderColor =
                    DominoSemanticColors.dialogDisabledDismissAction,
                focusedLabelColor = if (success) {
                    DominoSemanticColors.brandPositive
                } else {
                    DominoSemanticColors.dialogAction
                },
                unfocusedLabelColor = DominoSemanticColors.dialogBody,
                disabledLabelColor =
                    DominoSemanticColors.dialogDisabledDismissAction,
                cursorColor = DominoSemanticColors.dialogAction,
            )
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        label = {
            Text(text = label)
        },
        supportingText = supportingText?.let { text ->
            {
                Text(text = text)
            }
        },
        keyboardOptions = keyboardOptions,
        colors = colors,
    )
}
