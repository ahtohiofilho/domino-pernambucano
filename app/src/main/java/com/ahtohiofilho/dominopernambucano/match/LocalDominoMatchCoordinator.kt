package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.domain.createNextRoundDominoGameState
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.domain.hasPlayablePiece
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import com.ahtohiofilho.dominopernambucano.domain.passTurn
import com.ahtohiofilho.dominopernambucano.domain.playMoveForCurrentPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalDominoMatchCoordinator(
    localPlayerIndex: Int = 0,
) : DominoMatchCoordinator {
    private val mutableState = MutableStateFlow(
        DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = localPlayerIndex,
            phase = DominoMatchPhase.RoundIntro,
        )
    )

    override val state: StateFlow<DominoMatchRuntimeState> =
        mutableState.asStateFlow()

    override val currentState: DominoMatchRuntimeState
        get() = mutableState.value

    override fun dispatch(
        command: DominoMatchCommand,
    ) {
        when (command) {
            DominoMatchCommand.RoundIntroFinished -> {
                handleRoundIntroFinished()
            }

            is DominoMatchCommand.LocalMoveSelected -> {
                handleLocalMoveSelected(command)
            }

            DominoMatchCommand.PresentationFinished -> {
                handlePresentationFinished()
            }

            DominoMatchCommand.StartNextRound -> {
                handleStartNextRound()
            }

            DominoMatchCommand.StartNewMatch -> {
                handleStartNewMatch()
            }
        }
    }

    private fun handleRoundIntroFinished() {
        val runtimeState = mutableState.value

        if (runtimeState.phase != DominoMatchPhase.RoundIntro) {
            return
        }

        mutableState.value = runtimeState.copy(
            phase = determineNextPhase(
                runtimeState = runtimeState,
            ),
        )
    }

    private fun handleLocalMoveSelected(
        command: DominoMatchCommand.LocalMoveSelected,
    ) {
        val runtimeState = mutableState.value

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return
        }

        val gameState = runtimeState.gameState

        if (gameState.currentPlayerIndex != runtimeState.localPlayerIndex) {
            return
        }

        val validMoves = getPlayableMoves(
            board = gameState.board,
            piece = command.move.piece,
            openingPiece = gameState.openingPiece,
        )

        if (!validMoves.contains(command.move)) {
            return
        }

        mutableState.value = runtimeState.copy(
            phase = DominoMatchPhase.PresentingMove(
                playerIndex = gameState.currentPlayerIndex,
                move = command.move,
            ),
        )
    }

    private fun handlePresentationFinished() {
        val runtimeState = mutableState.value

        when (val phase = runtimeState.phase) {
            is DominoMatchPhase.PresentingMove -> {
                val updatedGameState = playMoveForCurrentPlayer(
                    state = runtimeState.gameState,
                    playableMove = phase.move,
                )

                val updatedRuntimeState = runtimeState.copy(
                    gameState = updatedGameState,
                )

                mutableState.value = updatedRuntimeState.copy(
                    phase = determineNextPhase(
                        runtimeState = updatedRuntimeState,
                    ),
                )
            }

            is DominoMatchPhase.PresentingPass -> {
                val updatedGameState = passTurn(
                    state = runtimeState.gameState,
                )

                val updatedRuntimeState = runtimeState.copy(
                    gameState = updatedGameState,
                )

                mutableState.value = updatedRuntimeState.copy(
                    phase = determineNextPhase(
                        runtimeState = updatedRuntimeState,
                    ),
                )
            }

            else -> Unit
        }
    }

    private fun handleStartNextRound() {
        val runtimeState = mutableState.value

        if (runtimeState.phase != DominoMatchPhase.RoundSummary) {
            return
        }

        if (isGameFinished(runtimeState.gameState)) {
            mutableState.value = runtimeState.copy(
                phase = DominoMatchPhase.MatchFinished,
            )
            return
        }

        mutableState.value = runtimeState.copy(
            gameState = createNextRoundDominoGameState(
                previousState = runtimeState.gameState,
            ),
            roundNumber = runtimeState.roundNumber + 1,
            phase = DominoMatchPhase.RoundIntro,
        )
    }

    private fun handleStartNewMatch() {
        val runtimeState = mutableState.value

        mutableState.value = DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = runtimeState.localPlayerIndex,
            phase = DominoMatchPhase.RoundIntro,
        )
    }

    private fun determineNextPhase(
        runtimeState: DominoMatchRuntimeState,
    ): DominoMatchPhase {
        val gameState = runtimeState.gameState

        if (isGameFinished(gameState)) {
            return DominoMatchPhase.MatchFinished
        }

        if (isRoundFinished(gameState)) {
            return DominoMatchPhase.RoundSummary
        }

        val currentPlayerIndex = gameState.currentPlayerIndex

        if (!hasPlayablePiece(
                state = gameState,
                playerIndex = currentPlayerIndex,
            )
        ) {
            return DominoMatchPhase.PresentingPass(
                playerIndex = currentPlayerIndex,
            )
        }

        if (currentPlayerIndex == runtimeState.localPlayerIndex) {
            return DominoMatchPhase.WaitingForLocalMove
        }

        val botMove = findBasicBotMove(
            state = gameState,
        )

        if (botMove != null) {
            return DominoMatchPhase.PresentingMove(
                playerIndex = currentPlayerIndex,
                move = botMove,
            )
        }

        return DominoMatchPhase.PresentingPass(
            playerIndex = currentPlayerIndex,
        )
    }
}