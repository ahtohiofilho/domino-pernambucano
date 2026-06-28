package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.online.OnlineGameUiTraceReporter
import com.ahtohiofilho.dominopernambucano.online.OnlineUiTraceContext

@Composable
fun DominoGameRoute(
    matchCoordinator: DominoMatchCoordinator,
    onBackToMenuClick: () -> Unit,
    onlineUiTraceReporter: OnlineGameUiTraceReporter? = null,
) {
    val runtimeState by matchCoordinator.state.collectAsState()
    val gameState = runtimeState.gameState
    val onlineUiTraceContext = onlineUiTraceReporter?.currentUiTraceContext()

    val localPlayer = gameState.players.getOrNull(
        runtimeState.localPlayerIndex,
    )

    val localPlayableMoves = if (
        runtimeState.phase == DominoMatchPhase.WaitingForLocalMove &&
        gameState.currentPlayerIndex == runtimeState.localPlayerIndex &&
        localPlayer != null
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
            playerClockReserveMillis =
                runtimeState.playerClockReserveMillis,
            isTurnClockEnabled = runtimeState.clockPolicy.enabled,
            turnClockTotalMillis = runtimeState.clockPolicy.playerRoundTimeMillis,
            onlinePresentationId = onlineUiTraceContext?.presentationId,
            onlineSnapshotRevision = onlineUiTraceContext?.snapshotRevision,
        ),
        onBackToMenuClick = onBackToMenuClick,
        onOnlineTrace = { type, presentationId, snapshotRevision, attributes ->
            onlineUiTraceReporter?.traceUiEvent(
                type = type,
                traceContext = OnlineUiTraceContext(
                    presentationId = presentationId,
                    snapshotRevision = snapshotRevision,
                ),
                attributes = attributes,
            )
        },
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