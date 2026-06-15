package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

enum class DominoPlayerSeatOrientation {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun DominoPlayerSeat(
    name: String,
    pieces: List<DominoPiece>,
    isCurrent: Boolean,
    orientation: DominoPlayerSeatOrientation,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    faceUp: Boolean = false,
    isWinner: Boolean = false,
    onBoundsChanged: (Rect?) -> Unit = {},
) {
    val handPadding = if (compact) {
        DominoGameVisualTokens.OpponentHandPaddingCompact
    } else {
        DominoGameVisualTokens.OpponentHandPadding
    }

    val highlightShape = RoundedCornerShape(
        DominoGameVisualTokens.OpponentHandHighlightCornerRadius,
    )

    val shouldHighlight = isCurrent || isWinner

    val highlightModifier = if (shouldHighlight) {
        Modifier
            .background(
                color = DominoSemanticColors.scoreHighlight.copy(
                    alpha = if (isWinner) 0.26f else 0.22f,
                ),
                shape = highlightShape,
            )
            .border(
                width = DominoGameVisualTokens.OpponentHandHighlightBorderWidth,
                color = DominoSemanticColors.scoreHighlight.copy(
                    alpha = if (isWinner) 0.96f else 0.88f,
                ),
                shape = highlightShape,
            )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInWindow())
            }
            .then(highlightModifier)
            .padding(handPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (compact) 4.dp else 6.dp,
        ),
    ) {
        PlayerSeatName(
            name = name,
            isCurrent = isCurrent,
            isWinner = isWinner,
            compact = compact,
        )

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

        if (isCurrent) {
            CurrentTurnPill(
                compact = compact,
            )
        } else if (isWinner) {
            WinnerPill(
                compact = compact,
            )
        }
    }
}

@Composable
private fun PlayerSeatName(
    name: String,
    isCurrent: Boolean,
    isWinner: Boolean,
    compact: Boolean,
) {
    Text(
        text = name,
        color = if (isCurrent || isWinner) {
            DominoSemanticColors.scoreHighlight
        } else {
            DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.76f)
        },
        style = if (compact) {
            MaterialTheme.typography.labelSmall
        } else {
            MaterialTheme.typography.labelMedium
        },
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
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

@Composable
private fun WinnerPill(
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                color = DominoSemanticColors.scoreHighlight.copy(alpha = 0.92f),
            )
            .padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "★",
            color = DominoColorTokens.InkBlue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun CurrentTurnPill(
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                color = DominoSemanticColors.scoreHighlight.copy(alpha = 0.92f),
            )
            .padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(DominoSemanticColors.playableMove)
                .padding(3.dp),
        )

        Text(
            text = "vez",
            color = DominoColorTokens.InkBlue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
        )
    }
}