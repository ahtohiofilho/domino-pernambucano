package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.ahtohiofilho.dominopernambucano.offline.OfflinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.offline.isValidOfflineTableCode
import com.ahtohiofilho.dominopernambucano.offline.normalizeOfflineDisplayName
import com.ahtohiofilho.dominopernambucano.offline.normalizeOfflineTableCode
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

@Composable
fun OfflineIdentityDialog(
    onDismiss: () -> Unit,
    onConfirm: (OfflinePlayerIdentity) -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var tableCode by remember { mutableStateOf("") }

    val normalizedName = normalizeOfflineDisplayName(displayName)
    val normalizedCode = normalizeOfflineTableCode(tableCode)
    val canConfirm = normalizedName.isNotBlank() &&
        isValidOfflineTableCode(normalizedCode)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = DominoSemanticColors.brandText,
        unfocusedTextColor = DominoSemanticColors.brandText,
        focusedBorderColor = DominoColorTokens.AccentYellow,
        unfocusedBorderColor = DominoSemanticColors.brandText.copy(
            alpha = 0.62f,
        ),
        cursorColor = DominoColorTokens.AccentYellow,
        focusedLabelColor = DominoColorTokens.AccentYellow,
        unfocusedLabelColor = DominoSemanticColors.brandSupportingText,
        focusedSupportingTextColor =
            DominoSemanticColors.brandSupportingText,
        unfocusedSupportingTextColor =
            DominoSemanticColors.brandSupportingText,
        focusedContainerColor =
            DominoSemanticColors.brandBackground.copy(alpha = 0.36f),
        unfocusedContainerColor =
            DominoSemanticColors.brandBackground.copy(alpha = 0.24f),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DominoSemanticColors.brandSurface,
        titleContentColor = DominoSemanticColors.brandText,
        textContentColor = DominoSemanticColors.brandSupportingText,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = "Identidade offline",
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.md,
                ),
            ) {
                Text(
                    text = "Escolha como você será identificado nas partidas offline deste aparelho.",
                    color = DominoSemanticColors.brandText.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { value -> displayName = value },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = "Nome") },
                    colors = fieldColors,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                )

                OutlinedTextField(
                    value = tableCode,
                    onValueChange = { value ->
                        tableCode = normalizeOfflineTableCode(value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = "Código de mesa") },
                    supportingText = {
                        Text(
                            text = "Exatamente 3 caracteres: A–Z ou 0–9.",
                        )
                    },
                    colors = fieldColors,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                )

                Text(
                    text = "Essa identidade vale somente no modo offline. Ao entrar com uma conta, nome público e código serão definidos separadamente.",
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        OfflinePlayerIdentity(
                            displayName = normalizedName,
                            tableCode = normalizedCode,
                        ),
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        DominoSemanticColors.brandPrimaryAction,
                    contentColor =
                        DominoSemanticColors.brandPrimaryActionContent,
                ),
            ) {
                Text(text = "Jogar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = DominoSemanticColors.brandText,
                ),
            ) {
                Text(text = "Cancelar")
            }
        },
    )
}