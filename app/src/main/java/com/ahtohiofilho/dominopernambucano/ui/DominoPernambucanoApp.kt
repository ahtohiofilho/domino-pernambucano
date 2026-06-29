package com.ahtohiofilho.dominopernambucano.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ahtohiofilho.dominopernambucano.online.OnlineAppConfig
import com.ahtohiofilho.dominopernambucano.online.OnlineAppEnvironment
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationResumeResult
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRepositoryFactory
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlineAnonymousSessionStore
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlineParticipationStore
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlinePlayerIdentityStore
import com.ahtohiofilho.dominopernambucano.online.toOnlineParticipationBackendScope
import com.ahtohiofilho.dominopernambucano.online.observability.AndroidLogcatOnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.CompositeOnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchUploader
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.PersistentOnlineTraceOutbox
import com.ahtohiofilho.dominopernambucano.session.DominoSessionCommand
import com.ahtohiofilho.dominopernambucano.session.DominoSessionState
import com.ahtohiofilho.dominopernambucano.session.LocalDominoSessionCoordinator
import com.ahtohiofilho.dominopernambucano.ui.game.DominoGameRoute
import com.ahtohiofilho.dominopernambucano.ui.menu.MainMenuScreen
import com.ahtohiofilho.dominopernambucano.ui.menu.PlayModeScreen
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineCreateRoomRoute
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineJoinRoomRoute
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineParticipationRestoreScreen
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineResumedRoomRoute
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface OnlineParticipationBootstrapState {
    data object Checking : OnlineParticipationBootstrapState

    data object Ready : OnlineParticipationBootstrapState

    data class Unavailable(
        val reason: String,
    ) : OnlineParticipationBootstrapState
}

