package com.ahtohiofilho.dominopernambucano.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ahtohiofilho.dominopernambucano.online.OnlineAppConfig
import com.ahtohiofilho.dominopernambucano.online.OnlineAppEnvironment
import com.ahtohiofilho.dominopernambucano.online.OnlineRepositoryFactory
import com.ahtohiofilho.dominopernambucano.online.observability.InMemoryOnlineTraceBuffer
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlinePlayerIdentityStore
import com.ahtohiofilho.dominopernambucano.session.DominoSessionCommand
import com.ahtohiofilho.dominopernambucano.session.DominoSessionState
import com.ahtohiofilho.dominopernambucano.session.LocalDominoSessionCoordinator
import com.ahtohiofilho.dominopernambucano.ui.game.DominoGameRoute
import com.ahtohiofilho.dominopernambucano.ui.menu.MainMenuScreen
import com.ahtohiofilho.dominopernambucano.ui.menu.PlayModeScreen
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineCreateRoomRoute
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineJoinRoomRoute

@Composable
fun DominoPernambucanoApp(
    onlineAppConfig: OnlineAppConfig = OnlineAppEnvironment.Current,
) {
    val context = LocalContext.current

    val sessionCoordinator = remember {
        LocalDominoSessionCoordinator()
    }

    val onlineTraceBuffer = remember {
        InMemoryOnlineTraceBuffer()
    }

    val onlineTraceLogger = remember(
        onlineTraceBuffer,
    ) {
        OnlineTraceLogger(
            sink = onlineTraceBuffer,
        )
    }

    val onlineRoomRepository = remember(
        onlineAppConfig.backendConfig,
        onlineTraceLogger,
    ) {
        OnlineRepositoryFactory.create(
            config = onlineAppConfig.backendConfig,
            traceLogger = onlineTraceLogger,
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
            )
        }
    }
}