package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun PrimaryMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DominoSemanticColors.primarySurface,
            contentColor = DominoSemanticColors.primaryTextOnLight,
        ),
    ) {
        Text(
            text = text,
        )
    }
}

@Composable
fun SecondaryMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = DominoColorTokens.PureWhite.copy(alpha = 0.70f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DominoSemanticColors.primaryTextOnDark,
        ),
    ) {
        Text(
            text = text,
        )
    }
}