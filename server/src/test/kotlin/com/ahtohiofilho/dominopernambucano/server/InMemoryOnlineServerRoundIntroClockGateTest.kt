package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.createOnlineSnapshotRequestAction
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOnlineServerRoundIntroClockGateTest {
    @Test
    fun current_player_ack_releases_intro_with_full_clock() {
        var now = 1_000L
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val room = startFourHumanMatch(store)
        val matchId = requireNotNull(room.matchId)
        val initial = requireNotNull(
            store.getMatchSnapshot(matchId),
        )
        val runtime =
            initial.toRuntimeState(localPlayerIndex = 0)
        val currentPlayerIndex =
            runtime.gameState.currentPlayerIndex
        val currentPlayerId = requireNotNull(
            room.players.firstOrNull { player ->
                player.seatIndex == currentPlayerIndex
            },
        ).playerId

        assertEquals(
            DominoMatchPhase.RoundIntro,
            runtime.phase,
        )

        now += 3_000L

        assertFalse(store.advanceAuthoritativeTime())

        val stillIntro =
            requireNotNull(store.getMatchSnapshot(matchId))
        assertEquals(
            DominoMatchPhase.RoundIntro,
            stillIntro.toRuntimeState(0).phase,
        )
        assertEquals(
            20_000L,
            stillIntro.playerClockMillis[currentPlayerIndex],
        )

        val release = store.submitAction(
            createOnlineSnapshotRequestAction(
                roomId = room.roomId,
                matchId = matchId,
                playerId = currentPlayerId,
                revision = stillIntro.revision,
                actionId = "round-intro-finished",
            ),
        )

        assertTrue(release.accepted)

        val released =
            requireNotNull(store.getMatchSnapshot(matchId))

        assertEquals(
            DominoMatchPhase.WaitingForLocalMove,
            released.toRuntimeState(0).phase,
        )
        assertEquals(
            20_000L,
            released.playerClockMillis[currentPlayerIndex],
        )
        assertEquals(now, released.serverEpochMillis)
    }

    @Test
    fun fallback_releases_old_client_without_spending_clock() {
        var now = 1_000L
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val room = startFourHumanMatch(store)
        val matchId = requireNotNull(room.matchId)
        val initial = requireNotNull(
            store.getMatchSnapshot(matchId),
        )
        val currentPlayerIndex =
            initial.toRuntimeState(0)
                .gameState.currentPlayerIndex

        now += DominoMatchTiming.RoundIntroServerFallbackMillis

        assertTrue(store.advanceAuthoritativeTime())

        val released =
            requireNotNull(store.getMatchSnapshot(matchId))

        assertEquals(
            DominoMatchPhase.WaitingForLocalMove,
            released.toRuntimeState(0).phase,
        )
        assertEquals(
            20_000L,
            released.playerClockMillis[currentPlayerIndex],
        )
        assertEquals(now, released.serverEpochMillis)
    }

    private fun startFourHumanMatch(
        store: InMemoryOnlineServerStore,
    ) = run {
        val created = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        (2..4).forEach { number ->
            assertTrue(
                store.joinRoom(
                    JoinOnlineRoomRequestDto(
                        roomCode = created.roomCode,
                        localPlayerId = "player-$number",
                        playerName = "Jogador $number",
                    ),
                ).accepted,
            )
        }

        val started = store.startPrivateRoom(
            PrivateRoomStartRequestDto(
                roomId = created.roomId,
                localPlayerId = "player-1",
            ),
        )

        assertTrue(started.accepted)
        requireNotNull(started.roomSnapshot)
    }

    @Test
    fun terminal_transition_is_not_wrapped_in_round_intro() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val room = startFourHumanMatch(store)
        val snapshot = requireNotNull(
            store.getMatchSnapshot(
                requireNotNull(room.matchId),
            ),
        )
        val runtime = snapshot.toRuntimeState(0)

        assertEquals(
            DominoMatchPhase.MatchFinished,
            applyServerRoundIntroAfterNextRound(
                runtime.copy(
                    phase = DominoMatchPhase.MatchFinished,
                ),
            ).phase,
        )
        assertEquals(
            DominoMatchPhase.RoundIntro,
            applyServerRoundIntroAfterNextRound(
                runtime.copy(
                    phase = DominoMatchPhase.WaitingForLocalMove,
                ),
            ).phase,
        )
    }
}