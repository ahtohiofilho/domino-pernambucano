package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineDominoBoardChainDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoGameStateDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoPieceDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchClockPolicyDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchPhaseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchPhaseTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticDecisionCadenceTest {
    @Test
    fun all_profiles_have_stable_personality_centers_inside_expected_band() {
        val centers = syntheticRoster.map { profile ->
            val first = resolveSyntheticPersonalityBaseDelayMillis(
                identityKey = profile.tableCode,
            )
            val second = resolveSyntheticPersonalityBaseDelayMillis(
                identityKey = profile.tableCode,
            )

            assertEquals(first, second)
            assertTrue(first in 1_800L..3_200L)

            first
        }

        assertTrue(centers.distinct().size > 20)
    }

    @Test
    fun per_turn_delay_varies_while_remaining_between_one_and_four_seconds() {
        val delays = (1..24).map { turnIndex ->
            val turnKey = SyntheticDecisionTurnKey(
                roundNumber = turnIndex,
                localSeatIndex = 1,
                gameState = gameState(
                    currentPlayerIndex = 1,
                    marker = turnIndex,
                ),
            )

            resolveSyntheticDecisionDelayMillis(
                identityKey = "MCO",
                turnKey = turnKey,
            )
        }

        assertTrue(delays.all { delay -> delay in 1_000L..4_000L })
        assertTrue(delays.distinct().size >= 8)
        assertTrue(delays.any { delay -> delay < 2_000L })
        assertTrue(delays.any { delay -> delay > 2_500L })
    }

    @Test
    fun same_logical_turn_keeps_same_deadline_across_revision_and_clock_ticks() {
        val firstObservedAt = 10_000L
        val snapshot = snapshot(
            revision = 7L,
            phase = OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE,
            currentPlayerIndex = 1,
            marker = 1,
        )

        val first = resolveSyntheticDecisionCadence(
            currentState = null,
            snapshot = snapshot,
            localSeatIndex = 1,
            identityKey = "MCO",
            nowEpochMillis = firstObservedAt,
        )

        assertFalse(first.readyToAct)
        val firstState = requireNotNull(first.state)
        assertTrue(firstState.delayMillis in 1_000L..4_000L)
        assertEquals(
            firstObservedAt + firstState.delayMillis,
            first.nextStepAtEpochMillis,
        )

        val tickerRevision = snapshot.copy(
            revision = 8L,
            playerClockMillis =
                listOf(20_000L, 19_250L, 20_000L, 20_000L),
        )

        val justBefore = resolveSyntheticDecisionCadence(
            currentState = firstState,
            snapshot = tickerRevision,
            localSeatIndex = 1,
            identityKey = "MCO",
            nowEpochMillis = firstState.readyAtEpochMillis - 1L,
        )

        assertFalse(justBefore.readyToAct)
        assertEquals(firstState, justBefore.state)
        assertEquals(
            firstState.readyAtEpochMillis,
            justBefore.nextStepAtEpochMillis,
        )

        val ready = resolveSyntheticDecisionCadence(
            currentState = justBefore.state,
            snapshot = tickerRevision,
            localSeatIndex = 1,
            identityKey = "MCO",
            nowEpochMillis = firstState.readyAtEpochMillis,
        )

        assertTrue(ready.readyToAct)
        assertNull(ready.nextStepAtEpochMillis)
        assertEquals(firstState, ready.state)
    }

    @Test
    fun changed_logical_turn_gets_a_fresh_per_turn_delay() {
        val firstSnapshot = snapshot(
            revision = 10L,
            phase = OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE,
            currentPlayerIndex = 1,
            marker = 1,
        )

        val first = resolveSyntheticDecisionCadence(
            currentState = null,
            snapshot = firstSnapshot,
            localSeatIndex = 1,
            identityKey = "MCO",
            nowEpochMillis = 1_000L,
        )
        val firstState = requireNotNull(first.state)

        val otherPlayersTurn = firstSnapshot.copy(
            revision = 11L,
            gameState = firstSnapshot.gameState.copy(
                currentPlayerIndex = 2,
            ),
        )

        val cleared = resolveSyntheticDecisionCadence(
            currentState = firstState,
            snapshot = otherPlayersTurn,
            localSeatIndex = 1,
            identityKey = "MCO",
            nowEpochMillis = 1_500L,
        )

        assertTrue(cleared.readyToAct)
        assertNull(cleared.state)

        val newSnapshot = snapshot(
            revision = 12L,
            phase = OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE,
            currentPlayerIndex = 1,
            marker = 2,
        )

        val fresh = resolveSyntheticDecisionCadence(
            currentState = firstState,
            snapshot = newSnapshot,
            localSeatIndex = 1,
            identityKey = "MCO",
            nowEpochMillis = 3_000L,
        )
        val freshState = requireNotNull(fresh.state)

        assertFalse(fresh.readyToAct)
        assertTrue(freshState.turnKey != firstState.turnKey)
        assertEquals(
            3_000L + freshState.delayMillis,
            fresh.nextStepAtEpochMillis,
        )
        assertTrue(freshState.delayMillis in 1_000L..4_000L)
    }

    @Test
    fun different_identities_keep_different_stable_tendencies() {
        val fastOrSlowProfiles = listOf("JAL", "MCO", "RLI", "CRO", "BME", "VSI")
        val centers = fastOrSlowProfiles.associateWith { code ->
            resolveSyntheticPersonalityBaseDelayMillis(
                identityKey = code,
            )
        }

        assertTrue(centers.values.distinct().size >= 4)

        centers.forEach { (code, center) ->
            assertEquals(
                center,
                resolveSyntheticPersonalityBaseDelayMillis(
                    identityKey = code,
                ),
            )
        }
    }

    @Test
    fun round_summary_transition_is_not_artificially_delayed() {
        val resolution = resolveSyntheticDecisionCadence(
            currentState = null,
            snapshot = snapshot(
                revision = 15L,
                phase = OnlineMatchPhaseTypeDto.ROUND_SUMMARY,
                currentPlayerIndex = 1,
                marker = 3,
            ),
            localSeatIndex = 1,
            identityKey = "MCO",
            nowEpochMillis = 5_000L,
        )

        assertTrue(resolution.readyToAct)
        assertNull(resolution.state)
        assertNull(resolution.nextStepAtEpochMillis)
    }

    private fun snapshot(
        revision: Long,
        phase: OnlineMatchPhaseTypeDto,
        currentPlayerIndex: Int,
        marker: Int,
    ): OnlineMatchSnapshotDto {
        return OnlineMatchSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = revision,
            roundNumber = marker,
            gameState = gameState(
                currentPlayerIndex = currentPlayerIndex,
                marker = marker,
                roundSummary =
                    phase ==
                        OnlineMatchPhaseTypeDto.ROUND_SUMMARY,
            ),
            phase = OnlineMatchPhaseDto(type = phase),
            clockPolicy =
                OnlineMatchClockPolicyDto.ONLINE_PER_PLAYER_ROUND,
            playerClockMillis =
                listOf(20_000L, 20_000L, 20_000L, 20_000L),
            playerClockReserveMillis =
                listOf(20_000L, 20_000L, 20_000L, 20_000L),
        )
    }

    private fun gameState(
        currentPlayerIndex: Int,
        marker: Int,
        roundSummary: Boolean = false,
    ): OnlineDominoGameStateDto {
        val sixSix = OnlineDominoPieceDto(6, 6)
        val hidden = OnlineDominoPieceDto(-1, -1)

        return OnlineDominoGameStateDto(
            board =
                if (marker % 2 == 0) {
                    listOf(
                        OnlineDominoPieceDto(
                            marker % 7,
                            (marker + 2) % 7,
                        ),
                    )
                } else {
                    emptyList()
                },
            boardChain = OnlineDominoBoardChainDto(),
            players = listOf(
                OnlineDominoPlayerDto(
                    id = 0,
                    name = "HUM",
                    hand = listOf(hidden),
                ),
                OnlineDominoPlayerDto(
                    id = 1,
                    name = "MCO",
                    hand = listOf(sixSix),
                ),
                OnlineDominoPlayerDto(
                    id = 2,
                    name = "RLI",
                    hand = listOf(hidden),
                ),
                OnlineDominoPlayerDto(
                    id = 3,
                    name = "EXT",
                    hand = listOf(hidden),
                ),
            ),
            sleepingPieces = emptyList(),
            currentPlayerIndex = currentPlayerIndex,
            lastRoundWinnerIndex = null,
            openingPiece = sixSix,
            teamScores = listOf(marker % 6, 0),
            lastMove = null,
            roundWinnerPlayerIndex =
                if (roundSummary) 1 else null,
            roundWinnerTeamIndex =
                if (roundSummary) 1 else null,
            roundWinKind =
                if (roundSummary) "NORMAL" else null,
            gameWinnerTeamIndex = null,
            consecutivePassTurns = marker % 3,
            scoreMultiplier = 1,
            targetScore = 6,
        )
    }
}