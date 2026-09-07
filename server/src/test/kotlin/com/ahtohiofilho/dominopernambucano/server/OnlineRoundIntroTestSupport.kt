package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.createOnlineSnapshotRequestAction
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState

internal fun releaseRoundIntroForTest(
    store: OnlineServerStore,
    roomId: String,
    matchId: String,
): OnlineMatchSnapshotDto {
    val snapshot = requireNotNull(
        store.getMatchSnapshot(matchId),
    )
    val runtimeState = snapshot.toRuntimeState(
        localPlayerIndex = snapshot.gameState.currentPlayerIndex,
    )

    if (runtimeState.phase != DominoMatchPhase.RoundIntro) {
        return snapshot
    }

    val room = requireNotNull(
        store.getRoomSnapshot(roomId),
    )
    val currentSeatIndex =
        runtimeState.gameState.currentPlayerIndex
    val currentParticipant =
        room.players.firstOrNull { player ->
            player.seatIndex == currentSeatIndex
        }

    val authority =
        if (
            currentParticipant?.participantType ==
                OnlineParticipantTypeDto.HUMAN
        ) {
            currentParticipant
        } else {
            room.players
                .filter { player ->
                    player.participantType ==
                        OnlineParticipantTypeDto.HUMAN
                }
                .minByOrNull { player ->
                    requireNotNull(player.seatIndex)
                }
        }

    val authorityPlayer = requireNotNull(authority) {
        "RoundIntro de teste sem participante humano apto a liberar a apresentaÃ§Ã£o."
    }

    val result = store.submitAction(
        createOnlineSnapshotRequestAction(
            roomId = roomId,
            matchId = matchId,
            playerId = authorityPlayer.playerId,
            revision = snapshot.revision,
            actionId =
                "test-round-intro-release-${snapshot.revision}",
        ),
    )

    require(result.accepted) {
        "Falha ao liberar RoundIntro no teste: ${result.reason}"
    }

    val released = requireNotNull(
        store.getMatchSnapshot(matchId),
    )

    require(
        released.toRuntimeState(
            localPlayerIndex =
                released.gameState.currentPlayerIndex,
        ).phase != DominoMatchPhase.RoundIntro,
    ) {
        "RoundIntro permaneceu ativo apÃ³s ack autoritativo de teste."
    }

    return released
}