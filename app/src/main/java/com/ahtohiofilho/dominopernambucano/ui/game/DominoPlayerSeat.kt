package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

enum class DominoPlayerSeatOrientation {
    HORIZONTAL,
    VERTICAL,
}

enum class DominoPlayerIdentityPlacement {
    BEFORE_HAND,
    AFTER_HAND,
}

@Composable
fun DominoPlayerSeat(
    name: String,
    participantType: DominoParticipantType =
        DominoParticipantType.HUMAN,
    pieces: List<DominoPiece>,
    isCurrent: Boolean,
    orientation: DominoPlayerSeatOrientation,
    identityPlacement: DominoPlayerIdentityPlacement =
        DominoPlayerIdentityPlacement.BEFORE_HAND,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    faceUp: Boolean = false,
    isWinner: Boolean = false,
    onBoundsChanged: (Rect?) -> Unit = {},
) {
    val winnerAttention = rememberWinnerAttentionMotion(
        isWinner = isWinner,
    )

    val animatedModifier = modifier.graphicsLayer {
        scaleX = winnerAttention.scale
        scaleY = winnerAttention.scale
    }


    Box(
        modifier = animatedModifier.fillMaxSize(),
    ) {
        val handAlignment = when (orientation) {
            DominoPlayerSeatOrientation.HORIZONTAL -> Alignment.TopCenter
            DominoPlayerSeatOrientation.VERTICAL -> {
                if (identityPlacement == DominoPlayerIdentityPlacement.BEFORE_HAND) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            }
        }

        OpponentHandSurface(
            pieces = pieces,
            faceUp = faceUp,
            compact = compact,
            isCurrent = isCurrent,
            isWinner = isWinner,
            orientation = orientation,
            onBoundsChanged = onBoundsChanged,
            modifier = Modifier.align(handAlignment),
        )

        val codeModifier = when (orientation) {
            DominoPlayerSeatOrientation.HORIZONTAL -> {
                val fixedOffset = if (compact) {
                    DominoGameVisualTokens.TopPlayerCodeAnchorOffsetCompact
                } else {
                    DominoGameVisualTokens.TopPlayerCodeAnchorOffset
                }

                Modifier
                    .align(Alignment.TopCenter)
                    .offset(
                        x = if (identityPlacement == DominoPlayerIdentityPlacement.AFTER_HAND) {
                            -fixedOffset
                        } else {
                            fixedOffset
                        },
                    )
            }

            DominoPlayerSeatOrientation.VERTICAL -> {
                Modifier
                    .align(
                        if (identityPlacement == DominoPlayerIdentityPlacement.BEFORE_HAND) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        },
                    )
                    .offset(
                        y = if (identityPlacement == DominoPlayerIdentityPlacement.BEFORE_HAND) {
                            DominoGameVisualTokens.SidePlayerCodeAnchorOffsetCompact
                        } else {
                            -DominoGameVisualTokens.SidePlayerCodeAnchorOffsetCompact
                        },
                    )
            }
        }

        DominoPlayerCodeLabel(
            name = name,
            isCurrent = isCurrent,
            isWinner = isWinner,
            compact = compact,
            modifier = codeModifier,
        )
    }
}

