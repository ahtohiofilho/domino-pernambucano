package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.createOnlineSnapshotRequestAction
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineServerStateStoreTest {
    @Test
    fun json_state_store_round_trips_authoritative_state() {
        var now = 1_000L
        val originalStore = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val startedRoom = startFourHumanMatch(
            store = originalStore,
        )
        val matchId = requireNotNull(startedRoom.matchId)
        val snapshotBeforeRestart = requireNotNull(
            originalStore.getMatchSnapshot(
                matchId = matchId,
            ),
        )
        val action = createOnlineSnapshotRequestAction(
            roomId = startedRoom.roomId,
            matchId = matchId,
            playerId = "player-1",
            revision = snapshotBeforeRestart.revision,
            actionId = "survives-restart",
        )
        val actionResult = originalStore.submitAction(
            action = action,
        )
        val stateStore = JsonFileOnlineServerStateStore(
            stateDirectory = Files.createTempDirectory(
                "domino-server-state",
            ),
        )

        stateStore.save(
            originalStore.snapshotPersistentState(),
        )

        val recoveredState = (
                stateStore.load() as OnlineServerStateLoadResult.Recovered
                ).state
        val recoveredStore = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )

        recoveredStore.restorePersistentState(
            state = recoveredState,
        )

        assertEquals(
            startedRoom,
            recoveredStore.getRoomSnapshot(
                roomId = startedRoom.roomId,
            ),
        )
        assertEquals(
            snapshotBeforeRestart,
            recoveredStore.getMatchSnapshot(
                matchId = matchId,
            ),
        )
        assertEquals(
            actionResult,
            recoveredStore.submitAction(
                action = action,
            ),
        )

        now += 31_000L

        assertTrue(
            recoveredStore.advanceAuthoritativeTime(),
        )
        assertTrue(
            requireNotNull(
                recoveredStore.getMatchSnapshot(
                    matchId = matchId,
                ),
            ).revision > snapshotBeforeRestart.revision,
        )
    }

    @Test
    fun invalid_json_is_reported_explicitly() {
        val stateDirectory = Files.createTempDirectory(
            "domino-server-state-invalid",
        )
        val stateStore = JsonFileOnlineServerStateStore(
            stateDirectory = stateDirectory,
        )

        Files.writeString(
            stateDirectory.resolve(
                ONLINE_SERVER_STATE_FILE_NAME,
            ),
            "{not-json}",
        )

        val result = stateStore.load()

        assertTrue(result is OnlineServerStateLoadResult.Invalid)
    }

    @Test
    fun waiting_room_is_removed_after_operational_ttl() {
        var now = 1_000L
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
        )
        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        now += 24L * 60L * 60L * 1_000L

        assertTrue(
            store.clearExpiredRooms(),
        )
        assertNull(
            store.getRoomSnapshot(
                roomId = room.roomId,
            ),
        )
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
    ).let { room ->
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
        requireNotNull(
            joinPlayer(
                store = store,
                roomCode = room.roomCode,
                playerId = "player-4",
                playerName = "Jogador 4",
            ).roomSnapshot,
        )
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
