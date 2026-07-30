package com.ahtohiofilho.dominopernambucano.ui.theme

import androidx.compose.ui.graphics.Color

object DominoColorTokens {
    val PernambucoBlue = Color(0xFF123F8C)
    val PernambucoBlueDark = Color(0xFF08275C)
    val PernambucoBlueSoft = Color(0xFFEAF1FF)
    val PernambucoBlueElevated = Color(0xFF164C9C)
    val PernambucoBlueMuted = Color(0xFF9FB6DC)

    val SurfaceWhite = Color(0xFFF8FAFF)
    val PureWhite = Color(0xFFFFFFFF)

    val InkBlue = Color(0xFF071B3A)

    val AccentRed = Color(0xFFE53935)
    val AccentYellow = Color(0xFFFFC928)
    val AccentGreen = Color(0xFF2EAD5B)
}

object DominoSemanticColors {
    val appBackground = DominoColorTokens.PernambucoBlueDark
    val primarySurface = DominoColorTokens.SurfaceWhite
    val secondarySurface = DominoColorTokens.PernambucoBlueSoft

    val primaryAction = DominoColorTokens.PernambucoBlue
    val primaryTextOnDark = DominoColorTokens.SurfaceWhite
    val primaryTextOnLight = DominoColorTokens.InkBlue

    val playableMove = DominoColorTokens.AccentGreen
    val scoreHighlight = DominoColorTokens.AccentYellow
    val warningImpact = DominoColorTokens.AccentRed

    val brandBackground = DominoColorTokens.PernambucoBlueDark
    val brandSurface = DominoColorTokens.PernambucoBlue
    val brandSurfaceElevated = DominoColorTokens.PernambucoBlueElevated
    val brandPrimaryAction = DominoColorTokens.AccentYellow
    val brandPrimaryActionContent = DominoColorTokens.InkBlue
    val brandEnergy = DominoColorTokens.AccentRed
    val brandPositive = DominoColorTokens.AccentGreen
    val brandText = DominoColorTokens.PureWhite
    val brandSupportingText = DominoColorTokens.PernambucoBlueMuted
    val brandPattern =
        DominoColorTokens.PureWhite.copy(alpha = 0.08f)
    val brandBorder =
        DominoColorTokens.PureWhite.copy(alpha = 0.18f)

    /*
     * Accessible light-dialog mapping approved before the Design System.
     * Keeping it here preserves the proven contrast while removing the
     * feature-local palette from OnlineAccountDialog.
     */
    val dialogSurface = Color(0xFFF7F8FC)
    val dialogTitle = Color(0xFF0B2D63)
    val dialogBody = Color(0xFF16365C)
    val dialogAction = Color(0xFF0B3B7A)
    val dialogActionContent = DominoColorTokens.PureWhite
    val dialogDisabledAction = Color(0xFF466A98)
    val dialogDisabledActionContent = DominoColorTokens.PureWhite
    val dialogDismissAction = Color(0xFF0B3B7A)
    val dialogDisabledDismissAction = Color(0xFF59697D)

    val disabledAction =
        DominoColorTokens.PernambucoBlue.copy(alpha = 0.38f)
    val disabledContentOnDark =
        DominoColorTokens.SurfaceWhite.copy(alpha = 0.60f)
    val disabledContentOnLight =
        DominoColorTokens.InkBlue.copy(alpha = 0.60f)

    val highContrastBorder =
        DominoColorTokens.PureWhite.copy(alpha = 0.72f)
    val lowContrastBorder =
        DominoColorTokens.PureWhite.copy(alpha = 0.36f)
}
