package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
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
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = description,
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.82f),
        )

        SecondaryMenuButton(
            text = "Voltar",
            onClick = onBackClick,
        )
    }
}