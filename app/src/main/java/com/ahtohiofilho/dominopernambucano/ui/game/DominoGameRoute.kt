package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase

@Composable
fun DominoGameRoute(
    matchCoordinator: DominoMatchCoordinator,
    onBackToMenuClick: () -> Unit,
) {
    val runtimeState by matchCoordinator.state.collectAsState()
    val gameState = runtimeState.gameState

    val localPlayer = gameState.players[runtimeState.localPlayerIndex]

    val localPlayableMoves = if (
        runtimeState.phase == DominoMatchPhase.WaitingForLocalMove &&
        gameState.currentPlayerIndex == runtimeState.localPlayerIndex
    ) {
        localPlayer.hand.flatMap { piece ->
            getPlayableMoves(
                board = gameState.board,
                piece = piece,
                openingPiece = gameState.openingPiece,
            )
        }
    } else {
        emptyList()
    }

    DominoGameScreen(
        uiState = DominoGameUiState(
            gameState = gameState,
            roundNumber = runtimeState.roundNumber,
            localPlayerIndex = runtimeState.localPlayerIndex,
            phase = runtimeState.phase,
            localPlayableMoves = localPlayableMoves,
            playerClockMillis = runtimeState.playerClockMillis,
        ),
        onBackToMenuClick = onBackToMenuClick,
        onRoundIntroFinished = {
            matchCoordinator.dispatch(
                DominoMatchCommand.RoundIntroFinished,
            )
        },
        onLocalMoveSelected = { move ->
            matchCoordinator.dispatch(
                DominoMatchCommand.LocalMoveSelected(
                    move = move,
                )
            )
        },
        onTurnClockTick = { elapsedMillis ->
            matchCoordinator.dispatch(
                DominoMatchCommand.TurnClockTick(
                    elapsedMillis = elapsedMillis,
                )
            )
        },
        onBotDecisionReady = {
            matchCoordinator.dispatch(
                DominoMatchCommand.BotDecisionReady,
            )
        },
        onPresentationFinished = {
            matchCoordinator.dispatch(
                DominoMatchCommand.PresentationFinished,
            )
        },
        onStartNextRound = {
            matchCoordinator.dispatch(
                DominoMatchCommand.StartNextRound,
            )
        },
        onStartNewMatch = {
            matchCoordinator.dispatch(
                DominoMatchCommand.StartNewMatch,
            )
        },
    )
}