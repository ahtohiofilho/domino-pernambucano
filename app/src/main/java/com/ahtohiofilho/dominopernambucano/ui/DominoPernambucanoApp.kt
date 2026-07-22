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
import com.ahtohiofilho.dominopernambucano.online.AndroidGoogleIdTokenProvider
import com.ahtohiofilho.dominopernambucano.online.GoogleSignInConfig
import com.ahtohiofilho.dominopernambucano.online.GoogleSignInEnvironment
import com.ahtohiofilho.dominopernambucano.online.KtorRemoteOnlineApiClient
import com.ahtohiofilho.dominopernambucano.online.OnlineAppConfig
import com.ahtohiofilho.dominopernambucano.online.OnlineAppEnvironment
import com.ahtohiofilho.dominopernambucano.online.OnlineBackendMode
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountActionResult
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountManager
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleIdentityRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingRepository
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationMatchResumeActivation
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationMatchResumePreparation
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlineRepositoryFactory
import com.ahtohiofilho.dominopernambucano.online.OnlineSessionCredentialRepository
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlineSessionCredentialStore
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
    googleSignInConfig: GoogleSignInConfig =
        GoogleSignInEnvironment.Current,
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

    val onlineSessionCredentialRepository = remember(
        context.applicationContext,
    ) {
        OnlineSessionCredentialRepository(
            store = SharedPreferencesOnlineSessionCredentialStore(
                context = context.applicationContext,
            ),
        )
    }

    val googleIdentityApiClient = remember(
        onlineAppConfig.backendConfig,
    ) {
        if (
            onlineAppConfig.backendConfig.mode ==
            OnlineBackendMode.REMOTE
        ) {
            KtorRemoteOnlineApiClient(
                config = onlineAppConfig.backendConfig,
            )
        } else {
            null
        }
    }

    val onlineGoogleAccountManager = remember(
        context,
        googleSignInConfig,
        googleIdentityApiClient,
        onlineSessionCredentialRepository,
    ) {
        val googleIdentityRepository = googleIdentityApiClient?.let {
                apiClient ->
            OnlineGoogleIdentityRepository(
                apiClient = apiClient,
                sessionCredentialRepository =
                    onlineSessionCredentialRepository,
            )
        }

        val tokenProvider = if (
            googleSignInConfig.isConfigured &&
            googleIdentityRepository != null
        ) {
            AndroidGoogleIdTokenProvider(
                activityContext = context,
                config = googleSignInConfig,
            )
        } else {
            null
        }

        OnlineGoogleAccountManager(
            available =
                googleSignInConfig.isConfigured &&
                    googleIdentityRepository != null,
            googleIdTokenProvider = tokenProvider,
            googleIdentityRepository = googleIdentityRepository,
            sessionCredentialRepository =
                onlineSessionCredentialRepository,
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
        onlineSessionCredentialRepository,
        onlineParticipationBindingRepository,
    ) {
        OnlineRepositoryFactory.create(
            config = onlineAppConfig.backendConfig,
            traceLogger = onlineTraceLogger,
            sessionCredentialRepository =
                onlineSessionCredentialRepository,
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
        )
    }

    val sessionCoordinator = remember(
        onlineParticipationBindingRepository,
        onlineSessionCredentialRepository,
        onlineRoomRepository,
    ) {
        LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
            onlineSessionCredentialRepository =
                onlineSessionCredentialRepository,
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

    var onlineGoogleAccountActionInProgress by remember {
        mutableStateOf(false)
    }

    var onlineGoogleAccountFeedbackMessage by remember {
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
                onlineGoogleAccountStatus =
                    onlineGoogleAccountManager.currentStatus(),
                onlineGoogleAccountActionInProgress =
                    onlineGoogleAccountActionInProgress,
                onlineGoogleAccountFeedbackMessage =
                    onlineGoogleAccountFeedbackMessage,
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
                                when (
                                    val preparation =
                                        onlineRoomRepository
                                            .preparePendingParticipationMatchResume(
                                                binding =
                                                    pendingParticipation.binding,
                                            )
                                ) {
                                    OnlinePendingParticipationMatchResumePreparation
                                        .RemoteSessionRejected -> {
                                        sessionCoordinator
                                            .recordPendingOnlineParticipationRemoteSessionRejected(
                                                binding =
                                                    pendingParticipation.binding,
                                            )

                                        pendingOnlineMatchResumeFeedbackMessage =
                                            "Não foi possível retomar a participação online: " +
                                                    "a sessão deste dispositivo foi rejeitada."
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .NotAttempted -> {
                                        sessionCoordinator
                                            .invalidatePendingOnlineParticipationRemoteConfirmation(
                                                binding =
                                                    pendingParticipation.binding,
                                            )

                                        pendingOnlineMatchResumeFeedbackMessage =
                                            "Não foi possível retomar neste dispositivo. " +
                                                    "Verifique a participação novamente."
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .NoLongerRecoverable -> {
                                        sessionCoordinator
                                            .invalidatePendingOnlineParticipationRemoteConfirmation(
                                                binding =
                                                    pendingParticipation.binding,
                                            )

                                        pendingOnlineMatchResumeFeedbackMessage =
                                            "A participação online anterior não está mais " +
                                                    "disponível. Verifique novamente."
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .TemporarilyUnavailable -> {
                                        pendingOnlineMatchResumeFeedbackMessage =
                                            "Não foi possível retomar agora. Tente novamente."
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .WaitingForPlayers -> {
                                        when (
                                            val activation =
                                                onlineRoomRepository
                                                    .activatePendingParticipationRoomResume(
                                                        preparation = preparation,
                                                    )
                                        ) {
                                            OnlinePendingParticipationMatchResumeActivation
                                                .Activated -> {
                                                sessionCoordinator.dispatch(
                                                    DominoSessionCommand
                                                        .OpenResumedOnlineRoom(
                                                            binding =
                                                                preparation.binding,
                                                        ),
                                                )
                                            }

                                            is OnlinePendingParticipationMatchResumeActivation
                                                .NotAttempted -> {
                                                sessionCoordinator
                                                    .invalidatePendingOnlineParticipationRemoteConfirmation(
                                                        binding =
                                                            preparation.binding,
                                                    )

                                                pendingOnlineMatchResumeFeedbackMessage =
                                                    "Não foi possível reabrir a sala neste " +
                                                            "dispositivo. Verifique a " +
                                                            "participação novamente."
                                            }

                                            is OnlinePendingParticipationMatchResumeActivation
                                                .TemporarilyUnavailable -> {
                                                pendingOnlineMatchResumeFeedbackMessage =
                                                    "Não foi possível reabrir a sala agora. " +
                                                            "Tente novamente."
                                            }
                                        }
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .Ready -> {
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

                                        when (
                                            val activation =
                                                onlineRoomRepository
                                                    .activatePendingParticipationMatchResume(
                                                        preparation = preparation,
                                                    )
                                        ) {
                                            OnlinePendingParticipationMatchResumeActivation
                                                .Activated -> {
                                                sessionCoordinator.dispatch(
                                                    DominoSessionCommand.StartOnlineMatch(
                                                        matchCoordinator = matchCoordinator,
                                                    ),
                                                )

                                                createdMatchCoordinator = null
                                            }

                                            is OnlinePendingParticipationMatchResumeActivation
                                                .NotAttempted -> {
                                                sessionCoordinator
                                                    .invalidatePendingOnlineParticipationRemoteConfirmation(
                                                        binding =
                                                            preparation.binding,
                                                    )

                                                pendingOnlineMatchResumeFeedbackMessage =
                                                    "Não foi possível retomar a partida neste " +
                                                            "dispositivo. Verifique a " +
                                                            "participação novamente."
                                            }

                                            is OnlinePendingParticipationMatchResumeActivation
                                                .TemporarilyUnavailable -> {
                                                pendingOnlineMatchResumeFeedbackMessage =
                                                    "Não foi possível retomar a partida agora. " +
                                                            "Tente novamente."
                                            }
                                        }
                                    }
                                }
                            } finally {
                                createdMatchCoordinator?.dispose()

                                pendingOnlineMatchResumeInProgress = false
                            }
                        }
                    }
                },
                onConnectGoogleAccountClick = {
                    if (!onlineGoogleAccountActionInProgress) {
                        onlineGoogleAccountActionInProgress = true
                        onlineGoogleAccountFeedbackMessage = null

                        menuCoroutineScope.launch {
                            try {
                                when (
                                    val result =
                                        onlineGoogleAccountManager.connect()
                                ) {
                                    is OnlineGoogleAccountActionResult.Success -> {
                                        onlineGoogleAccountFeedbackMessage =
                                            "Conta conectada com sucesso."
                                    }

                                    OnlineGoogleAccountActionResult.Cancelled -> {
                                        onlineGoogleAccountFeedbackMessage = null
                                    }

                                    is OnlineGoogleAccountActionResult.Failure -> {
                                        onlineGoogleAccountFeedbackMessage =
                                            result.message
                                    }
                                }
                            } finally {
                                onlineGoogleAccountActionInProgress = false
                            }
                        }
                    }
                },
            )
        }

        DominoSessionState.PlayModeSelection -> {
            PlayModeScreen(
                onlineDisplayName = onlinePlayerIdentity.displayName,
                onlineTableName = onlinePlayerIdentity.tableName,
                onOnlineDisplayNameChange = { displayName ->
                    onlinePlayerIdentity =
                        onlinePlayerIdentityStore.updateDisplayName(
                            displayName = displayName,
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

        is DominoSessionState.OnlineResumedRoom -> {
            OnlineCreateRoomRoute(
                roomRepository = onlineRoomRepository,
                localPlayerIdentity = onlinePlayerIdentity,
                resumedParticipationBinding = state.binding,
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
                        DominoSessionCommand.BackToMainMenu,
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
