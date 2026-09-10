package com.ahtohiofilho.dominopernambucano.ui.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultAudioLogicTest {
    @Test
    fun `fires victory once during a final match episode`() {
        val tracker = MatchResultAudioTransitionTracker(
            initiallyMatchFinished = false,
        )

        assertNull(
            tracker.accept(
                isMatchFinished = false,
                winnerTeamIndex = null,
                localPlayerIndex = 0,
            ),
        )

        assertEquals(
            MatchResultAudioOutcome.Victory,
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 0,
                localPlayerIndex = 0,
            ),
        )

        assertNull(
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 0,
                localPlayerIndex = 0,
            ),
        )
    }

    @Test
    fun `fires defeat when opponent team wins`() {
        val tracker = MatchResultAudioTransitionTracker(
            initiallyMatchFinished = false,
        )

        assertEquals(
            MatchResultAudioOutcome.Defeat,
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 1,
                localPlayerIndex = 0,
            ),
        )
    }

    @Test
    fun `does not replay historical finished state`() {
        val tracker = MatchResultAudioTransitionTracker(
            initiallyMatchFinished = true,
        )

        assertNull(
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 0,
                localPlayerIndex = 0,
            ),
        )

        assertNull(
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 0,
                localPlayerIndex = 0,
            ),
        )
    }

    @Test
    fun `rearms after a new match starts`() {
        val tracker = MatchResultAudioTransitionTracker(
            initiallyMatchFinished = false,
        )

        assertEquals(
            MatchResultAudioOutcome.Victory,
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 0,
                localPlayerIndex = 0,
            ),
        )

        assertNull(
            tracker.accept(
                isMatchFinished = false,
                winnerTeamIndex = null,
                localPlayerIndex = 0,
            ),
        )

        assertEquals(
            MatchResultAudioOutcome.Defeat,
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 1,
                localPlayerIndex = 0,
            ),
        )
    }

    @Test
    fun `waits for winner if final phase arrives first`() {
        val tracker = MatchResultAudioTransitionTracker(
            initiallyMatchFinished = false,
        )

        assertNull(
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = null,
                localPlayerIndex = 0,
            ),
        )

        assertEquals(
            MatchResultAudioOutcome.Victory,
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 0,
                localPlayerIndex = 0,
            ),
        )

        assertNull(
            tracker.accept(
                isMatchFinished = true,
                winnerTeamIndex = 0,
                localPlayerIndex = 0,
            ),
        )
    }
}