package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.domain.createNextRoundDominoGameState
import com.ahtohiofilho.dominopernambucano.domain.getPlayableMoves
import com.ahtohiofilho.dominopernambucano.domain.hasPlayablePiece
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import com.ahtohiofilho.dominopernambucano.domain.passTurn
import com.ahtohiofilho.dominopernambucano.domain.playMoveForCurrentPlayer
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.createInitialPlayerClockMillis
import com.ahtohiofilho.dominopernambucano.match.createInitialPlayerClockReserveMillis
import com.ahtohiofilho.dominopernambucano.match.reloadPlayerClockFromReserveMillis

sealed interface OnlineMatchActionReduction {
    data class Accepted(
        val runtimeState: DominoMatchRuntimeState,
    ) : OnlineMatchActionReduction

    data class Rejected(
        val reason: String,
        val revision: Long,
    ) : OnlineMatchActionReduction
}

fun reduceOnlineGameAction(
    action: OnlinePlayerActionDto,
    currentSnapshot: OnlineMatchSnapshotDto,
    seatIndex: Int,
): OnlineMatchActionReduction {
    if (action.revision != currentSnapshot.revision) {
        return rejectOnlineAction(
            reason = "Snapshot desatualizado.",
            currentSnapshot = currentSnapshot,
        )
    }

    val runtimeState = currentSnapshot.toRuntimeState(
        localPlayerIndex = seatIndex,
    )

    val gameState = runtimeState.gameState

    if (isGameFinished(gameState)) {
        return rejectOnlineAction(
            reason = "A partida já terminou.",
            currentSnapshot = currentSnapshot,
        )
    }

    if (isRoundFinished(gameState)) {
        return rejectOnlineAction(
            reason = "A rodada já terminou.",
            currentSnapshot = currentSnapshot,
        )
    }

    if (gameState.currentPlayerIndex != seatIndex) {
        return rejectOnlineAction(
            reason = "Não é a vez deste jogador.",
            currentSnapshot = currentSnapshot,
        )
    }

    val updatedGameState = when (action.type) {
        OnlinePlayerActionTypeDto.PLAY_MOVE -> {
            val move = action.move?.toDomain()
                ?: return rejectOnlineAction(
                    reason = "Jogada sem peça informada.",
                    currentSnapshot = currentSnapshot,
                )

            val validMoves = getPlayableMoves(
                board = gameState.board,
                piece = move.piece,
                openingPiece = gameState.openingPiece,
            )

            if (!validMoves.contains(move)) {
                return rejectOnlineAction(
                    reason = "Jogada inválida para o estado atual.",
                    currentSnapshot = currentSnapshot,
                )
            }

            playMoveForCurrentPlayer(
                state = gameState,
                playableMove = move,
            )
        }

        OnlinePlayerActionTypeDto.PASS_TURN -> {
            if (
                hasPlayablePiece(
                    state = gameState,
                    playerIndex = seatIndex,
                )
            ) {
                return rejectOnlineAction(
                    reason = "O jogador ainda possui peça jogável.",
                    currentSnapshot = currentSnapshot,
                )
            }

            passTurn(
                state = gameState,
            )
        }

        OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT,
        OnlinePlayerActionTypeDto.LEAVE_ROOM,
        OnlinePlayerActionTypeDto.START_NEXT_ROUND,
        OnlinePlayerActionTypeDto.START_NEW_MATCH -> {
            return rejectOnlineAction(
                reason = "Ação inválida para o fluxo da partida.",
                currentSnapshot = currentSnapshot,
            )
        }
    }

    val reloadedClock = reloadPlayerClockFromReserveMillis(
        clocks = runtimeState.playerClockMillis,
        reserves = runtimeState.playerClockReserveMillis,
        playerIndex = seatIndex,
        playerRoundTimeMillis = runtimeState.clockPolicy.playerRoundTimeMillis,
    )

    return OnlineMatchActionReduction.Accepted(
        runtimeState = runtimeState.copy(
            gameState = updatedGameState,
            phase = determineOnlineNextPhase(
                gameState = updatedGameState,
            ),
            playerClockMillis = reloadedClock.playerClockMillis,
            playerClockReserveMillis =
                reloadedClock.playerClockReserveMillis,
        ),
    )
}

