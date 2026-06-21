package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun PlayModeScreen(
    onlinePlayerName: String,
    onOnlinePlayerNameChange: (String) -> Unit,
    onBackClick: () -> Unit,
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

        OnlinePlayerNameField(
            playerName = onlinePlayerName,
            onPlayerNameChange = onOnlinePlayerNameChange,
        )

        PrimaryMenuButton(
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
private fun OnlinePlayerNameField(
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Seu nome online",
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        OutlinedTextField(
            value = playerName,
            onValueChange = onPlayerNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text(
                    text = "Nome exibido na mesa",
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
            text = "O nome e a identidade deste aparelho ficam salvos localmente.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}