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
    private val localPlayerIndex: Int = 0,
    private val clockPolicy: DominoMatchClockPolicy = DominoMatchClockPolicy.Disabled,
) : DominoMatchCoordinator {
    private val mutableState = MutableStateFlow(
        createInitialRuntimeState(
            localPlayerIndex = localPlayerIndex,
            clockPolicy = clockPolicy,
        )
    )

    override val state: StateFlow<DominoMatchRuntimeState> =
        mutableState.asStateFlow()

    override val currentState: DominoMatchRuntimeState
        get() = mutableState.value

    private var playerIndexAwaitingClockReload: Int? = null

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

            is DominoMatchCommand.TurnClockTick -> {
                handleTurnClockTick(command)
            }

            DominoMatchCommand.BotDecisionReady -> {
                handleBotDecisionReady()
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

        if (
            runtimeState.clockPolicy.enabled &&
            isPlayerClockExpired(
                clocks = runtimeState.playerClockMillis,
                playerIndex = gameState.currentPlayerIndex,
            )
        ) {
            forceRandomMoveForCurrentPlayer(
                runtimeState = runtimeState,
            )
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

        playerIndexAwaitingClockReload = gameState.currentPlayerIndex

        mutableState.value = runtimeState.copy(
            phase = DominoMatchPhase.PresentingMove(
                playerIndex = gameState.currentPlayerIndex,
                move = command.move,
            ),
        )
    }

    private fun handleTurnClockTick(
        command: DominoMatchCommand.TurnClockTick,
    ) {
        if (command.elapsedMillis <= 0L) {
            return
        }

        val runtimeState = mutableState.value

        if (!runtimeState.clockPolicy.enabled) {
            return
        }

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return
        }

        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        if (
            isPlayerClockExpired(
                clocks = runtimeState.playerClockMillis,
                playerIndex = currentPlayerIndex,
            )
        ) {
            forceRandomMoveForCurrentPlayer(
                runtimeState = runtimeState,
            )
            return
        }

        val updatedClocks = decrementPlayerClockMillis(
            clocks = runtimeState.playerClockMillis,
            playerIndex = currentPlayerIndex,
            elapsedMillis = command.elapsedMillis,
        )

        val updatedRuntimeState = runtimeState.copy(
            playerClockMillis = updatedClocks,
        )

        if (
            isPlayerClockExpired(
                clocks = updatedClocks,
                playerIndex = currentPlayerIndex,
            )
        ) {
            forceRandomMoveForCurrentPlayer(
                runtimeState = updatedRuntimeState,
            )
        } else {
            mutableState.value = updatedRuntimeState
        }
    }

    private fun handleBotDecisionReady() {
        val runtimeState = mutableState.value

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return
        }

        val gameState = runtimeState.gameState

        if (gameState.currentPlayerIndex == runtimeState.localPlayerIndex) {
            return
        }

        if (
            runtimeState.clockPolicy.enabled &&
            isPlayerClockExpired(
                clocks = runtimeState.playerClockMillis,
                playerIndex = gameState.currentPlayerIndex,
            )
        ) {
            forceRandomMoveForCurrentPlayer(
                runtimeState = runtimeState,
            )
            return
        }

        val botMove = findBasicBotMove(
            state = gameState,
        )

        if (botMove != null) {
            playerIndexAwaitingClockReload = gameState.currentPlayerIndex

            mutableState.value = runtimeState.copy(
                phase = DominoMatchPhase.PresentingMove(
                    playerIndex = gameState.currentPlayerIndex,
                    move = botMove,
                ),
            )
        } else {
            mutableState.value = runtimeState.copy(
                phase = DominoMatchPhase.PresentingPass(
                    playerIndex = gameState.currentPlayerIndex,
                ),
            )
        }
    }

    private fun handlePresentationFinished() {
        val runtimeState = mutableState.value

        when (val phase = runtimeState.phase) {
            is DominoMatchPhase.PresentingMove -> {
                val updatedGameState = playMoveForCurrentPlayer(
                    state = runtimeState.gameState,
                    playableMove = phase.move,
                )

                val updatedRuntimeState = reloadPendingPlayerClock(
                    runtimeState = runtimeState.copy(
                        gameState = updatedGameState,
                    ),
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

                val updatedRuntimeState = reloadPendingPlayerClock(
                    runtimeState = runtimeState.copy(
                        gameState = updatedGameState,
                    ),
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

        val nextRoundGameState = createNextRoundDominoGameState(
            previousState = runtimeState.gameState,
        )

        playerIndexAwaitingClockReload = null

        mutableState.value = runtimeState.copy(
            gameState = nextRoundGameState,
            roundNumber = runtimeState.roundNumber + 1,
            phase = DominoMatchPhase.RoundIntro,
            playerClockMillis = createInitialPlayerClockMillis(
                playerCount = nextRoundGameState.players.size,
                clockPolicy = runtimeState.clockPolicy,
            ),
            playerClockReserveMillis = createInitialPlayerClockReserveMillis(
                playerCount = nextRoundGameState.players.size,
                clockPolicy = runtimeState.clockPolicy,
            ),
        )
    }

    private fun handleStartNewMatch() {
        val runtimeState = mutableState.value

        playerIndexAwaitingClockReload = null

        mutableState.value = createInitialRuntimeState(
            localPlayerIndex = runtimeState.localPlayerIndex,
            clockPolicy = runtimeState.clockPolicy,
        )
    }

    private fun forceRandomMoveForCurrentPlayer(
        runtimeState: DominoMatchRuntimeState,
    ) {
        playerIndexAwaitingClockReload = null

        val gameState = runtimeState.gameState

        val randomMove = findRandomPlayableMove(
            state = gameState,
        )

        if (randomMove != null) {
            mutableState.value = runtimeState.copy(
                phase = DominoMatchPhase.PresentingMove(
                    playerIndex = gameState.currentPlayerIndex,
                    move = randomMove,
                ),
            )
        } else {
            mutableState.value = runtimeState.copy(
                phase = DominoMatchPhase.PresentingPass(
                    playerIndex = gameState.currentPlayerIndex,
                ),
            )
        }
    }

    private fun reloadPendingPlayerClock(
        runtimeState: DominoMatchRuntimeState,
    ): DominoMatchRuntimeState {
        val playerIndex = playerIndexAwaitingClockReload
            ?: return runtimeState

        playerIndexAwaitingClockReload = null

        val reloadedClock = reloadPlayerClockFromReserveMillis(
            clocks = runtimeState.playerClockMillis,
            reserves = runtimeState.playerClockReserveMillis,
            playerIndex = playerIndex,
            playerRoundTimeMillis =
                runtimeState.clockPolicy.playerRoundTimeMillis,
        )

        return runtimeState.copy(
            playerClockMillis = reloadedClock.playerClockMillis,
            playerClockReserveMillis =
                reloadedClock.playerClockReserveMillis,
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

        return DominoMatchPhase.WaitingForLocalMove
    }
}

private fun createInitialRuntimeState(
    localPlayerIndex: Int,
    clockPolicy: DominoMatchClockPolicy,
): DominoMatchRuntimeState {
    val gameState = createInitialDominoGameState()

    return DominoMatchRuntimeState(
        gameState = gameState,
        roundNumber = 1,
        localPlayerIndex = localPlayerIndex,
        phase = DominoMatchPhase.RoundIntro,
        clockPolicy = clockPolicy,
        playerClockMillis = createInitialPlayerClockMillis(
            playerCount = gameState.players.size,
            clockPolicy = clockPolicy,
        ),
    )
}