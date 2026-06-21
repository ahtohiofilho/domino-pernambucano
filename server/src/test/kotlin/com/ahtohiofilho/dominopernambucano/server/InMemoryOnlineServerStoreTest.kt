package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.createOnlineSnapshotRequestAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOnlineServerStoreTest {
    @Test
    fun fourth_player_starts_authoritative_match() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val createRoomResult = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "Jogador 1",
            ),
        )

        assertTrue(createRoomResult.accepted)

        val room = requireNotNull(
            createRoomResult.roomSnapshot,
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-2",
            playerName = "Jogador 2",
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-3",
            playerName = "Jogador 3",
        )

        val fourthPlayerResult = joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-4",
            playerName = "Jogador 4",
        )

        assertTrue(fourthPlayerResult.accepted)

        val startedRoom = requireNotNull(
            fourthPlayerResult.roomSnapshot,
        )

        assertEquals(
            4,
            startedRoom.players.size,
        )

        assertNotNull(startedRoom.matchId)

        val matchSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = requireNotNull(startedRoom.matchId),
            ),
        )

        assertEquals(
            startedRoom.roomId,
            matchSnapshot.roomId,
        )

        assertEquals(
            1L,
            matchSnapshot.revision,
        )
    }

    @Test
    fun repeated_action_id_returns_cached_result_without_advancing_revision() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-2",
            playerName = "Jogador 2",
        )

        joinPlayer(
            store = store,
            roomCode = room.roomCode,
            playerId = "player-3",
            playerName = "Jogador 3",
        )

        val startedRoom = requireNotNull(
            joinPlayer(
                store = store,
                roomCode = room.roomCode,
                playerId = "player-4",
                playerName = "Jogador 4",
            ).roomSnapshot,
        )

        val matchId = requireNotNull(startedRoom.matchId)

        val initialSnapshot = requireNotNull(
            store.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        val action = createOnlineSnapshotRequestAction(
            roomId = startedRoom.roomId,
            matchId = matchId,
            playerId = "player-1",
            revision = initialSnapshot.revision,
            actionId = "repeated-action",
        )

        val firstResult = store.submitAction(
            action = action,
        )

        val repeatedResult = store.submitAction(
            action = action,
        )

        assertTrue(firstResult.accepted)
        assertEquals(firstResult, repeatedResult)
    }

    private fun joinPlayer(
        store: InMemoryOnlineServerStore,
        roomCode: String,
        playerId: String,
        playerName: String,
    ) = store.joinRoom(
        JoinOnlineRoomRequestDto(
            roomCode = roomCode,
            localPlayerId = playerId,
            playerName = playerName,
        ),
    )
}