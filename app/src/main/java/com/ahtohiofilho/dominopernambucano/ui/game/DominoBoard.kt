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
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoTableLayoutMetrics
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.domain.calculateDropTargets
import com.ahtohiofilho.dominopernambucano.domain.calculateTablePlacements
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

private val TABLE_PIECE_WIDTH = 30.dp
private val TABLE_PIECE_HEIGHT = 55.dp

private val TABLE_PIECE_GAP = 0.dp
private val TABLE_SAFE_MARGIN = 4.dp

private const val LATERAL_ESCAPE_PIECE_COUNT = 3f

@Composable
fun DominoBoard(
    boardChain: DominoBoardChain,
    modifier: Modifier = Modifier,
    playableMoves: List<PlayableMove> = emptyList(),
    showDropTargets: Boolean = false,
    highlightedDropSide: BoardSide? = null,
    onDropTargetsChanged: (List<DominoDropTargetInWindow>) -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current

        var boardBoundsInWindow by remember {
            mutableStateOf<Rect?>(null)
        }

        val metrics = DominoTableLayoutMetrics(
            boardWidth = maxWidth.value,
            boardHeight = maxHeight.value,

            pieceStandingWidth = TABLE_PIECE_WIDTH.value,
            pieceStandingHeight = TABLE_PIECE_HEIGHT.value,

            pieceGap = TABLE_PIECE_GAP.value,

            safeMarginLeft = TABLE_SAFE_MARGIN.value,
            safeMarginTop = TABLE_SAFE_MARGIN.value,
            safeMarginRight = TABLE_SAFE_MARGIN.value,
            safeMarginBottom = TABLE_SAFE_MARGIN.value,

            lateralEscapeDistance = TABLE_PIECE_HEIGHT.value * LATERAL_ESCAPE_PIECE_COUNT,
        )

        val placements = remember(
            boardChain,
            maxWidth,
            maxHeight,
        ) {
            calculateTablePlacements(
                boardChain = boardChain,
                metrics = metrics,
            )
        }

        val dropTargets = remember(
            boardChain,
            playableMoves,
            maxWidth,
            maxHeight,
        ) {
            calculateDropTargets(
                boardChain = boardChain,
                playableMoves = playableMoves,
                metrics = metrics,
            )
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
                        TABLE_PIECE_HEIGHT
                    } else {
                        TABLE_PIECE_WIDTH
                    }

                    val markerHeight = if (isSideways) {
                        TABLE_PIECE_WIDTH
                    } else {
                        TABLE_PIECE_HEIGHT
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
                                shape = RoundedCornerShape(5.dp),
                            )
                            .border(
                                width = 2.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(5.dp),
                            ),
                    )
                }
            }

            placements.forEach { placement ->
                DominoPieceView(
                    piece = placement.piece,
                    faceUp = true,
                    width = TABLE_PIECE_WIDTH,
                    height = TABLE_PIECE_HEIGHT,
                    rotationDegrees = placement.rotationDegrees,
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