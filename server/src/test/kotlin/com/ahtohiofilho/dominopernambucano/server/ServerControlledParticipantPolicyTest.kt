package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.AutomaticDecisionCadencePolicy
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerControlledParticipantPolicyTest {
    @Test
    fun only_application_is_server_controlled_by_identity() {
        val players = listOf(
            player(0, OnlineParticipantTypeDto.HUMAN),
            player(1, OnlineParticipantTypeDto.SYNTHETIC),
            player(2, OnlineParticipantTypeDto.APPLICATION),
            player(3, OnlineParticipantTypeDto.HUMAN),
        )

        assertEquals(
            setOf(2),
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
            setOf(3),
            resolveServerControlledSeatIndexes(
                players = players,
                legacyApplicationSeatIndexes = setOf(3),
            ),
        )
    }

    @Test
    fun private_application_uses_shared_variable_decision_cadence() {
        val application =
            player(
                seatIndex = 2,
                participantType =
                    OnlineParticipantTypeDto.APPLICATION,
            )
        val roundNumber = 3
        val gameStateHash = 123_456

        val delayMillis = requireNotNull(
            resolveServerControlledDecisionDelayMillis(
                player = application,
                roundNumber = roundNumber,
                localSeatIndex = 2,
                gameStateHash = gameStateHash,
            ),
        )

        assertEquals(
            AutomaticDecisionCadencePolicy
                .resolveDecisionDelayMillis(
                    identityKey = application.name,
                    roundNumber = roundNumber,
                    localSeatIndex = 2,
                    gameStateHash = gameStateHash,
                ),
            delayMillis,
        )
        assertTrue(
            delayMillis in
                AutomaticDecisionCadencePolicy.MinDecisionDelayMillis..
                    AutomaticDecisionCadencePolicy.MaxDecisionDelayMillis,
        )

        val turnStartedAt = 10_000L

        assertTrue(
            isServerControlledDecisionCoolingDown(
                player = application,
                roundNumber = roundNumber,
                localSeatIndex = 2,
                gameStateHash = gameStateHash,
                turnStartedAtEpochMillis = turnStartedAt,
                nowEpochMillis =
                    turnStartedAt + delayMillis - 1L,
            ),
        )
        assertFalse(
            isServerControlledDecisionCoolingDown(
                player = application,
                roundNumber = roundNumber,
                localSeatIndex = 2,
                gameStateHash = gameStateHash,
                turnStartedAtEpochMillis = turnStartedAt,
                nowEpochMillis =
                    turnStartedAt + delayMillis,
            ),
        )

        assertEquals(
            null,
            resolveServerControlledDecisionDelayMillis(
                player =
                    player(
                        seatIndex = 1,
                        participantType =
                            OnlineParticipantTypeDto.SYNTHETIC,
                    ),
                roundNumber = roundNumber,
                localSeatIndex = 1,
                gameStateHash = gameStateHash,
            ),
        )
        assertEquals(
            null,
            resolveServerControlledDecisionDelayMillis(
                player =
                    player(
                        seatIndex = 0,
                        participantType =
                            OnlineParticipantTypeDto.HUMAN,
                    ),
                roundNumber = roundNumber,
                localSeatIndex = 0,
                gameStateHash = gameStateHash,
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