package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.domain.BoardSide
import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.domain.PlayedMove
import com.ahtohiofilho.dominopernambucano.domain.RoundWinKind
import kotlinx.serialization.Serializable

@Serializable
data class OnlineDominoPieceDto(
    val left: Int,
    val right: Int,
)

@Serializable
enum class OnlineBoardSideDto {
    LEFT,
    RIGHT,
}

@Serializable
data class OnlinePlayableMoveDto(
    val piece: OnlineDominoPieceDto,
    val side: OnlineBoardSideDto,
    val flipped: Boolean,
)

@Serializable
data class OnlinePlayedMoveDto(
    val playerIndex: Int,
    val piece: OnlineDominoPieceDto,
    val wasLaELo: Boolean,
    val wasCruzada: Boolean,
)

@Serializable
data class OnlineDominoPlayerDto(
    val id: Int,
    val name: String,
    val hand: List<OnlineDominoPieceDto>,
    val participantType: OnlineParticipantTypeDto =
        OnlineParticipantTypeDto.HUMAN,
)

@Serializable
data class OnlineDominoBoardChainDto(
    val openingPiece: OnlineDominoPieceDto? = null,
    val leftPieces: List<OnlineDominoPieceDto> = emptyList(),
    val rightPieces: List<OnlineDominoPieceDto> = emptyList(),
)

@Serializable
data class OnlineDominoGameStateDto(
    val board: List<OnlineDominoPieceDto>,
    val boardChain: OnlineDominoBoardChainDto,
    val players: List<OnlineDominoPlayerDto>,
    val sleepingPieces: List<OnlineDominoPieceDto>,

    val currentPlayerIndex: Int,
    val lastRoundWinnerIndex: Int?,
    val openingPiece: OnlineDominoPieceDto?,

    val teamScores: List<Int>,

    val lastMove: OnlinePlayedMoveDto?,

    val roundWinnerPlayerIndex: Int?,
    val roundWinnerTeamIndex: Int?,
    val roundWinKind: String?,

    val gameWinnerTeamIndex: Int?,

    val consecutivePassTurns: Int,
    val scoreMultiplier: Int,
    val targetScore: Int,
)

fun DominoPiece.toOnlineDto(): OnlineDominoPieceDto {
    return OnlineDominoPieceDto(
        left = left,
        right = right,
    )
}

fun OnlineDominoPieceDto.toDomain(): DominoPiece {
    return DominoPiece(
        left = left,
        right = right,
    )
}

fun BoardSide.toOnlineDto(): OnlineBoardSideDto {
    return when (this) {
        BoardSide.LEFT -> OnlineBoardSideDto.LEFT
        BoardSide.RIGHT -> OnlineBoardSideDto.RIGHT
    }
}

fun OnlineBoardSideDto.toDomain(): BoardSide {
    return when (this) {
        OnlineBoardSideDto.LEFT -> BoardSide.LEFT
        OnlineBoardSideDto.RIGHT -> BoardSide.RIGHT
    }
}

fun PlayableMove.toOnlineDto(): OnlinePlayableMoveDto {
    return OnlinePlayableMoveDto(
        piece = piece.toOnlineDto(),
        side = side.toOnlineDto(),
        flipped = flipped,
    )
}

fun OnlinePlayableMoveDto.toDomain(): PlayableMove {
    return PlayableMove(
        piece = piece.toDomain(),
        side = side.toDomain(),
        flipped = flipped,
    )
}

fun PlayedMove.toOnlineDto(): OnlinePlayedMoveDto {
    return OnlinePlayedMoveDto(
        playerIndex = playerIndex,
        piece = piece.toOnlineDto(),
        wasLaELo = wasLaELo,
        wasCruzada = wasCruzada,
    )
}

fun OnlinePlayedMoveDto.toDomain(): PlayedMove {
    return PlayedMove(
        playerIndex = playerIndex,
        piece = piece.toDomain(),
        wasLaELo = wasLaELo,
        wasCruzada = wasCruzada,
    )
}

