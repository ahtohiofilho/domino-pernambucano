package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicRankedQueueRemoteContractTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun ranked_queue_route_is_shared_and_stable() {
        assertEquals("ranked-queue", OnlineRemoteRoutes.RANKED_QUEUE)
    }

    @Test
    fun enter_request_preserves_homologated_json() {
        assertEquals(
            "{\"playerName\":\"Jogador 1\"}",
            json.encodeToString(
                PublicRankedQueueEnterRequestDto(
                    playerName = "Jogador 1",
                ),
            ),
        )
    }

    @Test
    fun waiting_response_preserves_homologated_json() {
        assertEquals(
            "{\"status\":\"WAITING\",\"queuePosition\":2,\"matchId\":null,\"localSeatIndex\":null}",
            json.encodeToString(
                PublicRankedQueueHttpResponseDto(
                    status = PublicRankedQueueHttpStatus.WAITING,
                    queuePosition = 2,
                ),
            ),
        )
    }

    @Test
    fun waiting_response_can_add_neutral_lobby_codes() {
        assertEquals(
            "{\"status\":\"WAITING\",\"queuePosition\":2,\"participantCodes\":[\"AAA\",\"ZZZ\"],\"matchId\":null,\"localSeatIndex\":null}",
            json.encodeToString(
                PublicRankedQueueHttpResponseDto(
                    status = PublicRankedQueueHttpStatus.WAITING,
                    queuePosition = 2,
                    participantCodes = listOf("AAA", "ZZZ"),
                ),
            ),
        )
    }

    @Test
    fun matched_response_preserves_homologated_json() {
        assertEquals(
            "{\"status\":\"MATCHED\",\"queuePosition\":null,\"matchId\":\"match-1\",\"localSeatIndex\":3}",
            json.encodeToString(
                PublicRankedQueueHttpResponseDto(
                    status = PublicRankedQueueHttpStatus.MATCHED,
                    matchId = "match-1",
                    localSeatIndex = 3,
                ),
            ),
        )
    }

    @Test
    fun unknown_identity_and_selection_fields_are_ignored() {
        val request = json.decodeFromString<PublicRankedQueueEnterRequestDto>(
            """
                {
                  "playerName": "Jogador 1",
                  "playerId": "forged-player",
                  "accountId": "forged-account",
                  "roomCode": "9999",
                  "roomId": "forged-room",
                  "matchMode": "PRIVATE_UNRANKED",
                  "seatIndex": 3
                }
            """.trimIndent(),
        )
        assertEquals("Jogador 1", request.playerName)
    }

    @Test
    fun sparse_server_responses_remain_compatible() {
        val waiting = json.decodeFromString<PublicRankedQueueHttpResponseDto>(
            "{\"status\":\"WAITING\",\"queuePosition\":4}",
        )
        val matched = json.decodeFromString<PublicRankedQueueHttpResponseDto>(
            "{\"status\":\"MATCHED\",\"matchId\":\"match-2\",\"localSeatIndex\":1}",
        )
        assertEquals(PublicRankedQueueHttpStatus.WAITING, waiting.status)
        assertEquals(4, waiting.queuePosition)
        assertNull(waiting.matchId)
        assertNull(waiting.localSeatIndex)
        assertEquals(PublicRankedQueueHttpStatus.MATCHED, matched.status)
        assertEquals("match-2", matched.matchId)
        assertEquals(1, matched.localSeatIndex)
        assertNull(matched.queuePosition)
    }
}
