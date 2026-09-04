package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerControlledParticipantPolicyTest {
    @Test
    fun synthetic_and_application_are_server_controlled_but_humans_are_not() {
        val players = listOf(
            player(0, OnlineParticipantTypeDto.HUMAN),
            player(1, OnlineParticipantTypeDto.SYNTHETIC),
            player(2, OnlineParticipantTypeDto.APPLICATION),
            player(3, OnlineParticipantTypeDto.HUMAN),
        )

        assertEquals(
            setOf(1, 2),
            resolveServerControlledSeatIndexes(players),
        )
    }

    @Test
    fun persisted_legacy_application_seats_remain_server_controlled() {
        val players = listOf(
            player(0, OnlineParticipantTypeDto.HUMAN),
            player(1, OnlineParticipantTypeDto.SYNTHETIC),
            player(2, OnlineParticipantTypeDto.HUMAN),
            player(3, OnlineParticipantTypeDto.HUMAN),
        )

        assertEquals(
            setOf(1, 3),
            resolveServerControlledSeatIndexes(
                players = players,
                legacyApplicationSeatIndexes = setOf(3),
            ),
        )
    }

    private fun player(
        seatIndex: Int,
        participantType: OnlineParticipantTypeDto,
    ) = OnlineRoomPlayerDto(
        playerId = "player-$seatIndex",
        name = "P0$seatIndex",
        seatIndex = seatIndex,
        connected = true,
        participantType = participantType,
    )
}