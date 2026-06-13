package com.ahtohiofilho.dominopernambucano.session

sealed interface DominoSessionCommand {
    data object OpenPlayModeSelection : DominoSessionCommand

    data object StartLocalMatch : DominoSessionCommand

    data object OpenOnlineCreateRoom : DominoSessionCommand

    data object OpenOnlineJoinRoom : DominoSessionCommand

    data object BackToMainMenu : DominoSessionCommand

    data object BackToPlayModeSelection : DominoSessionCommand
}