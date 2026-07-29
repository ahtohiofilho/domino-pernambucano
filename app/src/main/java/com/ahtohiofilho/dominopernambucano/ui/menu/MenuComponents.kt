package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahtohiofilho.dominopernambucano.ui.components.DominoPrimaryButton
import com.ahtohiofilho.dominopernambucano.ui.components.DominoSecondaryButton

@Composable
fun PrimaryMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DominoPrimaryButton(
        modifier = modifier.fillMaxWidth(),
        text = text,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
fun SecondaryMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DominoSecondaryButton(
        modifier = modifier.fillMaxWidth(),
        text = text,
        onClick = onClick,
        enabled = enabled,
    )
}