fun DominoParticipantType.toOnlineDto(): OnlineParticipantTypeDto {
    return when (this) {
        DominoParticipantType.HUMAN ->
            OnlineParticipantTypeDto.HUMAN

        DominoParticipantType.APPLICATION ->
            OnlineParticipantTypeDto.APPLICATION
    }
}

fun OnlineParticipantTypeDto.toDomain(): DominoParticipantType {
    return when (this) {
        OnlineParticipantTypeDto.HUMAN ->
            DominoParticipantType.HUMAN

        OnlineParticipantTypeDto.APPLICATION ->
            DominoParticipantType.APPLICATION
    }
}

fun DominoPlayer.toOnlineDto(): OnlineDominoPlayerDto {
    return OnlineDominoPlayerDto(
        id = id,
        name = name,
        hand = hand.map { piece ->
            piece.toOnlineDto()
        },
        participantType = participantType.toOnlineDto(),
    )
}

fun OnlineDominoPlayerDto.toDomain(): DominoPlayer {
    return DominoPlayer(
        id = id,
        name = name,
        hand = hand.map { piece ->
            piece.toDomain()
        },
        participantType = participantType.toDomain(),
    )
}

fun DominoBoardChain.toOnlineDto(): OnlineDominoBoardChainDto {
    return OnlineDominoBoardChainDto(
        openingPiece = openingPiece?.toOnlineDto(),
        leftPieces = leftPieces.map { piece ->
            piece.toOnlineDto()
        },
        rightPieces = rightPieces.map { piece ->
            piece.toOnlineDto()
        },
    )
}

fun OnlineDominoBoardChainDto.toDomain(): DominoBoardChain {
    return DominoBoardChain(
        openingPiece = openingPiece?.toDomain(),
        leftPieces = leftPieces.map { piece ->
            piece.toDomain()
        },
        rightPieces = rightPieces.map { piece ->
            piece.toDomain()
        },
    )
}

fun DominoGameState.toOnlineDto(): OnlineDominoGameStateDto {
    return OnlineDominoGameStateDto(
        board = board.map { piece ->
            piece.toOnlineDto()
        },
        boardChain = boardChain.toOnlineDto(),
        players = players.map { player ->
            player.toOnlineDto()
        },
        sleepingPieces = sleepingPieces.map { piece ->
            piece.toOnlineDto()
        },
        currentPlayerIndex = currentPlayerIndex,
        lastRoundWinnerIndex = lastRoundWinnerIndex,
        openingPiece = openingPiece?.toOnlineDto(),
        teamScores = teamScores,
        lastMove = lastMove?.toOnlineDto(),
        roundWinnerPlayerIndex = roundWinnerPlayerIndex,
        roundWinnerTeamIndex = roundWinnerTeamIndex,
        roundWinKind = roundWinKind?.name,
        gameWinnerTeamIndex = gameWinnerTeamIndex,
        consecutivePassTurns = consecutivePassTurns,
        scoreMultiplier = scoreMultiplier,
        targetScore = targetScore,
    )
}

fun OnlineDominoGameStateDto.toDomain(): DominoGameState {
    return DominoGameState(
        board = board.map { piece ->
            piece.toDomain()
        },
        boardChain = boardChain.toDomain(),
        players = players.map { player ->
            player.toDomain()
        },
        sleepingPieces = sleepingPieces.map { piece ->
            piece.toDomain()
        },
        currentPlayerIndex = currentPlayerIndex,
        lastRoundWinnerIndex = lastRoundWinnerIndex,
        openingPiece = openingPiece?.toDomain(),
        teamScores = teamScores,
        lastMove = lastMove?.toDomain(),
        roundWinnerPlayerIndex = roundWinnerPlayerIndex,
        roundWinnerTeamIndex = roundWinnerTeamIndex,
        roundWinKind = roundWinKind?.let { value ->
            RoundWinKind.valueOf(value)
        },
        gameWinnerTeamIndex = gameWinnerTeamIndex,
        consecutivePassTurns = consecutivePassTurns,
        scoreMultiplier = scoreMultiplier,
        targetScore = targetScore,
    )
}