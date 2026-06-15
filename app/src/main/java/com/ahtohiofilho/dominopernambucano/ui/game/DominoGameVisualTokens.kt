package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.unit.dp

object DominoGameVisualTokens {
    /*
     * Estes tokens evitam
     * que os valores fiquem espalhados pela tela nova.
     */

    val LocalHandPieceWidth = 48.dp
    val LocalHandPieceHeight = 88.dp

    val LocalHandCardCornerRadius = 18.dp
    val LocalHandCardPadding = 8.dp
    val LocalHandHeaderBottomGap = 6.dp
    val LocalHandPieceSpacing = 5.dp

    val LocalPlayableHighlightCornerRadius = 7.dp
    val LocalPlayableHighlightPadding = 2.dp
    val LocalPlayableBorderWidth = 2.dp

    const val LocalPlayablePieceScale = 1.06f
    const val LocalPlayableShadowElevation = 8f
    const val LocalUnavailablePieceAlpha = 0.45f
    const val LocalDefaultPieceAlpha = 1f
    const val LocalHiddenPieceAlpha = 0f

    val TablePieceReferenceWidth = 30.dp
    val TablePieceReferenceHeight = 55.dp

    const val TablePieceHeightRatio = 1.83f

    const val ResponsiveTablePieceWidthMin = 20f
    const val ResponsiveTablePieceWidthMax = 31f

    const val ResponsiveTablePieceHeightMin = 38f
    const val ResponsiveTablePieceHeightMax = 57f

    const val ResponsiveTableSafeMarginMin = 2f
    const val ResponsiveTableSafeMarginMax = 8f

    const val ResponsiveTableSafeMarginFactor = 0.014f

    const val ResponsiveTablePieceWidthFactorCompact = 0.070f
    const val ResponsiveTablePieceWidthFactorNarrow = 0.074f
    const val ResponsiveTablePieceWidthFactorDefault = 0.078f

    const val LateralEscapePieceCountVeryNarrow = 1.65f
    const val LateralEscapePieceCountNarrow = 2.15f
    const val LateralEscapePieceCountCompact = 2.45f
    const val LateralEscapePieceCountDefault = 2.85f

    val TablePieceGap = 0.dp

    val TableAreaCornerRadius = 30.dp
    val TableAreaContentPadding = 6.dp
    val TableAreaBorderWidth = 1.dp

    val DropTargetCornerRadius = 5.dp
    val DropTargetBorderWidth = 2.dp
    val DropTargetHitRadius = 86.dp

    val OpponentHorizontalPieceWidth = 30.dp
    val OpponentHorizontalPieceHeight = 55.dp

    val OpponentHorizontalPieceWidthCompact = 24.dp
    val OpponentHorizontalPieceHeightCompact = 44.dp

    val OpponentVerticalPieceWidth = 26.dp
    val OpponentVerticalPieceHeight = 46.dp

    val OpponentVerticalPieceWidthCompact = 22.dp
    val OpponentVerticalPieceHeightCompact = 38.dp

    const val OpponentVerticalPieceRotationDegrees = 90f

    val OpponentHandPieceSpacing = 2.dp
    val OpponentHandPadding = 6.dp
    val OpponentHandPaddingCompact = 4.dp
    val OpponentHandHighlightCornerRadius = 12.dp
    val OpponentHandHighlightBorderWidth = 2.dp

    const val DraggedPieceScale = 1.08f
    const val DraggedPieceShadowElevation = 18f
    const val DraggedPieceAlpha = 0.96f
    const val DraggedPieceOverHandAlpha = 0.72f

    const val PlayedMovePieceScale = 1.08f
    const val PlayedMovePieceShadowElevation = 22f
}