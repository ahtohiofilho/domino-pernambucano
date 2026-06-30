package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnlineServerPersistenceControllerTest {
    @Test
    fun recovered_state_restores_room_before_accepting_new_mutations() {
        val sourceStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        val createdRoom = requireNotNull(
            sourceStore.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        val targetStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val stateStore = RecordingOnlineServerStateStore(
            loadResult = OnlineServerStateLoadResult.Recovered(
                sourceStore.snapshotPersistentState(),
            ),
        )
        val controller = OnlineServerPersistenceController(
            store = targetStore,
            stateStore = stateStore,
        )

        assertEquals(
            OnlineServerRecoveryStatus.Ready,
            controller.restoreAtStartup(),
        )
        assertEquals(
            createdRoom,
            targetStore.getRoomSnapshot(
                roomId = createdRoom.roomId,
            ),
        )
    }

    @Test
    fun invalid_persisted_state_blocks_mutations() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val controller = OnlineServerPersistenceController(
            store = store,
            stateStore = RecordingOnlineServerStateStore(
                loadResult = OnlineServerStateLoadResult.Invalid(
                    reason = "JSON inválido.",
                ),
            ),
        )

        val recoveryStatus = controller.restoreAtStartup()

        assertTrue(
            recoveryStatus is OnlineServerRecoveryStatus.Invalid,
        )

        try {
            controller.mutate {
                store.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "player-1",
                        playerName = "Jogador 1",
                    ),
                )
            }
            fail("A mutação deveria ser bloqueada.")
        } catch (_: IllegalStateException) {
            // Esperado: não inicia uma sala vazia silenciosamente.
        }

        assertNull(
            store.getRoomSnapshot(
                roomId = "server-room-1",
            ),
        )
    }

    @Test
    fun persistence_failure_rolls_back_mutation() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val controller = OnlineServerPersistenceController(
            store = store,
            stateStore = RecordingOnlineServerStateStore(
                failOnSave = true,
            ),
        )

        assertEquals(
            OnlineServerRecoveryStatus.Ready,
            controller.restoreAtStartup(),
        )

        try {
            controller.mutate {
                store.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "player-1",
                        playerName = "Jogador 1",
                    ),
                )
            }
            fail("A gravação deveria falhar.")
        } catch (_: IllegalStateException) {
            // Esperado: o estado em memória retorna ao snapshot anterior.
        }

        assertNull(
            store.getRoomSnapshot(
                roomId = "server-room-1",
            ),
        )
    }

    @Test
    fun recovery_reanchors_expired_turn_and_persists_new_revision() {
        var sourceNow = 1_000L
        val sourceStore = InMemoryOnlineServerStore(
            nowEpochMillis = { sourceNow },
        )

        val startedRoom = startFourHumanMatch(
            store = sourceStore,
        )
        val matchId = requireNotNull(startedRoom.matchId)
        val initialSnapshot = requireNotNull(
            sourceStore.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        sourceNow += 31_000L

        val targetStore = InMemoryOnlineServerStore(
            nowEpochMillis = { sourceNow },
        )
        val stateStore = RecordingOnlineServerStateStore(
            loadResult = OnlineServerStateLoadResult.Recovered(
                sourceStore.snapshotPersistentState(),
            ),
        )
        val controller = OnlineServerPersistenceController(
            store = targetStore,
            stateStore = stateStore,
        )

        assertEquals(
            OnlineServerRecoveryStatus.Ready,
            controller.restoreAtStartup(),
        )

        val recoveredSnapshot = requireNotNull(
            targetStore.getMatchSnapshot(
                matchId = matchId,
            ),
        )

        assertTrue(
            recoveredSnapshot.revision > initialSnapshot.revision,
        )
        assertTrue(
            initialSnapshot.gameState.currentPlayerIndex in
                    recoveredSnapshot.automaticPlayerIndexes,
        )
        assertTrue(
            stateStore.savedStates.isNotEmpty(),
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

    private class RecordingOnlineServerStateStore(
        private val loadResult: OnlineServerStateLoadResult =
            OnlineServerStateLoadResult.Empty,
        private val failOnSave: Boolean = false,
    ) : OnlineServerStateStore {
        val savedStates = mutableListOf<OnlineServerPersistentState>()

        override fun load(): OnlineServerStateLoadResult {
            return loadResult
        }

        override fun save(
            state: OnlineServerPersistentState,
        ) {
            if (failOnSave) {
                error("Falha de persistência simulada.")
            }

            savedStates += state
        }
    }
}
