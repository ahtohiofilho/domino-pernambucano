package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.ui.menu.MenuScaffold
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun OnlineParticipationRestoreScreen(
    isLoading: Boolean,
    message: String? = null,
    onRetryClick: () -> Unit,
    onOpenMenuClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = "Retomando jogo online",
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = message ?: "Consultando sua sala no servidor...",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        if (isLoading) {
            CircularProgressIndicator(
                color = DominoSemanticColors.primaryTextOnDark,
            )
        } else {
            PrimaryMenuButton(
                text = "Tentar novamente",
                onClick = onRetryClick,
            )

            SecondaryMenuButton(
                text = "Abrir menu",
                onClick = onOpenMenuClick,
            )
        }
    }
}
