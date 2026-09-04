package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineAuthoritativeClockProjectionTest {
    @Test
    fun delayed_heartbeat_projects_full_elapsed_time_from_authoritative_baseline() {
        val baseline = runtimeState()

        val projected = projectOnlineAuthoritativeClock(
            runtimeState = baseline,
            elapsedSinceSnapshotMillis = 3_250L,
        )

        val currentPlayerIndex =
            baseline.gameState.currentPlayerIndex

        assertEquals(
            16_750L,
            projected.playerClockMillis[currentPlayerIndex],
        )

        baseline.playerClockMillis.forEachIndexed {
                playerIndex,
                remainingMillis,
            ->
            if (playerIndex != currentPlayerIndex) {
                assertEquals(
                    remainingMillis,
                    projected.playerClockMillis[playerIndex],
                )
            }
        }
    }

    @Test
    fun repeated_projection_does_not_accumulate_previous_display_tick() {
        val authoritativeBaseline = runtimeState()
        val currentPlayerIndex =
            authoritativeBaseline.gameState.currentPlayerIndex

        val atThreeSeconds =
            projectOnlineAuthoritativeClock(
                runtimeState = authoritativeBaseline,
                elapsedSinceSnapshotMillis = 3_000L,
            )

        val atThreePointTwoFiveSeconds =
            projectOnlineAuthoritativeClock(
                runtimeState = authoritativeBaseline,
                elapsedSinceSnapshotMillis = 3_250L,
            )

        assertEquals(
            17_000L,
            atThreeSeconds.playerClockMillis[currentPlayerIndex],
        )
        assertEquals(
            16_750L,
            atThreePointTwoFiveSeconds
                .playerClockMillis[currentPlayerIndex],
        )
    }

    @Test
    fun projection_reaches_zero_without_forcing_client_game_transition() {
        val baseline = runtimeState()

        val projected = projectOnlineAuthoritativeClock(
            runtimeState = baseline,
            elapsedSinceSnapshotMillis = 25_000L,
        )

        val currentPlayerIndex =
            baseline.gameState.currentPlayerIndex

        assertEquals(
            0L,
            projected.playerClockMillis[currentPlayerIndex],
        )
        assertEquals(
            baseline.gameState,
            projected.gameState,
        )
        assertEquals(
            DominoMatchPhase.WaitingForLocalMove,
            projected.phase,
        )
    }

    @Test
    fun non_waiting_phase_is_not_locally_decremented() {
        val baseline = runtimeState().copy(
            phase = DominoMatchPhase.RoundIntro,
        )

        assertEquals(
            baseline,
            projectOnlineAuthoritativeClock(
                runtimeState = baseline,
                elapsedSinceSnapshotMillis = 10_000L,
            ),
        )
    }

    @Test
    fun negative_elapsed_time_is_clamped_to_zero() {
        val baseline = runtimeState()

        assertEquals(
            baseline,
            projectOnlineAuthoritativeClock(
                runtimeState = baseline,
                elapsedSinceSnapshotMillis = -3_000L,
            ),
        )
    }

    private fun runtimeState(): DominoMatchRuntimeState {
        val gameState = createInitialDominoGameState()

        return DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.WaitingForLocalMove,
            clockPolicy =
                DominoMatchClockPolicy.OnlinePerPlayerRound,
            playerClockMillis =
                List(gameState.players.size) {
                    20_000L
                },
            playerClockReserveMillis =
                List(gameState.players.size) {
                    20_000L
                },
        )
    }
}