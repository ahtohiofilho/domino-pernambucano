package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.unit.dp

object DominoGameVisualTokens {
    /*
     * Estes tokens evitam
     * que os valores fiquem espalhados pela tela nova.
     */

    val HeaderSlotHeight = 70.dp
    val HeaderCornerRadius = 22.dp
    val HeaderHorizontalPadding = 12.dp
    val HeaderVerticalPadding = 8.dp
    val HeaderElevation = 6.dp
    val HeaderBorderWidth = 1.dp
    val HeaderBrandDiscSize = 34.dp
    val HeaderRoundBadgeCornerRadius = 12.dp
    val HeaderRoundBadgeHorizontalPadding = 9.dp
    val HeaderRoundBadgeVerticalPadding = 5.dp

    val LocalHandSlotHeight = 126.dp

    val TableStageHorizontalPadding = 6.dp
    val TableStageVerticalPadding = 4.dp
    val TableStageInnerHorizontalPadding = 4.dp

    val OpponentTopSeatSlotHeight = 116.dp
    val OpponentSideSeatSlotWidth = 70.dp
    val OpponentStatusIndicatorSlotHeight = 16.dp

    val TopPlayerCodeAnchorOffset = 137.dp
    val TopPlayerCodeAnchorOffsetCompact = 117.dp
    val SidePlayerCodeAnchorOffsetCompact = 155.dp
    val LocalPlayerCodeLift = 72.dp
    val PlayerCodeMinWidth = 36.dp
    val PlayerCodeCurrentIndicatorSize = 7.dp

    val TurnCountdownHudEdgePadding = 10.dp
    val TurnCountdownHudLateralOffset = 100.dp
    val TurnCountdownHudTopGap = 6.dp
    val TurnCountdownHudLocalGap = 8.dp
    val TurnCountdownHudMinWidth = 34.dp
    val TurnCountdownHudMinHeight = 34.dp
    val TurnCountdownHudHorizontalPadding = 9.dp
    val TurnCountdownHudVerticalPadding = 6.dp

    val LocalHandPieceWidth = 48.dp
    val LocalHandPieceHeight = 88.dp

    val LocalHandPieceSlotWidth = 58.dp
    val LocalHandPieceSlotHeight = 98.dp

    val LocalHandCardCornerRadius = 22.dp
    val LocalHandCardElevation = 6.dp
    val LocalHandCardBorderWidth = 1.dp
    val LocalHandCardPadding = 8.dp
    val LocalHandHeaderBottomGap = 6.dp
    val LocalHandPieceSpacing = 5.dp

    val LocalCurrentTurnIndicatorSize = 13.dp
    val LocalWaitingTurnIndicatorSize = 8.dp

    val LocalPlayableHighlightCornerRadius = 7.dp
    val LocalPlayableBorderWidth = 2.dp

    const val LocalPlayablePieceScale = 1.06f
    const val LocalPlayableShadowElevation = 8f
    const val LocalUnavailablePieceAlpha = 0.45f
    const val LocalDefaultPieceAlpha = 1f
    const val LocalHiddenPieceAlpha = 0f

    val TablePieceReferenceWidth = 33.dp
    val TablePieceReferenceHeight = 60.dp

    const val TablePieceHeightRatio = 1.83f

    const val ResponsiveTablePieceWidthMin = 23f
    const val ResponsiveTablePieceWidthMax = 35f

    const val ResponsiveTablePieceHeightMin = 42f
    const val ResponsiveTablePieceHeightMax = 64f

    const val MagnifiedTablePieceWidthMin = 32f
    const val MagnifiedTablePieceWidthMax = 48f
    const val MagnifiedTablePieceHeightMin = 59f
    const val MagnifiedTablePieceHeightMax = 88f
    const val MagnifiedTablePieceWidthFactor = 0.118f

    const val ResponsiveTableSafeMarginMin = 2f
    const val ResponsiveTableSafeMarginMax = 8f

    const val ResponsiveTableSafeMarginFactor = 0.014f

    const val ResponsiveTablePieceWidthFactorCompact = 0.077f
    const val ResponsiveTablePieceWidthFactorNarrow = 0.082f
    const val ResponsiveTablePieceWidthFactorDefault = 0.086f

    const val LateralEscapePieceCountVeryNarrow = 1.85f
    const val LateralEscapePieceCountNarrow = 2.45f
    const val LateralEscapePieceCountCompact = 2.85f
    const val LateralEscapePieceCountDefault = 3.35f

    val TablePieceGap = 0.dp

    val TableAreaCornerRadius = 30.dp
    val TableAreaShadowElevation = 4.dp
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
    val OpponentHandSurfaceCornerRadius = 14.dp
    val OpponentHandSurfaceElevation = 2.dp
    val OpponentHandSurfaceBorderWidth = 1.dp
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