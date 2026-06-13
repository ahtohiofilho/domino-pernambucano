package com.ahtohiofilho.dominopernambucano.match

import kotlinx.coroutines.flow.StateFlow

interface DominoMatchCoordinator {
    val state: StateFlow<DominoMatchRuntimeState>

    val currentState: DominoMatchRuntimeState

    fun dispatch(
        command: DominoMatchCommand,
    )
}