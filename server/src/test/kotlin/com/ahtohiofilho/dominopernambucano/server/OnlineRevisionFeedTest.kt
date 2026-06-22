package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.createOnlinePassTurnAction
import com.ahtohiofilho.dominopernambucano.online.createOnlinePlayMoveAction
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRevisionFeedTest {
    @Test
    fun revision_feed_keeps_initial_and_action_snapshots_in_order() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val room = startFourHumanMatch(
            store = store,
        )
        val matchId = requireNotNull(room.matchId)
        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(matchId),
        )

        val result = submitLegalActionForCurrentPlayer(
            store = store,
            roomId = room.roomId,
            matchId = matchId,
            snapshot = initialSnapshot,
        )

        assertTrue(result.accepted)

        val history = requireNotNull(
            store.getMatchSnapshotsAfter(
                matchId = matchId,
                afterRevision = 0L,
            ),
        )

        assertEquals(
            (1L..requireNotNull(result.revision)).toList(),
            history.map { snapshot -> snapshot.revision },
        )
    }

    @Test
    fun authoritative_tick_resolves_presenting_pass_without_player_request() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val room = startFourHumanMatch(
            store = store,
        )
        val matchId = requireNotNull(room.matchId)

        repeat(80) {
            val snapshot = requireNotNull(
                store.getMatchSnapshot(matchId),
            )
            val runtimeState = snapshot.toRuntimeState(
                localPlayerIndex = snapshot.gameState.currentPlayerIndex,
            )

            if (runtimeState.phase is DominoMatchPhase.PresentingPass) {
                val playerBeforePass = snapshot.gameState.currentPlayerIndex

                store.advanceAuthoritativeTime()

                val afterTick = requireNotNull(
                    store.getMatchSnapshot(matchId),
                )

                assertTrue(afterTick.revision > snapshot.revision)
                assertTrue(
                    afterTick.gameState.currentPlayerIndex != playerBeforePass ||
                        afterTick.gameState.roundWinnerPlayerIndex != null,
                )
                return
            }

            val result = submitLegalActionForCurrentPlayer(
                store = store,
                roomId = room.roomId,
                matchId = matchId,
                snapshot = snapshot,
            )

            assertTrue(result.accepted)
        }

        error("NÃ£o foi encontrada uma passagem obrigatÃ³ria no limite de turnos do teste.")
    }

    private fun submitLegalActionForCurrentPlayer(
        store: InMemoryOnlineServerStore,
        roomId: String,
        matchId: String,
        snapshot: OnlineMatchSnapshotDto,
    ) = snapshot.toRuntimeState(
        localPlayerIndex = snapshot.gameState.currentPlayerIndex,
    ).let { runtimeState ->
        val playerIndex = runtimeState.gameState.currentPlayerIndex
        val playerId = "player-${playerIndex + 1}"
        val move = findBasicBotMove(runtimeState.gameState)

        val action = if (move != null) {
            createOnlinePlayMoveAction(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                revision = snapshot.revision,
                move = move,
            )
        } else {
            createOnlinePassTurnAction(
                roomId = roomId,
                matchId = matchId,
                playerId = playerId,
                revision = snapshot.revision,
            )
        }

        store.submitAction(action)
    }

    private fun startFourHumanMatch(
        store: InMemoryOnlineServerStore,
    ) = requireNotNull(
        store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "Jogador 1",
            ),
        ).roomSnapshot,
    ).let { waitingRoom ->
        (2..4).forEach { playerNumber ->
            store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = waitingRoom.roomCode,
                    localPlayerId = "player-$playerNumber",
                    playerName = "Jogador $playerNumber",
                ),
            )
        }

        requireNotNull(
            store.getRoomSnapshot(waitingRoom.roomId),
        )
    }
}
