package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun MainMenuScreen(
    onPlayClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = "Dominó Pernambucano",
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = "Mesa, parceria e estratégia.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyLarge,
        )

        PrimaryMenuButton(
            text = "Jogar",
            onClick = onPlayClick,
        )
    }
}