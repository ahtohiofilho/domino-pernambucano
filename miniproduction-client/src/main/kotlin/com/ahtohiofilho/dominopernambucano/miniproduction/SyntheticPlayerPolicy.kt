package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchPhaseTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.createOnlinePassTurnAction
import com.ahtohiofilho.dominopernambucano.online.createOnlinePlayMoveAction
import com.ahtohiofilho.dominopernambucano.online.createOnlineStartNextRoundAction
import com.ahtohiofilho.dominopernambucano.online.toDomain

internal class SyntheticPlayerPolicy(
    private val syntheticTableCodes: Set<String>,
) {
    fun chooseAction(
        snapshot: OnlineMatchSnapshotDto,
        localSeatIndex: Int,
        playerId: String,
    ): OnlinePlayerActionDto? {
        return when (snapshot.phase.type) {
            OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE -> {
                chooseTurnAction(
                    snapshot = snapshot,
                    localSeatIndex = localSeatIndex,
                    playerId = playerId,
                )
            }

            OnlineMatchPhaseTypeDto.ROUND_SUMMARY -> {
                chooseRoundTransition(
                    snapshot = snapshot,
                    localSeatIndex = localSeatIndex,
                    playerId = playerId,
                )
            }

            OnlineMatchPhaseTypeDto.ROUND_INTRO,
            OnlineMatchPhaseTypeDto.PRESENTING_MOVE,
            OnlineMatchPhaseTypeDto.PRESENTING_PASS,
            OnlineMatchPhaseTypeDto.MATCH_FINISHED -> null
        }
    }

    private fun chooseTurnAction(
        snapshot: OnlineMatchSnapshotDto,
        localSeatIndex: Int,
        playerId: String,
    ): OnlinePlayerActionDto? {
        if (snapshot.gameState.currentPlayerIndex != localSeatIndex) {
            return null
        }

        val move = findBasicBotMove(
            state = snapshot.gameState.toDomain(),
        )

        return if (move != null) {
            createOnlinePlayMoveAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = playerId,
                revision = snapshot.revision,
                move = move,
            )
        } else {
            createOnlinePassTurnAction(
                roomId = snapshot.roomId,
                matchId = snapshot.matchId,
                playerId = playerId,
                revision = snapshot.revision,
            )
        }
    }

    private fun chooseRoundTransition(
        snapshot: OnlineMatchSnapshotDto,
        localSeatIndex: Int,
        playerId: String,
    ): OnlinePlayerActionDto? {
        val syntheticLeaderSeat = snapshot.gameState.players
            .mapIndexedNotNull { seatIndex, player ->
                seatIndex.takeIf { player.name in syntheticTableCodes }
            }
            .minOrNull()

        if (syntheticLeaderSeat != localSeatIndex) {
            return null
        }

        return createOnlineStartNextRoundAction(
            roomId = snapshot.roomId,
            matchId = snapshot.matchId,
            playerId = playerId,
            revision = snapshot.revision,
        )
    }
}
