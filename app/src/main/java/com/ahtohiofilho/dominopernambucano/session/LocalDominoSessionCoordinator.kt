package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.match.LocalDominoMatchCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
                    matchCoordinator = LocalDominoMatchCoordinator(),
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