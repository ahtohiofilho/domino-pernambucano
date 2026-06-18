package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator

sealed interface DominoSessionCommand {
    data object OpenPlayModeSelection : DominoSessionCommand

    data object StartLocalMatch : DominoSessionCommand

    data class StartOnlineMatch(
        val matchCoordinator: OnlineDominoMatchCoordinator,
    ) : DominoSessionCommand

    data object OpenOnlineCreateRoom : DominoSessionCommand

    data object OpenOnlineJoinRoom : DominoSessionCommand

    data object BackToMainMenu : DominoSessionCommand

    data object BackToPlayModeSelection : DominoSessionCommand
}