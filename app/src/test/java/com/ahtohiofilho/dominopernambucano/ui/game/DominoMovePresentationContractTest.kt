package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.ui.geometry.Offset
import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DominoMovePresentationContractTest {
    @Test
    fun `target from the right branch is rejected by a later left presentation`() {
        val boardChain = DominoBoardChain(
            openingPiece = DominoPiece(
                left = 6,
                right = 6,
            ),
        )
        val rightPresentation = createPresentationKey(
            boardChain = boardChain,
            move = PlayableMove(
                piece = DominoPiece(
                    left = 6,
                    right = 3,
                ),
                side = BoardSide.RIGHT,
                flipped = false,
            ),
        )
        val leftPresentation = createPresentationKey(
            boardChain = boardChain,
            move = PlayableMove(
                piece = DominoPiece(
                    left = 2,
                    right = 6,
                ),
                side = BoardSide.LEFT,
                flipped = false,
            ),
        )
        val staleRightTarget = DominoMoveTargetInWindow(
            presentationKey = rightPresentation,
            positionInWindow = Offset(
                x = 640f,
                y = 420f,
            ),
            rotationDegrees = 90f,
        )

        assertNull(
            staleRightTarget.forPresentation(
                presentationKey = leftPresentation,
            ),
        )
    }

    @Test
    fun `target is resolved only by the presentation that produced it`() {
        val presentation = createPresentationKey(
            boardChain = DominoBoardChain(
                openingPiece = DominoPiece(
                    left = 4,
                    right = 4,
                ),
                leftPieces = listOf(
                    DominoPiece(
                        left = 1,
                        right = 4,
                    ),
                ),
            ),
            move = PlayableMove(
                piece = DominoPiece(
                    left = 1,
                    right = 5,
                ),
                side = BoardSide.LEFT,
                flipped = false,
            ),
        )
        val target = DominoMoveTargetInWindow(
            presentationKey = presentation,
            positionInWindow = Offset(
                x = 210f,
                y = 310f,
            ),
            rotationDegrees = 180f,
        )

        assertEquals(
            target,
            target.forPresentation(
                presentationKey = presentation,
            ),
        )
    }

    private fun createPresentationKey(
        boardChain: DominoBoardChain,
        move: PlayableMove,
    ): DominoMovePresentationKey {
        return DominoMovePresentationKey(
            onlinePresentationId = "r42",
            roundNumber = 3,
            boardChainBeforeMove = boardChain,
            playerIndex = 1,
            move = move,
        )
    }
}
