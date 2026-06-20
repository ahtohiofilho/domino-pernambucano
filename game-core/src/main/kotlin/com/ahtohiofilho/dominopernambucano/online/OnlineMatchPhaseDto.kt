package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import kotlinx.serialization.Serializable

@Serializable
enum class OnlineMatchPhaseTypeDto {
    ROUND_INTRO,
    WAITING_FOR_LOCAL_MOVE,
    PRESENTING_MOVE,
    PRESENTING_PASS,
    ROUND_SUMMARY,
    MATCH_FINISHED,
}

@Serializable
data class OnlineMatchPhaseDto(
    val type: OnlineMatchPhaseTypeDto,
    val playerIndex: Int? = null,
    val move: OnlinePlayableMoveDto? = null,
)

fun DominoMatchPhase.toOnlineDto(): OnlineMatchPhaseDto {
    return when (this) {
        DominoMatchPhase.RoundIntro -> OnlineMatchPhaseDto(
            type = OnlineMatchPhaseTypeDto.ROUND_INTRO,
        )

        DominoMatchPhase.WaitingForLocalMove -> OnlineMatchPhaseDto(
            type = OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE,
        )

        is DominoMatchPhase.PresentingMove -> OnlineMatchPhaseDto(
            type = OnlineMatchPhaseTypeDto.PRESENTING_MOVE,
            playerIndex = playerIndex,
            move = move.toOnlineDto(),
        )

        is DominoMatchPhase.PresentingPass -> OnlineMatchPhaseDto(
            type = OnlineMatchPhaseTypeDto.PRESENTING_PASS,
            playerIndex = playerIndex,
        )

        DominoMatchPhase.RoundSummary -> OnlineMatchPhaseDto(
            type = OnlineMatchPhaseTypeDto.ROUND_SUMMARY,
        )

        DominoMatchPhase.MatchFinished -> OnlineMatchPhaseDto(
            type = OnlineMatchPhaseTypeDto.MATCH_FINISHED,
        )
    }
}

fun OnlineMatchPhaseDto.toDomain(): DominoMatchPhase {
    return when (type) {
        OnlineMatchPhaseTypeDto.ROUND_INTRO -> DominoMatchPhase.RoundIntro

        OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE -> {
            DominoMatchPhase.WaitingForLocalMove
        }

        OnlineMatchPhaseTypeDto.PRESENTING_MOVE -> {
            DominoMatchPhase.PresentingMove(
                playerIndex = requireNotNull(playerIndex) {
                    "PRESENTING_MOVE exige playerIndex."
                },
                move = requireNotNull(move) {
                    "PRESENTING_MOVE exige move."
                }.toDomain(),
            )
        }

        OnlineMatchPhaseTypeDto.PRESENTING_PASS -> {
            DominoMatchPhase.PresentingPass(
                playerIndex = requireNotNull(playerIndex) {
                    "PRESENTING_PASS exige playerIndex."
                },
            )
        }

        OnlineMatchPhaseTypeDto.ROUND_SUMMARY -> DominoMatchPhase.RoundSummary

        OnlineMatchPhaseTypeDto.MATCH_FINISHED -> DominoMatchPhase.MatchFinished
    }
}