package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto

/**
 * Identity and control are separate concerns.
 *
 * HUMAN requires human input.
 * SYNTHETIC keeps persistent identity but is controlled by the server in play.
 * APPLICATION remains server-controlled for legacy development compatibility.
 */
internal fun resolveServerControlledSeatIndexes(players: List<OnlineRoomPlayerDto>,
    legacyApplicationSeatIndexes: Set<Int> = emptySet(),
): Set<Int> {
    val resolved = legacyApplicationSeatIndexes.toMutableSet()

    players.forEach { player ->
        if (
            player.participantType ==
                OnlineParticipantTypeDto.SYNTHETIC ||
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