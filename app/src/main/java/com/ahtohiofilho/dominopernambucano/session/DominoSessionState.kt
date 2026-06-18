package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator

sealed interface DominoSessionState {
    data object MainMenu : DominoSessionState

    data object PlayModeSelection : DominoSessionState

    data class LocalMatch(
        val matchCoordinator: DominoMatchCoordinator,
    ) : DominoSessionState

    data object OnlineCreateRoom : DominoSessionState

    data object OnlineJoinRoom : DominoSessionState

    data class OnlineMatch(
        val matchCoordinator: OnlineDominoMatchCoordinator,
    ) : DominoSessionState
}