package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun MenuPlaceholderScreen(
    title: String,
    description: String,
    onBackClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = title,
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = description,
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Funcionalidade reservada para o roadmap online.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        SecondaryMenuButton(
            text = "Voltar",
            onClick = onBackClick,
        )
    }
}