package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomCompleteRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomRemoveAutomaticPlayerRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateRoomAutomaticCompletionAuthorityTest {
    @Test
    fun host_completes_only_empty_seats_with_automatic_players() {
        val store = store()
        val room = createRoom(store)

        val completed = store.completePrivateRoom(
            PrivateRoomCompleteRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
            ),
        )

        assertTrue(completed.accepted)
        val snapshot = requireNotNull(completed.roomSnapshot)

        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            snapshot.matchMode,
        )
        assertEquals(
            OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            snapshot.status,
        )
        assertNull(snapshot.matchId)
        assertEquals(4, snapshot.players.size)
        assertEquals(
            listOf(0, 1, 2, 3),
            snapshot.players.mapNotNull { it.seatIndex }.sorted(),
        )
        assertEquals(
            1,
            snapshot.players.count {
                it.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            },
        )
        assertEquals(
            3,
            snapshot.players.count {
                it.participantType ==
                    OnlineParticipantTypeDto.APPLICATION
            },
        )
        assertEquals(
            snapshot.players.size,
            snapshot.players.map { it.playerId }.distinct().size,
        )
        assertEquals(
            snapshot.players.size,
            snapshot.players.map { it.name }.distinct().size,
        )
    }

    @Test
    fun non_host_cannot_complete_table() {
        val store = store()
        val room = createRoom(store)

        assertTrue(
            store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "guest",
                    playerName = "GST",
                ),
            ).accepted,
        )

        val before = requireNotNull(
            store.getRoomSnapshot(room.roomId),
        )

        val denied = store.completePrivateRoom(
            PrivateRoomCompleteRequestDto(
                roomId = room.roomId,
                localPlayerId = "guest",
            ),
        )

        assertFalse(denied.accepted)
        assertEquals(
            before,
            store.getRoomSnapshot(room.roomId),
        )
    }

    @Test
    fun incoming_human_replaces_an_automatic_player_before_start() {
        val store = store()
        val room = createRoom(store)

        val completed = requireNotNull(
            store.completePrivateRoom(
                PrivateRoomCompleteRequestDto(
                    roomId = room.roomId,
                    localPlayerId = "host",
                ),
            ).roomSnapshot,
        )

        val replaceable = requireNotNull(
            completed.players
                .filter {
                    it.participantType ==
                        OnlineParticipantTypeDto.APPLICATION
                }
                .minByOrNull {
                    it.seatIndex ?: Int.MAX_VALUE
                },
        )

        val joined = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = room.roomCode,
                localPlayerId = "human-2",
                playerName = "H02",
            ),
        )

        assertTrue(joined.accepted)
        val snapshot = requireNotNull(joined.roomSnapshot)

        assertEquals(
            OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
            snapshot.status,
        )
        assertNull(snapshot.matchId)
        assertEquals(4, snapshot.players.size)
        assertFalse(
            snapshot.players.any {
                it.playerId == replaceable.playerId
            },
        )
        assertEquals(
            replaceable.seatIndex,
            snapshot.players.single {
                it.playerId == "human-2"
            }.seatIndex,
        )
        assertEquals(
            2,
            snapshot.players.count {
                it.participantType ==
                    OnlineParticipantTypeDto.HUMAN
            },
        )
        assertEquals(
            2,
            snapshot.players.count {
                it.participantType ==
                    OnlineParticipantTypeDto.APPLICATION
            },
        )
    }

    @Test
    fun host_can_release_automatic_seat_and_complete_it_again() {
        val store = store()
        val room = createRoom(store)

        val completed = requireNotNull(
            store.completePrivateRoom(
                PrivateRoomCompleteRequestDto(
                    roomId = room.roomId,
                    localPlayerId = "host",
                ),
            ).roomSnapshot,
        )

        val automaticSeat = requireNotNull(
            completed.players.first {
                it.participantType ==
                    OnlineParticipantTypeDto.APPLICATION
            }.seatIndex,
        )

        val released = store.removePrivateRoomAutomaticPlayer(
            PrivateRoomRemoveAutomaticPlayerRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
                targetSeatIndex = automaticSeat,
            ),
        )

        assertTrue(released.accepted)
        assertEquals(
            3,
            requireNotNull(released.roomSnapshot).players.size,
        )

        val completedAgain = store.completePrivateRoom(
            PrivateRoomCompleteRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
            ),
        )

        assertTrue(completedAgain.accepted)
        assertEquals(
            4,
            requireNotNull(completedAgain.roomSnapshot).players.size,
        )
    }

    @Test
    fun completed_private_table_can_start_but_remains_unranked() {
        val store = store()
        val room = createRoom(store)

        assertTrue(
            store.completePrivateRoom(
                PrivateRoomCompleteRequestDto(
                    roomId = room.roomId,
                    localPlayerId = "host",
                ),
            ).accepted,
        )

        val started = store.startPrivateRoom(
            PrivateRoomStartRequestDto(
                roomId = room.roomId,
                localPlayerId = "host",
            ),
        )

        assertTrue(started.accepted)
        val snapshot = requireNotNull(started.roomSnapshot)

        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            snapshot.matchMode,
        )
        assertEquals(
            OnlineRoomStatusDto.IN_MATCH,
            snapshot.status,
        )
        assertNotNull(snapshot.matchId)
        assertEquals(
            3,
            snapshot.players.count {
                it.participantType ==
                    OnlineParticipantTypeDto.APPLICATION
            },
        )
        assertNull(
            store.getRankedMatchResult(
                requireNotNull(snapshot.matchId),
            ),
        )
    }

    private fun store() = InMemoryOnlineServerStore(
        nowEpochMillis = { 1_000L },
        privateRoomCompletionEntropy = ZeroEntropy(),
    )

    private fun createRoom(
        store: InMemoryOnlineServerStore,
    ) = requireNotNull(
        store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "host",
                playerName = "HST",
            ),
        ).roomSnapshot,
    )

    private class ZeroEntropy : PublicRankedFormationEntropy {
        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            return 0
        }

        override fun nextNonceHex(byteCount: Int): String {
            require(byteCount > 0)
            return "ab".repeat(byteCount)
        }
    }
}