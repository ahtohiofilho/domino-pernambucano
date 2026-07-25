package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchClassification
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateOnlineRoomNonCompetitiveInvariantTest {
    @Test
    fun code_room_flow_is_always_private_and_unranked() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val room = startFourHumanMatchThroughPrivateBoundary(store)
        val matchId = requireNotNull(room.matchId)
        val persistedState = store.snapshotPersistentState()
        val persistedRoom = persistedState.rooms.single()
        val persistedMatch = persistedState.matches.single()

        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            room.matchMode,
        )
        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            store.getMatchMode(matchId),
        )
        assertEquals(
            RankedMatchClassification.UNRANKED,
            store.getRankedMatchClassification(matchId),
        )
        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            persistedRoom.matchMode,
        )
        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            persistedMatch.matchMode,
        )
        assertEquals(
            RankedMatchClassification.UNRANKED,
            persistedMatch.classification,
        )
        assertTrue(persistedState.rankedResults.isEmpty())
        assertNull(store.getRankedMatchResult(matchId))
    }

    @Test
    fun private_join_boundary_rejects_server_managed_public_ranked_room() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val hostAccount = requireNotNull(
            store.promoteAccount(
                playerId = "public-player-1",
            ),
        )
        val publicRoom = requireNotNull(
            store.createPublicRankedRoom(
                request = CreateOnlineRoomRequestDto(
                    localPlayerId = "public-player-1",
                    playerName = "Jogador 1",
                ),
                identity = hostAccount.toRequestIdentity(),
            ).roomSnapshot,
        )

        val privateJoinResult = store.joinRoom(
            JoinOnlineRoomRequestDto(
                roomCode = publicRoom.roomCode,
                localPlayerId = "public-player-2",
                playerName = "Jogador 2",
            ),
        )

        assertFalse(privateJoinResult.accepted)
        assertEquals(
            "A sala não pertence a esta modalidade.",
            privateJoinResult.reason,
        )
        assertEquals(
            1,
            requireNotNull(
                store.getRoomSnapshot(publicRoom.roomId),
            ).players.size,
        )
    }

    @Test
    fun schema_five_rejects_private_room_with_ranked_classification() {
        val sourceStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        startFourHumanMatchThroughPrivateBoundary(sourceStore)
        val validState = sourceStore.snapshotPersistentState()
        val invalidMatch = validState.matches.single().copy(
            classification = RankedMatchClassification.RANKED,
        )
        val restoredStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            restoredStore.restorePersistentState(
                validState.copy(
                    matches = listOf(invalidMatch),
                ),
            )
        }
    }

    @Test
    fun legacy_ranked_room_is_demoted_to_private_unranked() {
        val sourceStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )
        val publicRoom = startFourHumanServerManagedMatch(
            store = sourceStore,
            matchMode = DominoMatchMode.PUBLIC_RANKED,
        )
        val matchId = requireNotNull(publicRoom.matchId)
        val legacyState = sourceStore
            .snapshotPersistentState()
            .copy(
                schemaVersion = 4,
            )
        val restoredStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        restoredStore.restorePersistentState(legacyState)

        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            requireNotNull(
                restoredStore.getRoomSnapshot(publicRoom.roomId),
            ).matchMode,
        )
        assertEquals(
            DominoMatchMode.PRIVATE_UNRANKED,
            restoredStore.getMatchMode(matchId),
        )
        assertEquals(
            RankedMatchClassification.UNRANKED,
            restoredStore.getRankedMatchClassification(matchId),
        )
        assertTrue(
            restoredStore
                .snapshotPersistentState()
                .rankedResults
                .isEmpty(),
        )
        assertNull(restoredStore.getRankedMatchResult(matchId))
    }

    private fun startFourHumanMatchThroughPrivateBoundary(
        store: InMemoryOnlineServerStore,
    ): OnlineRoomSnapshotDto {
        val room = requireNotNull(
            store.createRoom(
                CreateOnlineRoomRequestDto(
                    localPlayerId = "private-player-1",
                    playerName = "Jogador 1",
                ),
            ).roomSnapshot,
        )

        listOf(2, 3, 4).forEach { playerNumber ->
            val result = store.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = "private-player-$playerNumber",
                    playerName = "Jogador $playerNumber",
                ),
            )

            assertTrue(result.accepted)
        }

        return requireNotNull(store.getRoomSnapshot(room.roomId))
    }

    private fun startFourHumanServerManagedMatch(
        store: InMemoryOnlineServerStore,
        matchMode: DominoMatchMode,
    ): OnlineRoomSnapshotDto {
        require(matchMode == DominoMatchMode.PUBLIC_RANKED)

        val hostAccount = requireNotNull(
            store.promoteAccount(
                playerId = "managed-player-1",
            ),
        )
        val room = requireNotNull(
            store.createPublicRankedRoom(
                request = CreateOnlineRoomRequestDto(
                    localPlayerId = "managed-player-1",
                    playerName = "Jogador 1",
                ),
                identity = hostAccount.toRequestIdentity(),
            ).roomSnapshot,
        )

        listOf(2, 3, 4).forEach { playerNumber ->
            val playerId = "managed-player-$playerNumber"
            val account = requireNotNull(
                store.promoteAccount(
                    playerId = playerId,
                ),
            )
            val result = store.joinPublicRankedRoom(
                request = JoinOnlineRoomRequestDto(
                    roomCode = room.roomCode,
                    localPlayerId = playerId,
                    playerName = "Jogador $playerNumber",
                ),
                identity = account.toRequestIdentity(),
            )

            assertTrue(result.accepted)
        }

        return requireNotNull(store.getRoomSnapshot(room.roomId))
    }

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "ranked-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )
}
