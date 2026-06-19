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

    val serverEpochMillis: Long? = null,

    /*
     * Jogadores que perderam o controle manual por estouro de tempo.
     *
     * Esse estado precisa viajar no snapshot porque o client/coordinator
     * não pode depender apenas do relógio local. Ao iniciar uma nova rodada,
     * os relógios voltam cheios, mas o jogador ainda deve continuar em modo
     * automático até a partida terminar ou uma nova partida ser iniciada.
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
    )
}