@Composable
fun DominoPernambucanoApp(
    onlineAppConfig: OnlineAppConfig = OnlineAppEnvironment.Current,
) {
    val context = LocalContext.current
    val appCoroutineScope = rememberCoroutineScope()

    val sessionCoordinator = remember {
        LocalDominoSessionCoordinator()
    }

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

    val onlineAnonymousSessionStore = remember(
        context.applicationContext,
    ) {
        SharedPreferencesOnlineAnonymousSessionStore(
            context = context.applicationContext,
        )
    }

    val onlineParticipationStore = remember(
        context.applicationContext,
    ) {
        SharedPreferencesOnlineParticipationStore(
            context = context.applicationContext,
        )
    }

    val onlineParticipationBackendScope = remember(
        onlineAppConfig.backendConfig,
    ) {
        onlineAppConfig.backendConfig.toOnlineParticipationBackendScope()
    }

    val onlineRoomRepository = remember(
        onlineAppConfig.backendConfig,
        onlineTraceLogger,
        onlineAnonymousSessionStore,
    ) {
        OnlineRepositoryFactory.create(
            config = onlineAppConfig.backendConfig,
            traceLogger = onlineTraceLogger,
            anonymousSessionStore = onlineAnonymousSessionStore,
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
    val onlineRoomSnapshot by onlineRoomRepository.roomSnapshot.collectAsState()
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

    /*
     * O binding local nunca substitui o servidor. Ele é apenas reconciliado
     * quando um snapshot do mesmo roomId chega pelo fluxo autoritativo.
     */
    LaunchedEffect(
        onlineRoomSnapshot,
        onlineParticipationBackendScope,
        onlineParticipationStore,
    ) {
        val binding = onlineParticipationStore.read()
            ?: return@LaunchedEffect

        if (binding.backendScope != onlineParticipationBackendScope) {
            onlineParticipationStore.clear()
            return@LaunchedEffect
        }

        val room = onlineRoomSnapshot ?: return@LaunchedEffect

        if (room.roomId != binding.roomId) {
            return@LaunchedEffect
        }

        val localPlayer = room.players.firstOrNull { player ->
            player.playerId == binding.playerId
        }

        if (
            room.status == OnlineRoomStatusDto.FINISHED ||
            room.status == OnlineRoomStatusDto.CLOSED ||
            localPlayer?.seatIndex != binding.seatIndex
        ) {
            onlineParticipationStore.clear()
            return@LaunchedEffect
        }

        onlineParticipationStore.write(
            binding.copy(
                matchId = room.matchId,
            ),
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

    var participationRestoreAttempt by remember {
        mutableIntStateOf(0)
    }

    var participationBootstrapState by remember {
        mutableStateOf<OnlineParticipationBootstrapState>(
            OnlineParticipationBootstrapState.Checking,
        )
    }

    LaunchedEffect(
        onlineRoomRepository,
        onlineParticipationStore,
        onlineParticipationBackendScope,
        participationRestoreAttempt,
    ) {
        val binding = onlineParticipationStore.read()

        if (binding == null) {
            participationBootstrapState = OnlineParticipationBootstrapState.Ready
            return@LaunchedEffect
        }

        if (binding.backendScope != onlineParticipationBackendScope) {
            onlineParticipationStore.clear()
            participationBootstrapState = OnlineParticipationBootstrapState.Ready
            return@LaunchedEffect
        }

        participationBootstrapState = OnlineParticipationBootstrapState.Checking

        val resumeResult = try {
            onlineRoomRepository.resumeParticipation(
                binding = binding,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }

            OnlineParticipationResumeResult.Unavailable(
                reason = error.message
                    ?: "Não foi possível consultar sua participação online.",
            )
        }

        when (resumeResult) {
            is OnlineParticipationResumeResult.WaitingRoom -> {
                val resumedBinding = binding.copy(
                    matchId = resumeResult.roomSnapshot.matchId,
                    seatIndex = resumeResult.localSeatIndex,
                )

                onlineParticipationStore.write(resumedBinding)
                participationBootstrapState = OnlineParticipationBootstrapState.Ready

                sessionCoordinator.dispatch(
                    DominoSessionCommand.OpenResumedOnlineRoom(
                        participationBinding = resumedBinding,
                    ),
                )
            }

            is OnlineParticipationResumeResult.ActiveMatch -> {
                val resumedBinding = binding.copy(
                    matchId = resumeResult.matchSnapshot.matchId,
                    seatIndex = resumeResult.localSeatIndex,
                )

                onlineParticipationStore.write(resumedBinding)
                participationBootstrapState = OnlineParticipationBootstrapState.Ready

                sessionCoordinator.dispatch(
                    DominoSessionCommand.StartOnlineMatch(
                        matchCoordinator = OnlineDominoMatchCoordinator(
                            repository = onlineRoomRepository,
                            roomId = resumeResult.matchSnapshot.roomId,
                            matchId = resumeResult.matchSnapshot.matchId,
                            localPlayerId = resumedBinding.playerId,
                            localPlayerIndex = resumedBinding.seatIndex,
                            initialSnapshot = resumeResult.matchSnapshot,
                            traceLogger = onlineTraceLogger,
                        ),
                    ),
                )
            }

            is OnlineParticipationResumeResult.Inactive -> {
                onlineParticipationStore.clear()
                participationBootstrapState = OnlineParticipationBootstrapState.Ready
            }

            is OnlineParticipationResumeResult.Unavailable -> {
                participationBootstrapState =
                    OnlineParticipationBootstrapState.Unavailable(
                        reason = resumeResult.reason,
                    )
            }
        }
    }

    when (val state = participationBootstrapState) {
        OnlineParticipationBootstrapState.Checking -> {
            OnlineParticipationRestoreScreen(
                isLoading = true,
                onRetryClick = { },
                onOpenMenuClick = { },
            )
            return
        }

        is OnlineParticipationBootstrapState.Unavailable -> {
            OnlineParticipationRestoreScreen(
                isLoading = false,
                message = state.reason,
                onRetryClick = {
                    participationRestoreAttempt += 1
                },
                onOpenMenuClick = {
                    participationBootstrapState =
                        OnlineParticipationBootstrapState.Ready
                },
            )
            return
        }

        OnlineParticipationBootstrapState.Ready -> Unit
    }

    val onlineDebugOptions = onlineAppConfig.debugOptions
    val sessionState by sessionCoordinator.state.collectAsState()

    when (val state = sessionState) {
        DominoSessionState.MainMenu -> {
            MainMenuScreen(
                onPlayClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.OpenPlayModeSelection,
                    )
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
                participationStore = onlineParticipationStore,
                participationBackendScope = onlineParticipationBackendScope,
                debugOptions = onlineDebugOptions,
                traceLogger = onlineTraceLogger,
                onStartOnlineMatch = { matchCoordinator ->
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartOnlineMatch(
                            matchCoordinator = matchCoordinator,
                        ),
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
                participationStore = onlineParticipationStore,
                participationBackendScope = onlineParticipationBackendScope,
                debugOptions = onlineDebugOptions,
                traceLogger = onlineTraceLogger,
                onStartOnlineMatch = { matchCoordinator ->
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartOnlineMatch(
                            matchCoordinator = matchCoordinator,
                        ),
                    )
                },
                onBackClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
            )
        }

        is DominoSessionState.OnlineResumedRoom -> {
            OnlineResumedRoomRoute(
                roomRepository = onlineRoomRepository,
                participationBinding = state.participationBinding,
                participationStore = onlineParticipationStore,
                traceLogger = onlineTraceLogger,
                onStartOnlineMatch = { matchCoordinator ->
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartOnlineMatch(
                            matchCoordinator = matchCoordinator,
                        ),
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
                    appCoroutineScope.launch {
                        onlineRoomRepository.leaveRoom()
                        onlineParticipationStore.clear()

                        sessionCoordinator.dispatch(
                            DominoSessionCommand.BackToPlayModeSelection,
                        )
                    }
                },
                onlineUiTraceReporter = state.matchCoordinator,
            )
        }
    }
}