package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun MainMenuScreen(
    onPlayClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = "MESA, PARCERIA E ESTRATÉGIA",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.14.sp,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Dominó\nPernambucano",
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 42.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Um jogo de parceria, leitura de mesa e tomada de decisão.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        PrimaryMenuButton(
            text = "Jogar",
            onClick = onPlayClick,
        )
    }
}