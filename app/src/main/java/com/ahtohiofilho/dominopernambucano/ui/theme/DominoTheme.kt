package com.ahtohiofilho.dominopernambucano.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DominoColorScheme = darkColorScheme(
    primary = DominoColorTokens.PernambucoBlue,
    onPrimary = DominoColorTokens.SurfaceWhite,

    secondary = DominoColorTokens.AccentYellow,
    onSecondary = DominoColorTokens.InkBlue,

    tertiary = DominoColorTokens.AccentGreen,
    onTertiary = DominoColorTokens.SurfaceWhite,

    background = DominoSemanticColors.appBackground,
    onBackground = DominoSemanticColors.primaryTextOnDark,

    surface = DominoSemanticColors.primarySurface,
    onSurface = DominoSemanticColors.primaryTextOnLight,

    surfaceVariant = DominoSemanticColors.secondarySurface,
    onSurfaceVariant = DominoSemanticColors.primaryTextOnLight,

    error = DominoColorTokens.AccentRed,
    onError = Color.White,
)

@Composable
fun DominoPernambucanoTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DominoColorScheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}