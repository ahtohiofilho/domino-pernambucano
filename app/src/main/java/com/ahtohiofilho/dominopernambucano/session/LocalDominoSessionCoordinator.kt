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
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.PlayModeSelection
            }

            DominoSessionCommand.StartLocalMatch -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.LocalMatch(
                    matchCoordinator = createLocalMatchCoordinator(),
                )
            }

            is DominoSessionCommand.StartOnlineMatch -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.OnlineMatch(
                    matchCoordinator = command.matchCoordinator,
                )
            }

            DominoSessionCommand.OpenOnlineCreateRoom -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.OnlineCreateRoom
            }

            DominoSessionCommand.OpenOnlineJoinRoom -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.OnlineJoinRoom
            }

            DominoSessionCommand.BackToMainMenu -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.MainMenu
            }

            DominoSessionCommand.BackToPlayModeSelection -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.PlayModeSelection
            }
        }
    }

    private fun disposeCurrentOnlineCoordinatorIfNeeded() {
        val state = mutableState.value

        if (state is DominoSessionState.OnlineMatch) {
            state.matchCoordinator.dispose()
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