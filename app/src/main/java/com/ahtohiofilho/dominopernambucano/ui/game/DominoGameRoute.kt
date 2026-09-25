package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import com.ahtohiofilho.dominopernambucano.match.LocalDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineGameUiTraceReporter
import com.ahtohiofilho.dominopernambucano.online.OnlineUiTraceContext
import com.ahtohiofilho.dominopernambucano.ui.audio.AndroidMatchResultSoundPlayer
import com.ahtohiofilho.dominopernambucano.ui.audio.AndroidPassKnockSoundPlayer
import com.ahtohiofilho.dominopernambucano.ui.audio.AndroidTilePlacementSoundPlayer
import com.ahtohiofilho.dominopernambucano.ui.audio.MatchResultAudioTransitionTracker
import com.ahtohiofilho.dominopernambucano.ui.audio.TilePlacementAudioTransitionTracker
import com.ahtohiofilho.dominopernambucano.ui.personalization.HandAppearanceTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun DominoGameRoute(
    matchCoordinator: DominoMatchCoordinator,
    handAppearanceTone: HandAppearanceTone =
        HandAppearanceTone.TONE_1,
    onBackToMenuClick: () -> Unit,
    onMatchFinished: () -> Unit = {},
    onMatchFinishedTransition: ((() -> Unit) -> Unit) = { continuation ->
        continuation()
    },
    onOfflineMatchExitTransition: ((() -> Unit) -> Unit) = { continuation ->
        continuation()
    },
    onlineUiTraceReporter: OnlineGameUiTraceReporter? = null,
    matchMode: DominoMatchMode = DominoMatchMode.OFFLINE_LOCAL,
    onRankedPlayAgain: () -> Unit = {
        error("Ranked post-match play-again route is not configured.")
    },
) {
    val runtimeState by matchCoordinator.state.collectAsState()
    val gameState = runtimeState.gameState
    val context = LocalContext.current

    val tilePlacementSoundPlayer = remember(context) {
        AndroidTilePlacementSoundPlayer(
            context = context.applicationContext,
        )
    }

    val passKnockSoundPlayer = remember(context) {
        AndroidPassKnockSoundPlayer(
            context = context.applicationContext,
        )
    }

    val tilePlacementAudioTracker = remember(matchCoordinator) {
        TilePlacementAudioTransitionTracker(
            initiallyPresentingMove =
                matchCoordinator.currentState.phase is
                    DominoMatchPhase.PresentingMove,
        )
    }

    val matchResultSoundPlayer = remember(context) {
        AndroidMatchResultSoundPlayer(
            context = context.applicationContext,
        )
    }

    val matchResultAudioTracker = remember(matchCoordinator) {
        MatchResultAudioTransitionTracker(
            initiallyMatchFinished =
                matchCoordinator.currentState.phase ==
                    DominoMatchPhase.MatchFinished,
        )
    }

    DisposableEffect(
        tilePlacementSoundPlayer,
        passKnockSoundPlayer,
        matchResultSoundPlayer,
    ) {
        onDispose {
            tilePlacementSoundPlayer.release()
            passKnockSoundPlayer.release()
            matchResultSoundPlayer.release()
        }
    }
    val onlineUiTraceContext = onlineUiTraceReporter?.currentUiTraceContext()
    val postMatchStatisticsTracker = remember(matchCoordinator) {
        PostMatchStatisticsTracker(
            initialState = matchCoordinator.currentState.gameState,
        )
    }
    val postMatchStatisticsState = remember(matchCoordinator) {
        mutableStateOf(postMatchStatisticsTracker.snapshot())
    }

    LaunchedEffect(matchCoordinator) {
        matchCoordinator.state.collect { state ->
            if (
                tilePlacementAudioTracker.accept(
                    isPresentingMove =
                        state.phase is DominoMatchPhase.PresentingMove,
                )
            ) {
                tilePlacementSoundPlayer.playTilePlacement()
            }

            val authoritativeAccumulator =
                onlineUiTraceReporter?.currentRankedMetricAccumulator()

            postMatchStatisticsState.value =
                if (authoritativeAccumulator != null) {
                    createPostMatchStatistics(
                        accumulator = authoritativeAccumulator,
                        finalOrCurrentState = state.gameState,
                    )
                } else {
                    postMatchStatisticsTracker.accept(state.gameState)
                }
        }
    }

    LaunchedEffect(
        matchCoordinator,
        runtimeState.phase,
    ) {
        if (
            runtimeState.phase == DominoMatchPhase.MatchFinished &&
            matchCoordinator is OnlineDominoMatchCoordinator
        ) {
            /*
             * The authoritative terminal state has reached the Compose
             * presentation. From this point a later process restart must not
             * reopen this already-seen result.
             */
            matchCoordinator.acknowledgeCompletedMatchPresented()
        }
    }

    LaunchedEffect(
        runtimeState.phase,
        gameState.gameWinnerTeamIndex,
        runtimeState.localPlayerIndex,
    ) {
        matchResultAudioTracker.accept(
            isMatchFinished =
                runtimeState.phase == DominoMatchPhase.MatchFinished,
            winnerTeamIndex =
                gameState.gameWinnerTeamIndex,
            localPlayerIndex =
                runtimeState.localPlayerIndex,
        )?.let { outcome ->
            matchResultSoundPlayer.play(outcome)
        }
    }

    LaunchedEffect(
        runtimeState.phase,
    ) {
        if (runtimeState.phase == DominoMatchPhase.MatchFinished) {
            onMatchFinished()
        }
    }

    /*
     * O fluxo visual normal continua solicitando a decisão do bot após
     * BotDecisionDelayMillis dentro de DominoGameScreen. Este watchdog é uma
     * segunda linha de defesa exclusiva do coordenador local: se aquela entrega
     * excepcionalmente não promover o estado, revalida o estado vivo do
     * coordenador e tenta novamente após o dobro do atraso normal.
     *
     * A repetição é segura porque LocalDominoMatchCoordinator ignora
     * BotDecisionReady assim que a fase deixa WaitingForLocalMove.
     */
    LaunchedEffect(
        matchCoordinator,
        runtimeState.phase,
        gameState.currentPlayerIndex,
        runtimeState.localPlayerIndex,
        runtimeState.roundNumber,
    ) {
        if (matchCoordinator !is LocalDominoMatchCoordinator) {
            return@LaunchedEffect
        }

        while (
            shouldRecoverLocalBotTurn(
                runtimeState = matchCoordinator.currentState,
            )
        ) {
            delay(DominoMatchTiming.BotDecisionDelayMillis * 2)

            if (
                !shouldRecoverLocalBotTurn(
                    runtimeState = matchCoordinator.currentState,
                )
            ) {
                break
            }

            matchCoordinator.dispatch(
                DominoMatchCommand.BotDecisionReady,
            )
        }
    }

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
            matchMode = matchMode,
            onlinePresentationId = onlineUiTraceContext?.presentationId,
            onlineSnapshotRevision = onlineUiTraceContext?.snapshotRevision,
            postMatchStatistics = postMatchStatisticsState.value,
        ),
        handAppearanceTone = handAppearanceTone,
        onBackToMenuClick = {
            if (runtimeState.phase == DominoMatchPhase.MatchFinished) {
                onMatchFinishedTransition(
                    onBackToMenuClick,
                )
            } else {
                onBackToMenuClick()
            }
        },
        onExitMatchClick = if (
            matchMode == DominoMatchMode.OFFLINE_LOCAL
        ) {
            {
                onOfflineMatchExitTransition(
                    onBackToMenuClick,
                )
            }
        } else {
            null
        },
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
        onPassKnockImpact = {
            passKnockSoundPlayer.playImpact()
        },
        onStartNextRound = {
            matchCoordinator.dispatch(
                DominoMatchCommand.StartNextRound,
            )
        },
        onStartNewMatch = {
            onMatchFinishedTransition {
                when (
                    resolvePostMatchPlayAgainAction(
                        matchMode = matchMode,
                    )
                ) {
                    PostMatchPlayAgainAction.START_NEW_MATCH -> {
                        matchCoordinator.dispatch(
                            DominoMatchCommand.StartNewMatch,
                        )
                    }

                    PostMatchPlayAgainAction.OPEN_RANKED_QUEUE -> {
                        onRankedPlayAgain()
                    }
                }
            }
        },
    )
}
internal enum class PostMatchPlayAgainAction {
    START_NEW_MATCH,
    OPEN_RANKED_QUEUE,
}

internal fun resolvePostMatchPlayAgainAction(
    matchMode: DominoMatchMode,
): PostMatchPlayAgainAction {
    return if (matchMode == DominoMatchMode.PUBLIC_RANKED) {
        PostMatchPlayAgainAction.OPEN_RANKED_QUEUE
    } else {
        PostMatchPlayAgainAction.START_NEW_MATCH
    }
}

internal fun shouldRecoverLocalBotTurn(
    runtimeState: DominoMatchRuntimeState,
): Boolean {
    return runtimeState.phase == DominoMatchPhase.WaitingForLocalMove &&
            runtimeState.gameState.currentPlayerIndex !=
            runtimeState.localPlayerIndex
}
