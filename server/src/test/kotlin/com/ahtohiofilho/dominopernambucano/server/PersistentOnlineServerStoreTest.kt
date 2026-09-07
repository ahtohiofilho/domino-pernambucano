package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.createOnlinePassTurnAction
import com.ahtohiofilho.dominopernambucano.online.createOnlinePlayMoveAction
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentOnlineServerStoreTest {
    @Test
    fun restart_restores_match_revision_history_and_action_idempotency() {
        val root = temporaryRoot()
        val stateFile = File(root, "authoritative-state.json")

        try {
            val firstStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 1_000L },
            )
            val room = startFourHumanMatch(firstStore)
            val matchId = requireNotNull(room.matchId)
            val initialSnapshot = requireNotNull(
                firstStore.getMatchSnapshot(matchId),
            )
            val action = legalAction(
                room = room,
                snapshot = initialSnapshot,
                actionId = "persisted-action-1",
            )
            val firstResult = firstStore.submitAction(action)
            val snapshotBeforeRestart = requireNotNull(
                firstStore.getMatchSnapshot(matchId),
            )

            assertTrue(firstResult.accepted)
            assertTrue(snapshotBeforeRestart.revision > initialSnapshot.revision)
            firstStore.close()

            val restartedStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 1_000L },
            )

            assertEquals(
                room,
                restartedStore.getRoomSnapshot(room.roomId),
            )
            assertEquals(
                snapshotBeforeRestart,
                restartedStore.getMatchSnapshot(matchId),
            )
            assertEquals(
                (1L..snapshotBeforeRestart.revision).toList(),
                requireNotNull(
                    restartedStore.getMatchSnapshotsAfter(
                        matchId = matchId,
                        afterRevision = 0L,
                    ),
                ).map { snapshot -> snapshot.revision },
            )

            val duplicateResult = restartedStore.submitAction(action)

            assertEquals(firstResult, duplicateResult)
            assertEquals(
                snapshotBeforeRestart,
                restartedStore.getMatchSnapshot(matchId),
            )

            room.players.forEach { player ->
                assertNotNull(
                    restartedStore.getMatchSnapshotForParticipant(
                        matchId = matchId,
                        playerId = player.playerId,
                    ),
                )
            }

            restartedStore.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun human_control_reclaim_is_persisted_across_restart() {
        val root = temporaryRoot()
        val stateFile = File(root, "authoritative-state.json")
        var nowEpochMillis = 1_000L

        try {
            val firstStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { nowEpochMillis },
            )
            val room = startFourHumanMatch(firstStore)
            val matchId = requireNotNull(room.matchId)
            val initialSnapshot = requireNotNull(
                firstStore.getMatchSnapshot(matchId),
            )
            val expiredSeatIndex =
                initialSnapshot.gameState.currentPlayerIndex
            val reconnectingPlayer = requireNotNull(
                room.players.firstOrNull { player ->
                    player.seatIndex == expiredSeatIndex
                },
            )

            releaseRoundIntroForTest(
                store = firstStore,
                roomId = room.roomId,
                matchId = matchId,
            )

            nowEpochMillis += 31_000L
            firstStore.advanceAuthoritativeTime()

            val automaticSnapshot = requireNotNull(
                firstStore.getMatchSnapshot(matchId),
            )

            assertTrue(
                expiredSeatIndex in
                        automaticSnapshot.automaticPlayerIndexes,
            )

            val reconnectResult = firstStore.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = reconnectingPlayer.playerId,
                    playerName = reconnectingPlayer.name,
                ),
            )

            assertTrue(reconnectResult.accepted)

            val reclaimedSnapshot = requireNotNull(
                firstStore.getMatchSnapshot(matchId),
            )

            assertTrue(
                expiredSeatIndex !in
                        reclaimedSnapshot.automaticPlayerIndexes,
            )

            firstStore.close()

            val restartedStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { nowEpochMillis },
            )

            assertEquals(
                reclaimedSnapshot,
                restartedStore.getMatchSnapshot(matchId),
            )
            assertTrue(
                expiredSeatIndex !in requireNotNull(
                    restartedStore.getMatchSnapshot(matchId),
                ).automaticPlayerIndexes,
            )

            restartedStore.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun state_file_has_an_exclusive_process_lock() {
        val root = temporaryRoot()
        val stateFile = File(root, "authoritative-state.json")

        try {
            val firstStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
            )

            try {
                assertThrows(IllegalStateException::class.java) {
                    PersistentOnlineServerStore.open(
                        stateFile = stateFile,
                    )
                }
            } finally {
                firstStore.close()
            }

            PersistentOnlineServerStore.open(
                stateFile = stateFile,
            ).close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corrupt_state_fails_closed_without_replacing_the_file() {
        val root = temporaryRoot()
        val stateFile = File(root, "authoritative-state.json")
        val corruptState = "{not-valid-json"

        try {
            root.mkdirs()
            stateFile.writeText(corruptState)

            assertThrows(IllegalStateException::class.java) {
                PersistentOnlineServerStore.open(
                    stateFile = stateFile,
                )
            }

            assertEquals(
                corruptState,
                stateFile.readText(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun homologation_uses_memory_and_rejects_a_configured_state_file() {
        val memoryStore = createDefaultOnlineServerStore(
            serverEnvironment = OnlineServerEnvironment.HOMOLOGATION,
            autoFillDevelopmentBotsAfterTwoHumanPlayers = false,
            traceLogger = OnlineTraceLogger(),
            readEnvironmentVariable = { null },
        )

        try {
            assertTrue(memoryStore is InMemoryOnlineServerStore)
        } finally {
            memoryStore.close()
        }

        assertThrows(IllegalStateException::class.java) {
            createDefaultOnlineServerStore(
                serverEnvironment = OnlineServerEnvironment.HOMOLOGATION,
                autoFillDevelopmentBotsAfterTwoHumanPlayers = false,
                traceLogger = OnlineTraceLogger(),
                readEnvironmentVariable = { variableName ->
                    if (
                        variableName ==
                        ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE
                    ) {
                        "shared-production-state.json"
                    } else {
                        null
                    }
                },
            )
        }
    }

    @Test
    fun production_requires_an_absolute_persistent_state_file() {
        val traceLogger = OnlineTraceLogger()

        assertThrows(IllegalStateException::class.java) {
            createDefaultOnlineServerStore(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                autoFillDevelopmentBotsAfterTwoHumanPlayers = false,
                traceLogger = traceLogger,
                readEnvironmentVariable = { null },
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            createDefaultOnlineServerStore(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                autoFillDevelopmentBotsAfterTwoHumanPlayers = false,
                traceLogger = traceLogger,
                readEnvironmentVariable = { variableName ->
                    if (
                        variableName ==
                        ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE
                    ) {
                        "relative-state.json"
                    } else {
                        null
                    }
                },
            )
        }
    }

    @Test
    fun configured_production_factory_returns_persistent_store() {
        val root = temporaryRoot()
        val stateFile = File(root, "authoritative-state.json")

        try {
            val store = createDefaultOnlineServerStore(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                autoFillDevelopmentBotsAfterTwoHumanPlayers = false,
                traceLogger = OnlineTraceLogger(),
                readEnvironmentVariable = { variableName ->
                    if (
                        variableName ==
                        ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE
                    ) {
                        stateFile.absolutePath
                    } else {
                        null
                    }
                },
            )

            assertTrue(store is PersistentOnlineServerStore)
            assertTrue(stateFile.isFile)
            store.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun miniproduction_requires_absolute_state_and_returns_persistent_store() {
        val traceLogger = OnlineTraceLogger()

        assertThrows(IllegalStateException::class.java) {
            createDefaultOnlineServerStore(
                serverEnvironment =
                    OnlineServerEnvironment.MINIPRODUCTION,
                autoFillDevelopmentBotsAfterTwoHumanPlayers = false,
                traceLogger = traceLogger,
                readEnvironmentVariable = { null },
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            createDefaultOnlineServerStore(
                serverEnvironment =
                    OnlineServerEnvironment.MINIPRODUCTION,
                autoFillDevelopmentBotsAfterTwoHumanPlayers = false,
                traceLogger = traceLogger,
                readEnvironmentVariable = { variableName ->
                    if (
                        variableName ==
                        ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE
                    ) {
                        "relative-state.json"
                    } else {
                        null
                    }
                },
            )
        }

        val root = temporaryRoot()
        val stateFile = File(root, "miniproduction-state.json")

        try {
            val store = createDefaultOnlineServerStore(
                serverEnvironment =
                    OnlineServerEnvironment.MINIPRODUCTION,
                autoFillDevelopmentBotsAfterTwoHumanPlayers = false,
                traceLogger = traceLogger,
                readEnvironmentVariable = { variableName ->
                    if (
                        variableName ==
                        ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE
                    ) {
                        stateFile.absolutePath
                    } else {
                        null
                    }
                },
            )

            assertTrue(store is PersistentOnlineServerStore)
            assertTrue(stateFile.isFile)
            store.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun persistence_failure_changes_readiness_until_a_write_succeeds() {
        val root = temporaryRoot()
        val stateFile = File(root, "authoritative-state.json")
        val persistence = SwitchableFailingStatePersistence(
            delegate = FileOnlineServerStatePersistence(
                stateFile = stateFile,
            ),
        )
        val store = PersistentOnlineServerStore.open(
            statePersistence = persistence,
            nowEpochMillis = { 1_000L },
        )

        try {
            assertEquals(
                OnlineServerStoreReadiness.READY,
                store.readiness(),
            )

            persistence.failWrites = true

            assertThrows(
                IllegalStateException::class.java,
            ) {
                store.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "player-failed",
                        playerName = "Falha",
                    ),
                )
            }

            assertEquals(
                OnlineServerStoreReadiness.UNAVAILABLE,
                store.readiness(),
            )
            assertNull(
                store.getRoomSnapshot("server-room-1"),
            )

            persistence.failWrites = false

            val recoveredResult = store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-recovered",
                    playerName = "Recuperado",
                ),
            )

            assertTrue(recoveredResult.accepted)
            assertEquals(
                OnlineServerStoreReadiness.READY,
                store.readiness(),
            )
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun expired_waiting_rooms_are_pruned_before_capacity_is_reused() {
        var nowEpochMillis = 1_000L
        val store = InMemoryOnlineServerStore(
            resourcePolicy = OnlineServerStoreResourcePolicy(
                maxRoomCount = 1,
                maxActionResultCount = 8,
                waitingRoomRetentionMillis = 1_000L,
                finalizedRoomRetentionMillis = 1_000L,
                pruneIntervalMillis = 1L,
            ),
            nowEpochMillis = { nowEpochMillis },
        )

        val firstRoom = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-first",
                    playerName = "Primeiro",
                ),
            ).roomSnapshot,
        )

        val capacityRejection = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-second",
                playerName = "Segundo",
            ),
        )

        assertFalse(capacityRejection.accepted)

        nowEpochMillis += 1_000L

        val replacement = store.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = "player-replacement",
                playerName = "Substituto",
            ),
        )

        assertTrue(replacement.accepted)
        assertNull(store.getRoomSnapshot(firstRoom.roomId))
    }

    private fun startFourHumanMatch(
        store: OnlineServerStore,
    ): OnlineRoomSnapshotDto {
        val waitingRoom = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        (2..4).forEach { playerNumber ->
            val result = store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = waitingRoom.roomCode,
                    localPlayerId = "player-$playerNumber",
                    playerName = "Jogador $playerNumber",
                ),
            )

            assertTrue(result.accepted)
        }

        return requireNotNull(
            store.startPrivateRoom(
                PrivateRoomStartRequestDto(
                    roomId = waitingRoom.roomId,
                    localPlayerId = "player-1",
                ),
            ).roomSnapshot,
        )
    }

    private class SwitchableFailingStatePersistence(
        private val delegate: OnlineServerStatePersistence,
    ) : OnlineServerStatePersistence by delegate {
        var failWrites = false

        override fun write(
            state: OnlineServerStoreState,
        ) {
            if (failWrites) {
                throw IllegalStateException("persistence failure")
            }

            delegate.write(state)
        }
    }

    private fun legalAction(
        room: OnlineRoomSnapshotDto,
        snapshot: OnlineMatchSnapshotDto,
        actionId: String,
    ): OnlinePlayerActionDto {
        val playerIndex = snapshot.gameState.currentPlayerIndex
        val playerId = requireNotNull(
            room.players.firstOrNull { player ->
                player.seatIndex == playerIndex
            },
        ).playerId
        val runtimeState = snapshot.toRuntimeState(
            localPlayerIndex = playerIndex,
        )
        val move = findBasicBotMove(runtimeState.gameState)

        return if (move != null) {
            createOnlinePlayMoveAction(
                roomId = room.roomId,
                matchId = snapshot.matchId,
                playerId = playerId,
                revision = snapshot.revision,
                move = move,
                actionId = actionId,
            )
        } else {
            createOnlinePassTurnAction(
                roomId = room.roomId,
                matchId = snapshot.matchId,
                playerId = playerId,
                revision = snapshot.revision,
                actionId = actionId,
            )
        }
    }

    private fun temporaryRoot(): File {
        return File(
            System.getProperty("java.io.tmpdir"),
            "persistent-online-store-test-${UUID.randomUUID()}",
        )
    }
}
