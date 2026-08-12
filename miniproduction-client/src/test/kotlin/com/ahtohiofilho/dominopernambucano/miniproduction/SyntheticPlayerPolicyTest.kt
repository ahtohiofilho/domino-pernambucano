package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineBoardSideDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoBoardChainDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoGameStateDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoPieceDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchClockPolicyDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchPhaseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchPhaseTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionTypeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyntheticPlayerPolicyTest {
    private val policy = SyntheticPlayerPolicy(
        syntheticTableCodes = setOf("MCO", "RLI"),
    )

    @Test
    fun projected_own_hand_produces_a_canonical_legal_move() {
        val snapshot = snapshot(
            phase = OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE,
            currentPlayerIndex = 0,
        )

        val action = policy.chooseAction(
            snapshot = snapshot,
            localSeatIndex = 0,
            playerId = "player-0",
        )

        assertNotNull(action)
        assertEquals(OnlinePlayerActionTypeDto.PLAY_MOVE, action?.type)
        assertEquals(OnlineDominoPieceDto(6, 6), action?.move?.piece)
        assertEquals(OnlineBoardSideDto.RIGHT, action?.move?.side)
        assertEquals(snapshot.revision, action?.revision)
    }

    @Test
    fun a_client_never_plays_for_another_seat() {
        assertNull(
            policy.chooseAction(
                snapshot = snapshot(
                    phase = OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE,
                    currentPlayerIndex = 0,
                ),
                localSeatIndex = 1,
                playerId = "player-1",
            ),
        )
    }

    @Test
    fun lowest_synthetic_seat_advances_round_when_human_owns_seat_zero() {
        val snapshot = snapshot(
            phase = OnlineMatchPhaseTypeDto.ROUND_SUMMARY,
            currentPlayerIndex = 0,
        )

        val leaderAction = policy.chooseAction(
            snapshot = snapshot,
            localSeatIndex = 1,
            playerId = "player-1",
        )
        val followerAction = policy.chooseAction(
            snapshot = snapshot,
            localSeatIndex = 2,
            playerId = "player-2",
        )

        assertEquals(
            OnlinePlayerActionTypeDto.START_NEXT_ROUND,
            leaderAction?.type,
        )
        assertNull(followerAction)
    }

    private fun snapshot(
        phase: OnlineMatchPhaseTypeDto,
        currentPlayerIndex: Int,
    ): OnlineMatchSnapshotDto {
        val sixSix = OnlineDominoPieceDto(6, 6)
        val hidden = OnlineDominoPieceDto(-1, -1)
        return OnlineMatchSnapshotDto(
            roomId = "room-1",
            matchId = "match-1",
            revision = 7L,
            roundNumber = 1,
            gameState = OnlineDominoGameStateDto(
                board = emptyList(),
                boardChain = OnlineDominoBoardChainDto(),
                players = listOf(
                    OnlineDominoPlayerDto(0, "HUM", listOf(sixSix)),
                    OnlineDominoPlayerDto(1, "MCO", listOf(hidden)),
                    OnlineDominoPlayerDto(2, "RLI", listOf(hidden)),
                    OnlineDominoPlayerDto(3, "EXT", listOf(hidden)),
                ),
                sleepingPieces = emptyList(),
                currentPlayerIndex = currentPlayerIndex,
                lastRoundWinnerIndex = null,
                openingPiece = sixSix,
                teamScores = listOf(0, 0),
                lastMove = null,
                roundWinnerPlayerIndex = if (
                    phase == OnlineMatchPhaseTypeDto.ROUND_SUMMARY
                ) 0 else null,
                roundWinnerTeamIndex = if (
                    phase == OnlineMatchPhaseTypeDto.ROUND_SUMMARY
                ) 0 else null,
                roundWinKind = if (
                    phase == OnlineMatchPhaseTypeDto.ROUND_SUMMARY
                ) "NORMAL" else null,
                gameWinnerTeamIndex = null,
                consecutivePassTurns = 0,
                scoreMultiplier = 1,
                targetScore = 6,
            ),
            phase = OnlineMatchPhaseDto(type = phase),
            clockPolicy = OnlineMatchClockPolicyDto.ONLINE_PER_PLAYER_ROUND,
            playerClockMillis = listOf(20_000L, 20_000L, 20_000L, 20_000L),
            playerClockReserveMillis =
                listOf(20_000L, 20_000L, 20_000L, 20_000L),
        )
    }
}
