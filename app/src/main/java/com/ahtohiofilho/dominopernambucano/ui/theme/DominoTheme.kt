package com.ahtohiofilho.dominopernambucano.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
    onError = DominoColorTokens.PureWhite,
)

@Composable
fun DominoPernambucanoTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDominoSpacing provides DominoSpacing(),
    ) {
        MaterialTheme(
            colorScheme = DominoColorScheme,
            typography = DominoTypography,
            shapes = DominoShapes,
            content = content,
        )
    }
}
