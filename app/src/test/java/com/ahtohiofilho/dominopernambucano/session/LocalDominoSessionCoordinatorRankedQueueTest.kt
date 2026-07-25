package com.ahtohiofilho.dominopernambucano.session

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDominoSessionCoordinatorRankedQueueTest {
    @Test
    fun ranked_queue_navigation_is_explicit_and_returns_to_play_modes() {
        val coordinator = LocalDominoSessionCoordinator()

        coordinator.dispatch(
            DominoSessionCommand.OpenPlayModeSelection,
        )
        coordinator.dispatch(
            DominoSessionCommand.OpenOnlineRankedQueue,
        )

        assertEquals(
            DominoSessionState.OnlineRankedQueue,
            coordinator.currentState,
        )

        coordinator.dispatch(
            DominoSessionCommand.BackToPlayModeSelection,
        )

        assertEquals(
            DominoSessionState.PlayModeSelection,
            coordinator.currentState,
        )
    }
}