@Composable
internal fun DominoPlayerCodeLabel(
    name: String,
    isCurrent: Boolean,
    isWinner: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleCode = resolveDominoVisiblePlayerCode(
        name = name,
    )

    Column(
        modifier = modifier.widthIn(
            min = DominoGameVisualTokens.PlayerCodeMinWidth,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = visibleCode,
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 21.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        when {
            isWinner -> {
                WinnerStatusPill(
                    compact = true,
                )
            }

            isCurrent -> {
                Box(
                    modifier = Modifier
                        .size(
                            DominoGameVisualTokens.PlayerCodeCurrentIndicatorSize,
                        )
                        .background(
                            color = DominoSemanticColors.playableMove,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun OpponentHandSurface(
    pieces: List<DominoPiece>,
    faceUp: Boolean,
    compact: Boolean,
    isCurrent: Boolean,
    isWinner: Boolean,
    orientation: DominoPlayerSeatOrientation,
    onBoundsChanged: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val handPadding = if (compact) {
        DominoGameVisualTokens.OpponentHandPaddingCompact
    } else {
        DominoGameVisualTokens.OpponentHandPadding
    }

    val handShape = RoundedCornerShape(
        DominoGameVisualTokens.OpponentHandSurfaceCornerRadius,
    )

    val winnerAttention = rememberWinnerAttentionMotion(
        isWinner = isWinner,
    )

    val shouldHighlight = isCurrent || isWinner

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInWindow())
            }
            .shadow(
                elevation = DominoGameVisualTokens.OpponentHandSurfaceElevation,
                shape = handShape,
                clip = false,
            )
            .background(
                color = if (shouldHighlight) {
                    DominoSemanticColors.scoreHighlight.copy(
                        alpha = if (isWinner) {
                            winnerAttention.containerAlpha
                        } else {
                            0.18f
                        },
                    )
                } else {
                    DominoSemanticColors.brandSurface.copy(alpha = 0.14f)
                },
                shape = handShape,
            )
            .border(
                width = if (shouldHighlight) {
                    DominoGameVisualTokens.OpponentHandHighlightBorderWidth
                } else {
                    DominoGameVisualTokens.OpponentHandSurfaceBorderWidth
                },
                color = if (shouldHighlight) {
                    DominoSemanticColors.scoreHighlight.copy(
                        alpha = if (isWinner) {
                            winnerAttention.borderAlpha
                        } else {
                            0.86f
                        },
                    )
                } else {
                    DominoSemanticColors.brandBorder.copy(alpha = 0.52f)
                },
                shape = handShape,
            )
            .padding(handPadding),
        contentAlignment = Alignment.Center,
    ) {
        when (orientation) {
            DominoPlayerSeatOrientation.HORIZONTAL -> {
                OpponentHorizontalPieces(
                    pieces = pieces,
                    faceUp = faceUp,
                    compact = compact,
                )
            }

            DominoPlayerSeatOrientation.VERTICAL -> {
                OpponentVerticalPieces(
                    pieces = pieces,
                    faceUp = faceUp,
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun OpponentHorizontalPieces(
    pieces: List<DominoPiece>,
    faceUp: Boolean,
    compact: Boolean,
) {
    val pieceWidth = if (compact) {
        DominoGameVisualTokens.OpponentHorizontalPieceWidthCompact
    } else {
        DominoGameVisualTokens.OpponentHorizontalPieceWidth
    }

    val pieceHeight = if (compact) {
        DominoGameVisualTokens.OpponentHorizontalPieceHeightCompact
    } else {
        DominoGameVisualTokens.OpponentHorizontalPieceHeight
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(
            DominoGameVisualTokens.OpponentHandPieceSpacing,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pieces.forEach { piece ->
            DominoPieceView(
                piece = piece,
                faceUp = faceUp,
                width = pieceWidth,
                height = pieceHeight,
                onClick = null,
            )
        }
    }
}

@Composable
private fun OpponentVerticalPieces(
    pieces: List<DominoPiece>,
    faceUp: Boolean,
    compact: Boolean,
) {
    val pieceWidth = if (compact) {
        DominoGameVisualTokens.OpponentVerticalPieceWidthCompact
    } else {
        DominoGameVisualTokens.OpponentVerticalPieceWidth
    }

    val pieceHeight = if (compact) {
        DominoGameVisualTokens.OpponentVerticalPieceHeightCompact
    } else {
        DominoGameVisualTokens.OpponentVerticalPieceHeight
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(
            DominoGameVisualTokens.OpponentHandPieceSpacing,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        pieces.forEach { piece ->
            DominoPieceView(
                piece = piece,
                faceUp = faceUp,
                width = pieceWidth,
                height = pieceHeight,
                rotationDegrees = DominoGameVisualTokens.OpponentVerticalPieceRotationDegrees,
                onClick = null,
            )
        }
    }
}
