package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.LocalDominoMatchCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ENABLE_OFFLINE_CLOCK_DEBUG = true

class LocalDominoSessionCoordinator : DominoSessionCoordinator {
    private val mutableState = MutableStateFlow<DominoSessionState>(
        DominoSessionState.MainMenu,
    )

    override val state: StateFlow<DominoSessionState> =
        mutableState.asStateFlow()

    override val currentState: DominoSessionState
        get() = mutableState.value

    override fun dispatch(
        command: DominoSessionCommand,
    ) {
        when (command) {
            DominoSessionCommand.OpenPlayModeSelection -> {
                mutableState.value = DominoSessionState.PlayModeSelection
            }

            DominoSessionCommand.StartLocalMatch -> {
                mutableState.value = DominoSessionState.LocalMatch(
                    matchCoordinator = createLocalMatchCoordinator(),
                )
            }

            DominoSessionCommand.OpenOnlineCreateRoom -> {
                mutableState.value = DominoSessionState.OnlineCreateRoom
            }

            DominoSessionCommand.OpenOnlineJoinRoom -> {
                mutableState.value = DominoSessionState.OnlineJoinRoom
            }

            DominoSessionCommand.BackToMainMenu -> {
                mutableState.value = DominoSessionState.MainMenu
            }

            DominoSessionCommand.BackToPlayModeSelection -> {
                mutableState.value = DominoSessionState.PlayModeSelection
            }
        }
    }
}

private fun createLocalMatchCoordinator(): LocalDominoMatchCoordinator {
    val clockPolicy = if (ENABLE_OFFLINE_CLOCK_DEBUG) {
        DominoMatchClockPolicy.OnlinePerPlayerRound
    } else {
        DominoMatchClockPolicy.Disabled
    }

    return LocalDominoMatchCoordinator(
        clockPolicy = clockPolicy,
    )
}