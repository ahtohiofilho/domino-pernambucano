package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun PlayModeScreen(
    onlineDisplayName: String,
    onlineTableName: String,
    onOnlineDisplayNameChange: (String) -> Unit,
    onBackClick: () -> Unit,
    rankedAccountAvailable: Boolean,
    onRankedGameClick: () -> Unit,
    onLocalGameClick: () -> Unit,
    onCreateOnlineRoomClick: () -> Unit,
    onJoinOnlineRoomClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = "Modo de jogo",
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Escolha o fluxo da partida.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        OnlinePublicIdentityField(
            displayName = onlineDisplayName,
            tableName = onlineTableName,
            onDisplayNameChange = onOnlineDisplayNameChange,
        )

        PrimaryMenuButton(
            text = "Jogar rankeado",
            onClick = onRankedGameClick,
            enabled = rankedAccountAvailable,
        )

        if (!rankedAccountAvailable) {
            Text(
                text =
                    "Conecte ou recupere sua conta para participar do ranking.",
                color =
                    DominoSemanticColors.primaryTextOnDark.copy(
                        alpha = 0.72f,
                    ),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        SecondaryMenuButton(
            text = "Partida local",
            onClick = onLocalGameClick,
        )

        SecondaryMenuButton(
            text = "Criar sala online",
            onClick = onCreateOnlineRoomClick,
        )

        SecondaryMenuButton(
            text = "Entrar em sala online",
            onClick = onJoinOnlineRoomClick,
        )

        SecondaryMenuButton(
            text = "Voltar",
            onClick = onBackClick,
        )
    }
}

@Composable
private fun OnlinePublicIdentityField(
    displayName: String,
    tableName: String,
    onDisplayNameChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Identidade pública",
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text(
                    text = "Nome público",
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DominoSemanticColors.primaryTextOnDark,
                unfocusedTextColor = DominoSemanticColors.primaryTextOnDark,
                focusedBorderColor = DominoColorTokens.PureWhite,
                unfocusedBorderColor = DominoColorTokens.PureWhite.copy(
                    alpha = 0.62f,
                ),
                focusedLabelColor = DominoColorTokens.PureWhite,
                unfocusedLabelColor = DominoSemanticColors.primaryTextOnDark.copy(
                    alpha = 0.72f,
                ),
                cursorColor = DominoColorTokens.PureWhite,
            ),
        )

        Text(
            text = "Listas e rankings: $displayName",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = "Na mesa: $tableName",
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "O nome curto é gerado agora e poderá ser personalizado no perfil.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
