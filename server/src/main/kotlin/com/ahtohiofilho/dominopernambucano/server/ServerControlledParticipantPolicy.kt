package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto

/**
 * Identity and control are separate concerns.
 *
 * HUMAN and SYNTHETIC both submit participant-originated actions.
 * SYNTHETIC remains a distinct identity for ranking, presentation and audit,
 * but the server must not choose a move merely because that identity is
 * synthetic. APPLICATION remains server-controlled only for legacy
 * development compatibility.
 */
internal fun resolveServerControlledSeatIndexes(players: List<OnlineRoomPlayerDto>,
    legacyApplicationSeatIndexes: Set<Int> = emptySet(),
): Set<Int> {
    val resolved = legacyApplicationSeatIndexes.toMutableSet()

    players.forEach { player ->
        if (
            player.participantType ==
                OnlineParticipantTypeDto.APPLICATION
        ) {
            player.seatIndex?.let { seatIndex ->
                resolved += seatIndex
            }
        }
    }

    return resolved
}