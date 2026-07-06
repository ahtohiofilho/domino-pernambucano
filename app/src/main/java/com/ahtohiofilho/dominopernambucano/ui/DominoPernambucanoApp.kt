package com.ahtohiofilho.dominopernambucano.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineAppConfig
import com.ahtohiofilho.dominopernambucano.online.OnlineAppEnvironment
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingRepository
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationMatchResumeActivation
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationMatchResumePreparation
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlineRepositoryFactory
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlineAnonymousSessionStore
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlineParticipationBindingStore
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlinePlayerIdentityStore
import com.ahtohiofilho.dominopernambucano.online.observability.AndroidLogcatOnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.CompositeOnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchUploader
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.PersistentOnlineTraceOutbox
import com.ahtohiofilho.dominopernambucano.session.DominoSessionCommand
import com.ahtohiofilho.dominopernambucano.session.DominoSessionState
import com.ahtohiofilho.dominopernambucano.session.LocalDominoSessionCoordinator
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationSessionRejection
import com.ahtohiofilho.dominopernambucano.ui.game.DominoGameRoute
import com.ahtohiofilho.dominopernambucano.ui.menu.MainMenuScreen
import com.ahtohiofilho.dominopernambucano.ui.menu.PlayModeScreen
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineCreateRoomRoute
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineJoinRoomRoute
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun DominoPernambucanoApp(
    onlineAppConfig: OnlineAppConfig = OnlineAppEnvironment.Current,
) {
    val context = LocalContext.current

    val onlineTraceClientSessionId = remember {
        "android-${UUID.randomUUID()}"
    }

    val onlineTraceOutbox = remember(
        context.applicationContext,
    ) {
        PersistentOnlineTraceOutbox(
            directory = File(
                context.applicationContext.filesDir,
                "online-trace-outbox.jsonl",
            ),
        )
    }

    val onlineTraceSink = remember(
        onlineTraceOutbox,
    ) {
        CompositeOnlineTraceSink(
            onlineTraceOutbox,
            AndroidLogcatOnlineTraceSink(),
        )
    }

    val onlineTraceLogger = remember(
        onlineTraceClientSessionId,
        onlineTraceSink,
    ) {
        OnlineTraceLogger(
            sink = onlineTraceSink,
            clientSessionId = onlineTraceClientSessionId,
        )
    }

    val onlineAnonymousSessionRepository = remember(
        context.applicationContext,
    ) {
        OnlineAnonymousSessionRepository(
            store = SharedPreferencesOnlineAnonymousSessionStore(
                context = context.applicationContext,
            ),
        )
    }

    val onlineParticipationBindingRepository = remember(
        context.applicationContext,
    ) {
        OnlineParticipationBindingRepository(
            store = SharedPreferencesOnlineParticipationBindingStore(
                context = context.applicationContext,
            ),
        )
    }

    val onlineRoomRepository = remember(
        onlineAppConfig.backendConfig,
        onlineTraceLogger,
        onlineAnonymousSessionRepository,
        onlineParticipationBindingRepository,
    ) {
        OnlineRepositoryFactory.create(
            config = onlineAppConfig.backendConfig,
            traceLogger = onlineTraceLogger,
            anonymousSessionRepository = onlineAnonymousSessionRepository,
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
        )
    }

    val sessionCoordinator = remember(
        onlineParticipationBindingRepository,
        onlineAnonymousSessionRepository,
        onlineRoomRepository,
    ) {
        LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
            onlineAnonymousSessionRepository =
                onlineAnonymousSessionRepository,
            onlineRoomRepository = onlineRoomRepository,
        )
    }

    val onlineTraceBatchUploader = remember(
        onlineRoomRepository,
        onlineTraceOutbox,
    ) {
        OnlineTraceBatchUploader(
            repository = onlineRoomRepository,
            traceOutbox = onlineTraceOutbox,
        )
    }

    val onlineTracePendingEntryVersion by onlineTraceOutbox
        .pendingEntryVersion
        .collectAsState()

    val onlineMatchSnapshot by onlineRoomRepository.matchSnapshot.collectAsState()

    LaunchedEffect(
        onlineTracePendingEntryVersion,
        onlineMatchSnapshot?.roomId,
        onlineMatchSnapshot?.matchId,
    ) {
        onlineTraceOutbox.retryPendingPersistence()

        val snapshot = onlineMatchSnapshot ?: return@LaunchedEffect

        onlineTraceBatchUploader.flushPendingEntries(
            roomId = snapshot.roomId,
            matchId = snapshot.matchId,
        )
    }

    val onlinePlayerIdentityStore = remember(
        context.applicationContext,
    ) {
        SharedPreferencesOnlinePlayerIdentityStore(
            context = context.applicationContext,
        )
    }

    var onlinePlayerIdentity by remember(
        onlinePlayerIdentityStore,
    ) {
        mutableStateOf(
            onlinePlayerIdentityStore.getOrCreate(),
        )
    }

    val onlineDebugOptions = onlineAppConfig.debugOptions

    val sessionState by sessionCoordinator.state.collectAsState()

    val menuCoroutineScope = rememberCoroutineScope()

    var pendingOnlineMatchResumeInProgress by remember {
        mutableStateOf(false)
    }

    var pendingOnlineMatchResumeFeedbackMessage by remember {
        mutableStateOf<String?>(null)
    }

    when (val state = sessionState) {
        is DominoSessionState.MainMenu -> {
            MainMenuScreen(
                pendingOnlineParticipation =
                    state.pendingOnlineParticipation,
                pendingOnlineParticipationInspection =
                    state.pendingOnlineParticipationInspection,
                pendingOnlineParticipationSessionRejection =
                    state.pendingOnlineParticipationSessionRejection,
                pendingOnlineMatchResumeInProgress =
                    pendingOnlineMatchResumeInProgress,
                pendingOnlineMatchResumeFeedbackMessage =
                    pendingOnlineMatchResumeFeedbackMessage,
                onPlayClick = {
                    if (
                        !pendingOnlineMatchResumeInProgress &&
                        state.pendingOnlineParticipationInspection
                                !is OnlinePendingParticipationInspectionState
                        .InProgress
                    ) {
                        sessionCoordinator.dispatch(
                            DominoSessionCommand.OpenPlayModeSelection,
                        )
                    }
                },
                onInspectPendingOnlineParticipationClick = {
                    if (!pendingOnlineMatchResumeInProgress) {
                        menuCoroutineScope.launch {
                            sessionCoordinator
                                .inspectPendingOnlineParticipation()
                        }
                    }
                },
                onDiscardRejectedPendingOnlineParticipationClick = {
                    pendingOnlineMatchResumeFeedbackMessage = null

                    sessionCoordinator
                        .discardRemoteSessionRejectedPendingOnlineParticipation()
                },
                onResumePendingOnlineMatchClick = {
                    val pendingParticipation =
                        state.pendingOnlineParticipation

                    val completedInspection =
                        state.pendingOnlineParticipationInspection
                                as? OnlinePendingParticipationInspectionState
                        .Completed

                    val hasRecoverableInspection =
                        completedInspection?.result is
                                OnlinePendingParticipationRemoteInspection
                                .Recoverable

                    val hasRemoteSessionRejection =
                        state.pendingOnlineParticipationSessionRejection is
                                OnlinePendingParticipationSessionRejection
                                .RemoteSessionRejected

                    if (
                        !pendingOnlineMatchResumeInProgress &&
                        pendingParticipation is
                                OnlinePendingParticipationLocalResolution
                                .ReadyForRemoteReconciliation &&
                        hasRecoverableInspection &&
                        !hasRemoteSessionRejection
                    ) {
                        pendingOnlineMatchResumeInProgress = true
                        pendingOnlineMatchResumeFeedbackMessage = null

                        menuCoroutineScope.launch {
                            var createdMatchCoordinator:
                                    OnlineDominoMatchCoordinator? = null

                            try {
                                val preparation =
                                    onlineRoomRepository
                                        .preparePendingParticipationMatchResume(
                                            binding =
                                                pendingParticipation.binding,
                                        )

                                if (
                                    preparation is
                                            OnlinePendingParticipationMatchResumePreparation
                                            .RemoteSessionRejected
                                ) {
                                    sessionCoordinator
                                        .recordPendingOnlineParticipationRemoteSessionRejected(
                                            binding =
                                                pendingParticipation.binding,
                                        )

                                    pendingOnlineMatchResumeFeedbackMessage =
                                        "N\u00e3o foi poss\u00edvel retomar a partida: " +
                                                "a sess\u00e3o online deste dispositivo foi rejeitada."
                                    return@launch
                                }

                                if (
                                    preparation !is
                                            OnlinePendingParticipationMatchResumePreparation
                                            .Ready
                                ) {
                                    return@launch
                                }

                                val matchCoordinator =
                                    OnlineDominoMatchCoordinator(
                                        repository = onlineRoomRepository,
                                        roomId =
                                            preparation.roomSnapshot.roomId,
                                        matchId =
                                            preparation.matchSnapshot.matchId,
                                        localPlayerId =
                                            preparation.binding.playerId,
                                        localPlayerIndex =
                                            preparation.binding.localSeatIndex,
                                        initialSnapshot =
                                            preparation.matchSnapshot,
                                        traceLogger = onlineTraceLogger,
                                    )

                                createdMatchCoordinator = matchCoordinator

                                val activation =
                                    onlineRoomRepository
                                        .activatePendingParticipationMatchResume(
                                            preparation = preparation,
                                        )

                                if (
                                    activation !is
                                            OnlinePendingParticipationMatchResumeActivation
                                            .Activated
                                ) {
                                    return@launch
                                }

                                sessionCoordinator.dispatch(
                                    DominoSessionCommand.StartOnlineMatch(
                                        matchCoordinator = matchCoordinator,
                                    ),
                                )

                                createdMatchCoordinator = null
                            } finally {
                                createdMatchCoordinator?.dispose()

                                pendingOnlineMatchResumeInProgress = false
                            }
                        }
                    }
                },
            )
        }

        DominoSessionState.PlayModeSelection -> {
            PlayModeScreen(
                onlinePlayerName = onlinePlayerIdentity.playerName,
                onOnlinePlayerNameChange = { playerName ->
                    onlinePlayerIdentity =
                        onlinePlayerIdentityStore.updatePlayerName(
                            playerName = playerName,
                        )
                },
                onBackClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToMainMenu,
                    )
                },
                onLocalGameClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartLocalMatch,
                    )
                },
                onCreateOnlineRoomClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.OpenOnlineCreateRoom,
                    )
                },
                onJoinOnlineRoomClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.OpenOnlineJoinRoom,
                    )
                },
            )
        }

        is DominoSessionState.LocalMatch -> {
            DominoGameRoute(
                matchCoordinator = state.matchCoordinator,
                onBackToMenuClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
            )
        }

        DominoSessionState.OnlineCreateRoom -> {
            OnlineCreateRoomRoute(
                roomRepository = onlineRoomRepository,
                localPlayerIdentity = onlinePlayerIdentity,
                debugOptions = onlineDebugOptions,
                traceLogger = onlineTraceLogger,
                onStartOnlineMatch = { matchCoordinator ->
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartOnlineMatch(
                            matchCoordinator = matchCoordinator,
                        )
                    )
                },
                onBackClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
            )
        }

        DominoSessionState.OnlineJoinRoom -> {
            OnlineJoinRoomRoute(
                roomRepository = onlineRoomRepository,
                localPlayerIdentity = onlinePlayerIdentity,
                debugOptions = onlineDebugOptions,
                traceLogger = onlineTraceLogger,
                onStartOnlineMatch = { matchCoordinator ->
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartOnlineMatch(
                            matchCoordinator = matchCoordinator,
                        )
                    )
                },
                onBackClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
            )
        }

        is DominoSessionState.OnlineMatch -> {
            DominoGameRoute(
                matchCoordinator = state.matchCoordinator,
                onBackToMenuClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
                onlineUiTraceReporter = state.matchCoordinator,
            )
        }
    }
}