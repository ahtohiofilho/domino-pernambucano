package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun MenuPlaceholderScreen(
    title: String,
    description: String,
    onBackClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            modifier = Modifier.semantics {
                heading()
            },
            text = title,
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = description,
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        SecondaryMenuButton(
            text = stringResource(R.string.common_back),
            onClick = onBackClick,
        )
    }
}
