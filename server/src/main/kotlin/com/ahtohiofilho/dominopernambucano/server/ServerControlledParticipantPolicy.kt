package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.AutomaticDecisionCadencePolicy
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto

/**
 * Identity and control are separate concerns.
 *
 * HUMAN and SYNTHETIC both submit participant-originated actions.
 * SYNTHETIC remains a distinct identity for ranking, presentation and audit,
 * but the server must not choose a move merely because that identity is
 * synthetic. APPLICATION is server-controlled for private-room automatic
 * completion and for legacy development compatibility.
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
internal fun resolveServerControlledDecisionDelayMillis(
    player: OnlineRoomPlayerDto?,
    roundNumber: Int,
    localSeatIndex: Int,
    gameStateHash: Int,
): Long? {
    if (
        player?.participantType !=
            OnlineParticipantTypeDto.APPLICATION
    ) {
        return null
    }

    val identityKey = player.name
        .trim()
        .ifBlank { player.playerId }

    return AutomaticDecisionCadencePolicy
        .resolveDecisionDelayMillis(
            identityKey = identityKey,
            roundNumber = roundNumber,
            localSeatIndex = localSeatIndex,
            gameStateHash = gameStateHash,
        )
}

internal fun isServerControlledDecisionCoolingDown(
    player: OnlineRoomPlayerDto?,
    roundNumber: Int,
    localSeatIndex: Int,
    gameStateHash: Int,
    turnStartedAtEpochMillis: Long?,
    nowEpochMillis: Long,
): Boolean {
    val delayMillis =
        resolveServerControlledDecisionDelayMillis(
            player = player,
            roundNumber = roundNumber,
            localSeatIndex = localSeatIndex,
            gameStateHash = gameStateHash,
        ) ?: return false

    val startedAt = turnStartedAtEpochMillis
        ?: return false

    return (
        nowEpochMillis - startedAt
    ).coerceAtLeast(0L) < delayMillis
}
