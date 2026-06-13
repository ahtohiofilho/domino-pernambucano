package com.ahtohiofilho.dominopernambucano.session

import kotlinx.coroutines.flow.StateFlow

interface DominoSessionCoordinator {
    val state: StateFlow<DominoSessionState>

    val currentState: DominoSessionState

    fun dispatch(
        command: DominoSessionCommand,
    )
}