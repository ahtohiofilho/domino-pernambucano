package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOnlineParticipantClassificationTest {
    @Test
    fun development_completion_marks_opaque_participants_as_application() =
        runBlocking {
            val repository = FakeOnlineRoomRepository(
                nowEpochMillis = { 1_000L },
            )

            val createdRoom = requireNotNull(
                repository.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = "human-host",
                        playerName = "Humano 1",
                    ),
                ).roomSnapshot,
            )

            repository.joinRoom(
                JoinOnlineRoomRequestDto(
                    roomCode = createdRoom.roomCode,
                    localPlayerId = "human-player-2",
                    playerName = "Humano 2",
                ),
            )

            val completion:
                OnlineDevelopmentParticipantCompletion =
                repository

            completion.addApplicationParticipant(
                OnlineDevelopmentParticipantRequest(
                    roomCode = createdRoom.roomCode,
                    playerId = "opaque-participant-alpha",
                    playerName = "Aplicativo 3",
                ),
            )

            val finalResult =
                completion.addApplicationParticipant(
                    OnlineDevelopmentParticipantRequest(
                        roomCode = createdRoom.roomCode,
                        playerId = "opaque-participant-beta",
                        playerName = "Aplicativo 4",
                    ),
                )

            assertTrue(finalResult.accepted)

            val room = requireNotNull(
                finalResult.roomSnapshot,
            )

            assertEquals(
                OnlineRoomStatusDto.IN_MATCH,
                room.status,
            )

            assertEquals(
                listOf(
                    OnlineParticipantTypeDto.HUMAN,
                    OnlineParticipantTypeDto.HUMAN,
                    OnlineParticipantTypeDto.APPLICATION,
                    OnlineParticipantTypeDto.APPLICATION,
                ),
                room.players
                    .sortedBy { player ->
                        player.seatIndex
                    }
                    .map { player ->
                        player.participantType
                    },
            )

            val match = requireNotNull(
                repository.matchSnapshot.value,
            )

            assertEquals(
                listOf(
                    OnlineParticipantTypeDto.HUMAN,
                    OnlineParticipantTypeDto.HUMAN,
                    OnlineParticipantTypeDto.APPLICATION,
                    OnlineParticipantTypeDto.APPLICATION,
                ),
                match.gameState.players.map { player ->
                    player.participantType
                },
            )

            assertTrue(
                match.automaticPlayerIndexes.isEmpty(),
            )
        }

    @Test
    fun application_with_opaque_id_is_selected_by_participant_type() {
        val room = createPolicyRoom(
            playerId = "opaque-id-without-prefix",
            participantType =
                OnlineParticipantTypeDto.APPLICATION,
        )

        val gameState = createInitialDominoGameState().copy(
            currentPlayerIndex = 2,
        )

        assertTrue(
            shouldAdvanceFakePlayerForSnapshotRequest(
                room = room,
                gameState = gameState,
            ),
        )
    }

    @Test
    fun human_with_fake_like_id_is_not_selected_as_application() {
        val room = createPolicyRoom(
            playerId = "fake-player-999",
            participantType =
                OnlineParticipantTypeDto.HUMAN,
        )

        val gameState = createInitialDominoGameState().copy(
            currentPlayerIndex = 2,
        )

        assertFalse(
            shouldAdvanceFakePlayerForSnapshotRequest(
                room = room,
                gameState = gameState,
            ),
        )
    }

    @Test
    fun real_backend_disables_client_side_participant_completion() {
        assertTrue(
            OnlineDebugOptions
                .FakeBackend
                .allowFakePlayerCompletion,
        )

        assertFalse(
            OnlineDebugOptions
                .RealBackend
                .allowFakePlayerCompletion,
        )
    }

    private fun createPolicyRoom(
        playerId: String,
        participantType: OnlineParticipantTypeDto,
    ): OnlineRoomSnapshotDto {
        return OnlineRoomSnapshotDto(
            roomId = "room-policy",
            roomCode = "0001",
            hostPlayerId = "human-host",
            status = OnlineRoomStatusDto.IN_MATCH,
            players = listOf(
                OnlineRoomPlayerDto(
                    playerId = "human-host",
                    name = "Humano 1",
                    seatIndex = 0,
                    connected = true,
                    participantType =
                        OnlineParticipantTypeDto.HUMAN,
                ),
                OnlineRoomPlayerDto(
                    playerId = "human-player-2",
                    name = "Humano 2",
                    seatIndex = 1,
                    connected = true,
                    participantType =
                        OnlineParticipantTypeDto.HUMAN,
                ),
                OnlineRoomPlayerDto(
                    playerId = playerId,
                    name = "Participante testado",
                    seatIndex = 2,
                    connected = true,
                    participantType = participantType,
                ),
                OnlineRoomPlayerDto(
                    playerId = "human-player-4",
                    name = "Humano 4",
                    seatIndex = 3,
                    connected = true,
                    participantType =
                        OnlineParticipantTypeDto.HUMAN,
                ),
            ),
            matchId = "match-policy",
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
    }
}
