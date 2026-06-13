package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDominoMatchCoordinatorTest {
    @Test
    fun match_starts_with_round_intro() {
        val coordinator = LocalDominoMatchCoordinator()

        assertEquals(
            DominoMatchPhase.RoundIntro,
            coordinator.currentState.phase,
        )

        assertEquals(
            1,
            coordinator.currentState.roundNumber,
        )
    }

    @Test
    fun round_intro_finished_advances_to_next_operational_phase() {
        val coordinator = LocalDominoMatchCoordinator()

        coordinator.dispatch(DominoMatchCommand.RoundIntroFinished)

        val phase = coordinator.currentState.phase

        assertTrue(
            phase == DominoMatchPhase.WaitingForLocalMove ||
                    phase is DominoMatchPhase.PresentingMove ||
                    phase is DominoMatchPhase.PresentingPass,
        )
    }

    @Test
    fun match_can_progress_without_ui_for_many_steps() {
        val coordinator = LocalDominoMatchCoordinator()

        repeat(300) {
            val runtimeState = coordinator.currentState

            when (val phase = runtimeState.phase) {
                DominoMatchPhase.RoundIntro -> {
                    coordinator.dispatch(DominoMatchCommand.RoundIntroFinished)
                }

                DominoMatchPhase.WaitingForLocalMove -> {
                    val gameState = runtimeState.gameState
                    val currentPlayer = gameState.players[gameState.currentPlayerIndex]

                    val move = currentPlayer.hand
                        .asSequence()
                        .flatMap { piece ->
                            getPlayableMoves(
                                board = gameState.board,
                                piece = piece,
                                openingPiece = gameState.openingPiece,
                            ).asSequence()
                        }
                        .firstOrNull()

                    requireNotNull(move) {
                        "WaitingForLocalMove sem jogada válida disponível."
                    }

                    coordinator.dispatch(
                        DominoMatchCommand.LocalMoveSelected(
                            move = move,
                        )
                    )
                }

                is DominoMatchPhase.PresentingMove -> {
                    coordinator.dispatch(DominoMatchCommand.PresentationFinished)
                }

                is DominoMatchPhase.PresentingPass -> {
                    coordinator.dispatch(DominoMatchCommand.PresentationFinished)
                }

                DominoMatchPhase.RoundSummary -> {
                    coordinator.dispatch(DominoMatchCommand.StartNextRound)
                }

                DominoMatchPhase.MatchFinished -> {
                    return
                }
            }
        }

        val finalPhase = coordinator.currentState.phase

        assertTrue(
            finalPhase == DominoMatchPhase.RoundIntro ||
                    finalPhase == DominoMatchPhase.WaitingForLocalMove ||
                    finalPhase is DominoMatchPhase.PresentingMove ||
                    finalPhase is DominoMatchPhase.PresentingPass ||
                    finalPhase == DominoMatchPhase.RoundSummary ||
                    finalPhase == DominoMatchPhase.MatchFinished,
        )
    }
}