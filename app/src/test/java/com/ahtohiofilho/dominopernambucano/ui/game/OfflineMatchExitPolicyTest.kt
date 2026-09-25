package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMatchExitPolicyTest {
    @Test
    fun offline_active_match_shows_exit_action() {
        assertTrue(
            shouldShowOfflineMatchExit(
                matchMode = DominoMatchMode.OFFLINE_LOCAL,
                phase = DominoMatchPhase.WaitingForLocalMove,
            ),
        )
    }

    @Test
    fun offline_match_finished_hides_exit_action() {
        assertFalse(
            shouldShowOfflineMatchExit(
                matchMode = DominoMatchMode.OFFLINE_LOCAL,
                phase = DominoMatchPhase.MatchFinished,
            ),
        )
    }

    @Test
    fun online_match_never_shows_offline_exit_action() {
        assertFalse(
            shouldShowOfflineMatchExit(
                matchMode = DominoMatchMode.PUBLIC_RANKED,
                phase = DominoMatchPhase.WaitingForLocalMove,
            ),
        )
    }
}