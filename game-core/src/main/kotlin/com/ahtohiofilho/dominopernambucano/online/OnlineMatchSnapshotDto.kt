package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import kotlinx.serialization.Serializable

@Serializable
enum class OnlineMatchClockPolicyDto {
    DISABLED,
    ONLINE_PER_PLAYER_ROUND,
}

@Serializable
data class OnlineMatchSnapshotDto(
    val roomId: String,
    val matchId: String,
    val revision: Long,

    val roundNumber: Int,
    val gameState: OnlineDominoGameStateDto,
    val phase: OnlineMatchPhaseDto,

    val clockPolicy: OnlineMatchClockPolicyDto,
    val playerClockMillis: List<Long>,
    val playerClockReserveMillis: List<Long> = emptyList(),

    val serverEpochMillis: Long? = null,

    /*
     * Assentos colocados temporariamente em modo automático por estouro de
     * tempo na rodada atual.
     *
     * Esse estado viaja no snapshot porque o client/coordinator não pode
     * depender apenas do relógio local. Ele é independente de participantType:
     * a natureza permanente HUMAN/APPLICATION continua em gameState.players.
     * Esta lista é limpa ao iniciar uma nova rodada ou uma nova partida.
     */
    val automaticPlayerIndexes: List<Int> = emptyList(),
)

fun DominoMatchClockPolicy.toOnlineDto(): OnlineMatchClockPolicyDto {
    return when (this) {
        DominoMatchClockPolicy.Disabled -> OnlineMatchClockPolicyDto.DISABLED
        DominoMatchClockPolicy.OnlinePerPlayerRound -> {
            OnlineMatchClockPolicyDto.ONLINE_PER_PLAYER_ROUND
        }
    }
}

fun OnlineMatchClockPolicyDto.toDomain(): DominoMatchClockPolicy {
    return when (this) {
        OnlineMatchClockPolicyDto.DISABLED -> DominoMatchClockPolicy.Disabled
        OnlineMatchClockPolicyDto.ONLINE_PER_PLAYER_ROUND -> {
            DominoMatchClockPolicy.OnlinePerPlayerRound
        }
    }
}

fun DominoMatchRuntimeState.toOnlineSnapshotDto(
    roomId: String,
    matchId: String,
    revision: Long,
    serverEpochMillis: Long? = null,
    automaticPlayerIndexes: List<Int> = emptyList(),
): OnlineMatchSnapshotDto {
    return OnlineMatchSnapshotDto(
        roomId = roomId,
        matchId = matchId,
        revision = revision,
        roundNumber = roundNumber,
        gameState = gameState.toOnlineDto(),
        phase = phase.toOnlineDto(),
        clockPolicy = clockPolicy.toOnlineDto(),
        playerClockMillis = playerClockMillis,
        playerClockReserveMillis = playerClockReserveMillis,
        serverEpochMillis = serverEpochMillis,
        automaticPlayerIndexes = automaticPlayerIndexes,
    )
}

fun OnlineMatchSnapshotDto.toRuntimeState(
    localPlayerIndex: Int,
): DominoMatchRuntimeState {
    return DominoMatchRuntimeState(
        gameState = gameState.toDomain(),
        roundNumber = roundNumber,
        localPlayerIndex = localPlayerIndex,
        phase = phase.toDomain(),
        clockPolicy = clockPolicy.toDomain(),
        playerClockMillis = playerClockMillis,
        playerClockReserveMillis = playerClockReserveMillis,
    )
}

private const val HIDDEN_ONLINE_DOMINO_VALUE = -1

private val hiddenOnlineDominoPiece = OnlineDominoPieceDto(
    left = HIDDEN_ONLINE_DOMINO_VALUE,
    right = HIDDEN_ONLINE_DOMINO_VALUE,
)

/*
 * O servidor mantém [OnlineMatchSnapshotDto] completo como estado autoritativo.
 * Antes de cruzar a fronteira HTTP, a projeção abaixo preserva somente a mão do
 * participante solicitado. As listas ocultas continuam com o tamanho real para
 * manter a composição visual dos assentos estável no cliente.
 */
fun OnlineMatchSnapshotDto.projectForParticipant(
    seatIndex: Int,
): OnlineMatchSnapshotDto {
    require(seatIndex in gameState.players.indices) {
        "Assento local inválido para projeção do snapshot: $seatIndex."
    }

    if (phase.revealsAllHands()) {
        return this
    }

    return copy(
        gameState = gameState.copy(
            players = gameState.players.mapIndexed { playerIndex, player ->
                if (playerIndex == seatIndex) {
                    player
                } else {
                    player.copy(
                        hand = hiddenOnlinePieces(
                            count = player.hand.size,
                        ),
                    )
                }
            },
            sleepingPieces = hiddenOnlinePieces(
                count = gameState.sleepingPieces.size,
            ),
        ),
    )
}

private fun OnlineMatchPhaseDto.revealsAllHands(): Boolean {
    return type == OnlineMatchPhaseTypeDto.ROUND_SUMMARY ||
            type == OnlineMatchPhaseTypeDto.MATCH_FINISHED
}

private fun hiddenOnlinePieces(
    count: Int,
): List<OnlineDominoPieceDto> {
    return List(count) {
        hiddenOnlineDominoPiece
    }
}
