package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

private const val ATLAS_CELL_WIDTH_PX = 86
private const val ATLAS_CELL_HEIGHT_PX = 158
private const val ATLAS_COLUMNS = 7

private val ATLAS_PIECES: List<DominoPiece?> = listOf(
    DominoPiece(0, 0),
    DominoPiece(0, 1),
    DominoPiece(0, 2),
    DominoPiece(0, 3),
    DominoPiece(0, 4),
    DominoPiece(0, 5),
    DominoPiece(0, 6),

    DominoPiece(1, 1),
    DominoPiece(1, 2),
    DominoPiece(1, 3),
    DominoPiece(1, 4),
    DominoPiece(1, 5),
    DominoPiece(1, 6),
    DominoPiece(2, 2),

    DominoPiece(2, 3),
    DominoPiece(2, 4),
    DominoPiece(2, 5),
    DominoPiece(2, 6),
    DominoPiece(3, 3),
    DominoPiece(3, 4),
    DominoPiece(3, 5),

    DominoPiece(3, 6),
    DominoPiece(4, 4),
    DominoPiece(4, 5),
    DominoPiece(4, 6),
    DominoPiece(5, 5),
    DominoPiece(5, 6),
    DominoPiece(6, 6),

    null,
)

@Composable
fun DominoPieceView(
    piece: DominoPiece,
    faceUp: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 42.dp,
    height: Dp = 77.dp,
    isPlayable: Boolean = false,
    rotationDegrees: Float = 0f,
    autoOrientToPieceOrder: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val atlas = ImageBitmap.imageResource(
        id = R.drawable.domino_atlas,
    )

    val atlasCell = remember(
        piece,
        faceUp,
    ) {
        getAtlasCellForPiece(
            piece = piece,
            faceUp = faceUp,
        )
    }

    val painter = remember(
        atlas,
        atlasCell,
    ) {
        BitmapPainter(
            image = atlas,
            srcOffset = IntOffset(
                x = atlasCell.column * ATLAS_CELL_WIDTH_PX,
                y = atlasCell.row * ATLAS_CELL_HEIGHT_PX,
            ),
            srcSize = IntSize(
                width = ATLAS_CELL_WIDTH_PX,
                height = ATLAS_CELL_HEIGHT_PX,
            ),
        )
    }

    val normalizedPiece = normalizePieceForAtlas(piece)

    val shouldRotateHalfTurn =
        autoOrientToPieceOrder &&
                faceUp &&
                normalizedPiece != piece

    val finalRotation = rotationDegrees + if (shouldRotateHalfTurn) {
        180f
    } else {
        0f
    }

    val normalizedRotation = ((rotationDegrees % 360f) + 360f) % 360f
    val isSideways = normalizedRotation == 90f || normalizedRotation == 270f

    val layoutWidth = if (isSideways) {
        height
    } else {
        width
    }

    val layoutHeight = if (isSideways) {
        width
    } else {
        height
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .width(layoutWidth)
            .height(layoutHeight)
            .then(clickableModifier),
        contentAlignment = Alignment.Center,
    ) {
        DominoPieceAtlasImage(
            painter = painter,
            piece = piece,
            faceUp = faceUp,
            width = width,
            height = height,
            finalRotation = finalRotation,
            isPlayable = isPlayable,
        )
    }
}

@Composable
private fun DominoPieceAtlasImage(
    painter: BitmapPainter,
    piece: DominoPiece,
    faceUp: Boolean,
    width: Dp,
    height: Dp,
    finalRotation: Float,
    isPlayable: Boolean,
) {
    val shape = RoundedCornerShape(5.dp)

    val borderModifier = if (isPlayable) {
        Modifier.border(
            width = 2.dp,
            color = DominoSemanticColors.playableMove,
            shape = shape,
        )
    } else {
        Modifier.border(
            width = 1.dp,
            color = DominoColorTokens.InkBlue.copy(alpha = 0.18f),
            shape = shape,
        )
    }

    Box(
        modifier = Modifier
            .requiredWidth(width)
            .requiredHeight(height)
            .graphicsLayer {
                rotationZ = finalRotation
                clip = false
            }
            .clip(shape)
            .then(borderModifier),
    ) {
        Image(
            painter = painter,
            contentDescription = if (faceUp) {
                "Peça ${piece.left}-${piece.right}"
            } else {
                "Peça virada para baixo"
            },
            modifier = Modifier
                .requiredWidth(width)
                .requiredHeight(height),
            contentScale = ContentScale.FillBounds,
        )
    }
}

private data class DominoAtlasCell(
    val row: Int,
    val column: Int,
)

private fun getAtlasCellForPiece(
    piece: DominoPiece,
    faceUp: Boolean,
): DominoAtlasCell {
    if (!faceUp) {
        return getHiddenPieceAtlasCell()
    }

    val normalizedPiece = normalizePieceForAtlas(piece)
    val pieceIndex = ATLAS_PIECES.indexOf(normalizedPiece)

    if (pieceIndex == -1) {
        return getHiddenPieceAtlasCell()
    }

    return getAtlasCellForIndex(pieceIndex)
}

private fun getHiddenPieceAtlasCell(): DominoAtlasCell {
    val pieceIndex = ATLAS_PIECES.indexOf(null)

    if (pieceIndex == -1) {
        return DominoAtlasCell(
            row = 4,
            column = 0,
        )
    }

    return getAtlasCellForIndex(pieceIndex)
}

private fun getAtlasCellForIndex(
    pieceIndex: Int,
): DominoAtlasCell {
    return DominoAtlasCell(
        row = pieceIndex / ATLAS_COLUMNS,
        column = pieceIndex % ATLAS_COLUMNS,
    )
}

private fun normalizePieceForAtlas(
    piece: DominoPiece,
): DominoPiece {
    return if (piece.left <= piece.right) {
        piece
    } else {
        piece.flipped()
    }
}