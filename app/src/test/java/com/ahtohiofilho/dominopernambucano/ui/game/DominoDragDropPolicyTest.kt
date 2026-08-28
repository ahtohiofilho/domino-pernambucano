package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DominoDragDropPolicyTest {
    private val piece = DominoPiece(2, 5)
    private val table = Rect(0f, 0f, 100f, 100f)
    private val targets = listOf(
        DominoDropTargetInWindow(BoardSide.LEFT, Offset(10f, 50f)),
        DominoDropTargetInWindow(BoardSide.RIGHT, Offset(90f, 50f)),
    )

    @Test
    fun outside_table_has_no_highlight() {
        assertNull(
            findHighlightedDropSideOrNull(
                positionInWindow = Offset(-1f, 50f),
                playableMoves = listOf(move(BoardSide.LEFT)),
                dropTargets = targets,
                tableBoundsInWindow = table,
            ),
        )
    }

    @Test
    fun single_side_is_selected_anywhere_inside_table() {
        assertEquals(
            BoardSide.LEFT,
            findHighlightedDropSideOrNull(
                positionInWindow = Offset(95f, 95f),
                playableMoves = listOf(move(BoardSide.LEFT)),
                dropTargets = targets,
                tableBoundsInWindow = table,
            ),
        )
    }

    @Test
    fun two_sides_use_nearest_without_radius_gate() {
        val moves = listOf(move(BoardSide.LEFT), move(BoardSide.RIGHT))

        assertEquals(
            BoardSide.LEFT,
            findHighlightedDropSideOrNull(
                positionInWindow = Offset(35f, 90f),
                playableMoves = moves,
                dropTargets = targets,
                tableBoundsInWindow = table,
            ),
        )

        assertEquals(
            BoardSide.RIGHT,
            findHighlightedDropSideOrNull(
                positionInWindow = Offset(65f, 10f),
                playableMoves = moves,
                dropTargets = targets,
                tableBoundsInWindow = table,
            ),
        )
    }

    @Test
    fun tie_keeps_previous_highlight() {
        assertEquals(
            BoardSide.RIGHT,
            findHighlightedDropSideOrNull(
                positionInWindow = Offset(50f, 50f),
                playableMoves = listOf(
                    move(BoardSide.LEFT),
                    move(BoardSide.RIGHT),
                ),
                dropTargets = targets,
                tableBoundsInWindow = table,
                previousHighlightedSide = BoardSide.RIGHT,
            ),
        )
    }

    @Test
    fun tie_without_previous_uses_left_fallback() {
        assertEquals(
            BoardSide.LEFT,
            findHighlightedDropSideOrNull(
                positionInWindow = Offset(50f, 50f),
                playableMoves = listOf(
                    move(BoardSide.LEFT),
                    move(BoardSide.RIGHT),
                ),
                dropTargets = targets,
                tableBoundsInWindow = table,
            ),
        )
    }

    @Test
    fun no_valid_side_has_no_highlight_inside_table() {
        assertNull(
            findHighlightedDropSideOrNull(
                positionInWindow = Offset(50f, 50f),
                playableMoves = emptyList(),
                dropTargets = targets,
                tableBoundsInWindow = table,
            ),
        )
    }

    private fun move(side: BoardSide) = PlayableMove(
        piece = piece,
        side = side,
        flipped = false,
    )
}
