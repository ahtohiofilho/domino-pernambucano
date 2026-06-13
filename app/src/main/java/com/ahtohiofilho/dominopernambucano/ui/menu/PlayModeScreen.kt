package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun PlayModeScreen(
    onBackClick: () -> Unit,
    onLocalGameClick: () -> Unit,
    onCreateOnlineRoomClick: () -> Unit,
    onJoinOnlineRoomClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = "Modo de jogo",
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
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