fun reduceOnlineStartNextRoundAction(
    action: OnlinePlayerActionDto,
    currentRoom: OnlineRoomSnapshotDto,
    currentSnapshot: OnlineMatchSnapshotDto,
): OnlineMatchActionReduction {
    if (action.revision != currentSnapshot.revision) {
        return rejectOnlineAction(
            reason = "Snapshot desatualizado.",
            currentSnapshot = currentSnapshot,
        )
    }

    val runtimeState = currentSnapshot.toRuntimeState(
        localPlayerIndex = 0,
    )

    if (!isRoundFinished(runtimeState.gameState)) {
        return rejectOnlineAction(
            reason = "A rodada ainda não terminou.",
            currentSnapshot = currentSnapshot,
        )
    }

    if (isGameFinished(runtimeState.gameState)) {
        return rejectOnlineAction(
            reason = "A partida já terminou.",
            currentSnapshot = currentSnapshot,
        )
    }

    val nextRoundGameState = createNextRoundDominoGameState(
        previousState = runtimeState.gameState,
    )

    val namedGameState = applyOnlineRoomPlayerNames(
        gameState = nextRoundGameState,
        room = currentRoom,
    )

    return OnlineMatchActionReduction.Accepted(
        runtimeState = runtimeState.copy(
            gameState = namedGameState,
            roundNumber = runtimeState.roundNumber + 1,
            phase = determineOnlineNextPhase(
                gameState = namedGameState,
            ),
            playerClockMillis = createInitialPlayerClockMillis(
                playerCount = namedGameState.players.size,
                clockPolicy = runtimeState.clockPolicy,
            ),
            playerClockReserveMillis = createInitialPlayerClockReserveMillis(
                playerCount = namedGameState.players.size,
                clockPolicy = runtimeState.clockPolicy,
            ),
        ),
    )
}

fun reduceOnlineStartNewMatchAction(
    action: OnlinePlayerActionDto,
    currentRoom: OnlineRoomSnapshotDto,
    currentSnapshot: OnlineMatchSnapshotDto,
): OnlineMatchActionReduction {
    if (action.revision != currentSnapshot.revision) {
        return rejectOnlineAction(
            reason = "Snapshot desatualizado.",
            currentSnapshot = currentSnapshot,
        )
    }

    val runtimeState = currentSnapshot.toRuntimeState(
        localPlayerIndex = 0,
    )

    if (!isGameFinished(runtimeState.gameState)) {
        return rejectOnlineAction(
            reason = "A partida ainda não terminou.",
            currentSnapshot = currentSnapshot,
        )
    }

    val freshGameState = applyOnlineRoomPlayerNames(
        gameState = createInitialDominoGameState(),
        room = currentRoom,
    )

    return OnlineMatchActionReduction.Accepted(
        runtimeState = runtimeState.copy(
            gameState = freshGameState,
            roundNumber = 1,
            phase = determineOnlineNextPhase(
                gameState = freshGameState,
            ),
            playerClockMillis = createInitialPlayerClockMillis(
                playerCount = freshGameState.players.size,
                clockPolicy = runtimeState.clockPolicy,
            ),
            playerClockReserveMillis = createInitialPlayerClockReserveMillis(
                playerCount = freshGameState.players.size,
                clockPolicy = runtimeState.clockPolicy,
            ),
        ),
    )
}

fun determineOnlineNextPhase(
    gameState: DominoGameState,
): DominoMatchPhase {
    if (isGameFinished(gameState)) {
        return DominoMatchPhase.MatchFinished
    }

    if (isRoundFinished(gameState)) {
        return DominoMatchPhase.RoundSummary
    }

    val currentPlayerIndex = gameState.currentPlayerIndex

    if (
        !hasPlayablePiece(
            state = gameState,
            playerIndex = currentPlayerIndex,
        )
    ) {
        return DominoMatchPhase.PresentingPass(
            playerIndex = currentPlayerIndex,
        )
    }

    return DominoMatchPhase.WaitingForLocalMove
}

fun applyOnlineRoomPlayerNames(
    gameState: DominoGameState,
    room: OnlineRoomSnapshotDto,
): DominoGameState {
    val playersBySeat = room.players.associateBy { player ->
        player.seatIndex
    }

    val namedPlayers = gameState.players.mapIndexed { index, player ->
        val roomPlayer = playersBySeat[index]

        player.copy(
            name = roomPlayer?.name ?: player.name,
        )
    }

    return gameState.copy(
        players = namedPlayers,
    )
}

private fun rejectOnlineAction(
    reason: String,
    currentSnapshot: OnlineMatchSnapshotDto,
): OnlineMatchActionReduction.Rejected {
    return OnlineMatchActionReduction.Rejected(
        reason = reason,
        revision = currentSnapshot.revision,
    )
}