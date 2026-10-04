package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoTableLayoutMetrics
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.domain.calculateDropTargets
import com.ahtohiofilho.dominopernambucano.domain.calculateTablePlacements
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

private data class ResponsiveBoardVisualMetrics(
    val pieceWidth: Dp,
    val pieceHeight: Dp,
    val pieceGap: Dp,
    val safeMargin: Dp,
    val lateralEscapeDistance: Dp,
)

@Composable
fun DominoBoard(
    boardChain: DominoBoardChain,
    modifier: Modifier = Modifier,
    playableMoves: List<PlayableMove> = emptyList(),
    showDropTargets: Boolean = false,
    highlightedDropSide: BoardSide? = null,
    animatedPlayableMove: PlayableMove? = null,
    animatedMovePresentationKey: DominoMovePresentationKey? = null,
    magnifiedVisuals: Boolean = false,
    onDropTargetsChanged: (List<DominoDropTargetInWindow>) -> Unit = {},
    onAnimatedMoveTargetChanged: (DominoMoveTargetInWindow) -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current

        var boardBoundsInWindow by remember {
            mutableStateOf<Rect?>(null)
        }

        val responsiveMetrics = remember(
            maxWidth,
            maxHeight,
            magnifiedVisuals,
        ) {
            buildResponsiveBoardVisualMetrics(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                magnifiedVisuals = magnifiedVisuals,
            )
        }

        val layoutMetrics = DominoTableLayoutMetrics(
            boardWidth = maxWidth.value,
            boardHeight = maxHeight.value,

            pieceStandingWidth = responsiveMetrics.pieceWidth.value,
            pieceStandingHeight = responsiveMetrics.pieceHeight.value,

            pieceGap = responsiveMetrics.pieceGap.value,

            safeMarginLeft = responsiveMetrics.safeMargin.value,
            safeMarginTop = responsiveMetrics.safeMargin.value,
            safeMarginRight = responsiveMetrics.safeMargin.value,
            safeMarginBottom = responsiveMetrics.safeMargin.value,

            lateralEscapeDistance = responsiveMetrics.lateralEscapeDistance.value,
        )

        val placements = remember(
            boardChain,
            maxWidth,
            maxHeight,
            responsiveMetrics,
        ) {
            calculateTablePlacements(
                boardChain = boardChain,
                metrics = layoutMetrics,
            )
        }

        val dropTargets = remember(
            boardChain,
            playableMoves,
            maxWidth,
            maxHeight,
            responsiveMetrics,
        ) {
            calculateDropTargets(
                boardChain = boardChain,
                playableMoves = playableMoves,
                metrics = layoutMetrics,
            )
        }

        val animatedMoveTargets = remember(
            boardChain,
            animatedPlayableMove,
            animatedMovePresentationKey,
            maxWidth,
            maxHeight,
            responsiveMetrics,
        ) {
            if (animatedPlayableMove == null) {
                emptyList()
            } else {
                calculateDropTargets(
                    boardChain = boardChain,
                    playableMoves = listOf(animatedPlayableMove),
                    metrics = layoutMetrics,
                )
            }
        }

        LaunchedEffect(
            dropTargets,
            boardBoundsInWindow,
            density,
        ) {
            val bounds = boardBoundsInWindow

            if (bounds == null || dropTargets.isEmpty()) {
                onDropTargetsChanged(emptyList())
                return@LaunchedEffect
            }

            val boardCenterInWindow = Offset(
                x = bounds.left + bounds.width / 2f,
                y = bounds.top + bounds.height / 2f,
            )

            val targetsInWindow = dropTargets.map { target ->
                val targetOffsetInPixels = with(density) {
                    Offset(
                        x = target.centerX.dp.toPx(),
                        y = target.centerY.dp.toPx(),
                    )
                }

                DominoDropTargetInWindow(
                    side = target.side,
                    positionInWindow = boardCenterInWindow + targetOffsetInPixels,
                )
            }

            onDropTargetsChanged(targetsInWindow)
        }

        LaunchedEffect(
            animatedMoveTargets,
            animatedMovePresentationKey,
            boardBoundsInWindow,
            density,
        ) {
            val bounds = boardBoundsInWindow
            val target = animatedMoveTargets.firstOrNull()
            val presentationKey = animatedMovePresentationKey

            if (
                bounds == null ||
                target == null ||
                presentationKey == null
            ) {
                return@LaunchedEffect
            }

            val boardCenterInWindow = Offset(
                x = bounds.left + bounds.width / 2f,
                y = bounds.top + bounds.height / 2f,
            )

            val targetOffsetInPixels = with(density) {
                Offset(
                    x = target.centerX.dp.toPx(),
                    y = target.centerY.dp.toPx(),
                )
            }

            onAnimatedMoveTargetChanged(
                DominoMoveTargetInWindow(
                    presentationKey = presentationKey,
                    positionInWindow = boardCenterInWindow + targetOffsetInPixels,
                    rotationDegrees = target.rotationDegrees,
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    boardBoundsInWindow = coordinates.boundsInWindow()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (showDropTargets) {
                dropTargets.forEach { target ->
                    val isHighlighted = target.side == highlightedDropSide
                    val normalizedRotation = ((target.rotationDegrees % 360f) + 360f) % 360f
                    val isSideways = normalizedRotation == 90f || normalizedRotation == 270f

                    val markerWidth = if (isSideways) {
                        responsiveMetrics.pieceHeight
                    } else {
                        responsiveMetrics.pieceWidth
                    }

                    val markerHeight = if (isSideways) {
                        responsiveMetrics.pieceWidth
                    } else {
                        responsiveMetrics.pieceHeight
                    }

                    val borderColor = if (isHighlighted) {
                        DominoSemanticColors.scoreHighlight
                    } else {
                        DominoColorTokens.PureWhite.copy(alpha = 0.38f)
                    }

                    val backgroundColor = if (isHighlighted) {
                        DominoSemanticColors.scoreHighlight.copy(alpha = 0.22f)
                    } else {
                        DominoColorTokens.PureWhite.copy(alpha = 0.08f)
                    }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = target.centerX.dp,
                                y = target.centerY.dp,
                            )
                            .width(markerWidth)
                            .height(markerHeight)
                            .background(
                                color = backgroundColor,
                                shape = RoundedCornerShape(
                                    DominoGameVisualTokens.DropTargetCornerRadius,
                                ),
                            )
                            .border(
                                width = DominoGameVisualTokens.DropTargetBorderWidth,
                                color = borderColor,
                                shape = RoundedCornerShape(
                                    DominoGameVisualTokens.DropTargetCornerRadius,
                                ),
                            ),
                    )
                }
            }

            placements.forEach { placement ->
                DominoPieceView(
                    piece = placement.piece,
                    faceUp = true,
                    width = responsiveMetrics.pieceWidth,
                    height = responsiveMetrics.pieceHeight,
                    rotationDegrees = placement.rotationDegrees,
                    autoOrientToPieceOrder = false,
                    modifier = Modifier.offset(
                        x = placement.centerX.dp,
                        y = placement.centerY.dp,
                    ),
                    onClick = null,
                )
            }
        }
    }
}

