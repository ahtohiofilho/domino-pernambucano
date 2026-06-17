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