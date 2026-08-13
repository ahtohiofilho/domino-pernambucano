package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineParticipantControlSeparationTest {
    @Test
    fun automatic_player_indexes_are_independent_from_permanent_participant_type() {
        val room = createRoomSnapshot()

        val gameState = applyOnlineRoomPlayerNames(
            gameState = createInitialDominoGameState(),
            room = room,
        )

        val snapshot = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.WaitingForLocalMove,
            clockPolicy =
                DominoMatchClockPolicy.OnlinePerPlayerRound,
        ).toOnlineSnapshotDto(
            roomId = room.roomId,
            matchId = requireNotNull(room.matchId),
            revision = 1L,
            automaticPlayerIndexes = listOf(0),
        )

        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            snapshot.gameState.players[0].participantType,
        )

        assertEquals(
            OnlineParticipantTypeDto.APPLICATION,
            snapshot.gameState.players[2].participantType,
        )

        assertEquals(
            listOf(0),
            snapshot.automaticPlayerIndexes,
        )

        assertTrue(
            2 !in snapshot.automaticPlayerIndexes,
        )

        val restoredRuntimeState = snapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        assertEquals(
            DominoParticipantType.HUMAN,
            restoredRuntimeState
                .gameState
                .players[0]
                .participantType,
        )

        assertEquals(
            DominoParticipantType.APPLICATION,
            restoredRuntimeState
                .gameState
                .players[2]
                .participantType,
        )
    }

    @Test
    fun next_round_clears_temporary_automation_without_changing_participant_types() {
        val room = createRoomSnapshot()

        val finishedGameState = applyOnlineRoomPlayerNames(
            gameState = createInitialDominoGameState(),
            room = room,
        ).copy(
            currentPlayerIndex = 0,
            lastRoundWinnerIndex = 0,
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.COMMON,
            gameWinnerTeamIndex = null,
        )

        val currentSnapshot = DominoMatchRuntimeState(
            gameState = finishedGameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.RoundSummary,
            clockPolicy =
                DominoMatchClockPolicy.OnlinePerPlayerRound,
        ).toOnlineSnapshotDto(
            roomId = room.roomId,
            matchId = requireNotNull(room.matchId),
            revision = 7L,
            automaticPlayerIndexes = listOf(0),
        )

        assertEquals(
            listOf(0),
            currentSnapshot.automaticPlayerIndexes,
        )

        val reduction = reduceOnlineStartNextRoundAction(
            action = createOnlineStartNextRoundAction(
                roomId = room.roomId,
                matchId = requireNotNull(room.matchId),
                playerId = "human-player-0",
                revision = currentSnapshot.revision,
                actionId = "start-next-round",
            ),
            currentRoom = room,
            currentSnapshot = currentSnapshot,
        )

        assertTrue(
            reduction is OnlineMatchActionReduction.Accepted,
        )

        val acceptedReduction =
            reduction as OnlineMatchActionReduction.Accepted

        val nextSnapshot =
            acceptedReduction.runtimeState.toOnlineSnapshotDto(
                roomId = room.roomId,
                matchId = requireNotNull(room.matchId),
                revision = currentSnapshot.revision + 1L,
                automaticPlayerIndexes = emptyList(),
            )

        assertEquals(
            2,
            nextSnapshot.roundNumber,
        )

        assertTrue(
            nextSnapshot.automaticPlayerIndexes.isEmpty(),
        )

        assertEquals(
            listOf(
                OnlineParticipantTypeDto.HUMAN,
                OnlineParticipantTypeDto.HUMAN,
                OnlineParticipantTypeDto.APPLICATION,
                OnlineParticipantTypeDto.APPLICATION,
            ),
            nextSnapshot.gameState.players.map { player ->
                player.participantType
            },
        )
    }

    @Test
    fun winning_round_summary_advances_to_match_finished_without_dealing_again() {
        val room = createRoomSnapshot()
        val finishedGameState = applyOnlineRoomPlayerNames(
            gameState = createInitialDominoGameState(),
            room = room,
        ).copy(
            currentPlayerIndex = 0,
            lastRoundWinnerIndex = 0,
            teamScores = listOf(6, 3),
            roundWinnerPlayerIndex = 0,
            roundWinnerTeamIndex = 0,
            roundWinKind = RoundWinKind.COMMON,
            gameWinnerTeamIndex = 0,
        )
        val currentSnapshot = DominoMatchRuntimeState(
            gameState = finishedGameState,
            roundNumber = 4,
            localPlayerIndex = 0,
            phase = DominoMatchPhase.RoundSummary,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
        ).toOnlineSnapshotDto(
            roomId = room.roomId,
            matchId = requireNotNull(room.matchId),
            revision = 11L,
        )

        val reduction = reduceOnlineStartNextRoundAction(
            action = createOnlineStartNextRoundAction(
                roomId = room.roomId,
                matchId = requireNotNull(room.matchId),
                playerId = "human-player-0",
                revision = currentSnapshot.revision,
                actionId = "finish-after-winning-summary",
            ),
            currentRoom = room,
            currentSnapshot = currentSnapshot,
        )

        assertTrue(reduction is OnlineMatchActionReduction.Accepted)
        val finalState =
            (reduction as OnlineMatchActionReduction.Accepted).runtimeState

        assertEquals(DominoMatchPhase.MatchFinished, finalState.phase)
        assertEquals(4, finalState.roundNumber)
        assertEquals(finishedGameState, finalState.gameState)
    }

    private fun createRoomSnapshot(): OnlineRoomSnapshotDto {
        return OnlineRoomSnapshotDto(
            roomId = "room-1",
            roomCode = "0001",
            hostPlayerId = "human-player-0",
            status = OnlineRoomStatusDto.IN_MATCH,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = "human-player-0",
                    name = "Humano 1",
                    seatIndex = 0,
                    connected = true,
                    participantType =
                        OnlineParticipantTypeDto.HUMAN,
                ),
                OnlineRoomPlayerDto(
                    playerId = "human-player-1",
                    name = "Humano 2",
                    seatIndex = 1,
                    connected = true,
                    participantType =
                        OnlineParticipantTypeDto.HUMAN,
                ),
                OnlineRoomPlayerDto(
                    playerId = "application-player-2",
                    name = "Bot 3",
                    seatIndex = 2,
                    connected = true,
                    participantType =
                        OnlineParticipantTypeDto.APPLICATION,
                ),
                OnlineRoomPlayerDto(
                    playerId = "application-player-3",
                    name = "Bot 4",
                    seatIndex = 3,
                    connected = true,
                    participantType =
                        OnlineParticipantTypeDto.APPLICATION,
                ),
            ),
            matchId = "match-1",
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
    }
}