private fun buildResponsiveBoardVisualMetrics(
    maxWidth: Dp,
    maxHeight: Dp,
    magnifiedVisuals: Boolean,
): ResponsiveBoardVisualMetrics {
    val shortSide = minOf(
        maxWidth.value,
        maxHeight.value,
    )

    val longSide = maxOf(
        maxWidth.value,
        maxHeight.value,
    )

    val compactBoard = shortSide < 230f
    val narrowBoard = maxWidth.value < 300f

    val pieceWidthFactor = if (magnifiedVisuals) {
        DominoGameVisualTokens.MagnifiedTablePieceWidthFactor
    } else {
        when {
            compactBoard ->
                DominoGameVisualTokens.ResponsiveTablePieceWidthFactorCompact
            narrowBoard ->
                DominoGameVisualTokens.ResponsiveTablePieceWidthFactorNarrow
            else ->
                DominoGameVisualTokens.ResponsiveTablePieceWidthFactorDefault
        }
    }

    val pieceWidthMin = if (magnifiedVisuals) {
        DominoGameVisualTokens.MagnifiedTablePieceWidthMin
    } else {
        DominoGameVisualTokens.ResponsiveTablePieceWidthMin
    }
    val pieceWidthMax = if (magnifiedVisuals) {
        DominoGameVisualTokens.MagnifiedTablePieceWidthMax
    } else {
        DominoGameVisualTokens.ResponsiveTablePieceWidthMax
    }

    val pieceWidthValue = (shortSide * pieceWidthFactor)
        .coerceIn(
            minimumValue = pieceWidthMin,
            maximumValue = pieceWidthMax,
        )

    val pieceHeightMin = if (magnifiedVisuals) {
        DominoGameVisualTokens.MagnifiedTablePieceHeightMin
    } else {
        DominoGameVisualTokens.ResponsiveTablePieceHeightMin
    }
    val pieceHeightMax = if (magnifiedVisuals) {
        DominoGameVisualTokens.MagnifiedTablePieceHeightMax
    } else {
        DominoGameVisualTokens.ResponsiveTablePieceHeightMax
    }

    val pieceHeightValue = (
            pieceWidthValue * DominoGameVisualTokens.TablePieceHeightRatio
            )
        .coerceIn(
            minimumValue = pieceHeightMin,
            maximumValue = pieceHeightMax,
        )

    val safeMarginValue = (
            shortSide * DominoGameVisualTokens.ResponsiveTableSafeMarginFactor
            )
        .coerceIn(
            minimumValue = DominoGameVisualTokens.ResponsiveTableSafeMarginMin,
            maximumValue = DominoGameVisualTokens.ResponsiveTableSafeMarginMax,
        )

    val lateralEscapePieceCount = when {
        maxWidth.value < 260f -> DominoGameVisualTokens.LateralEscapePieceCountVeryNarrow
        maxWidth.value < 340f -> DominoGameVisualTokens.LateralEscapePieceCountNarrow
        longSide < 520f -> DominoGameVisualTokens.LateralEscapePieceCountCompact
        else -> DominoGameVisualTokens.LateralEscapePieceCountDefault
    }

    return ResponsiveBoardVisualMetrics(
        pieceWidth = pieceWidthValue.dp,
        pieceHeight = pieceHeightValue.dp,
        pieceGap = DominoGameVisualTokens.TablePieceGap,
        safeMargin = safeMarginValue.dp,
        lateralEscapeDistance = (pieceHeightValue * lateralEscapePieceCount).dp,
    )
}