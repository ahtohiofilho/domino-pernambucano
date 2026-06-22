package com.ahtohiofilho.dominopernambucano.online.observability

import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import java.security.MessageDigest

fun createOnlineTraceStateFingerprint(
    runtimeState: DominoMatchRuntimeState,
    automaticPlayerIndexes: Set<Int>,
): String {
    val gameState = runtimeState.gameState

    val canonicalState = buildString {
        append("round=").append(runtimeState.roundNumber)
        append("|phase=").append(runtimeState.phase.toTraceFingerprintToken())
        append("|clockPolicy=").append(runtimeState.clockPolicy.toTraceFingerprintToken())
        append("|clocks=").append(runtimeState.playerClockMillis.joinToString(","))
        append("|automatic=").append(automaticPlayerIndexes.sorted().joinToString(","))

        append("|board=")
        appendPieces(gameState.board)

        append("|chainOpening=")
        appendPiece(gameState.boardChain.openingPiece)
        append("|chainLeft=")
        appendPieces(gameState.boardChain.leftPieces)
        append("|chainRight=")
        appendPieces(gameState.boardChain.rightPieces)

        append("|sleeping=")
        appendPieces(gameState.sleepingPieces)

        append("|players=")
        gameState.players.forEachIndexed { index, player ->
            append(index)
            append(':')
            append(player.id)
            append(':')
            appendPieces(player.hand)
            append(';')
        }

        append("|currentPlayer=").append(gameState.currentPlayerIndex)
        append("|lastRoundWinner=").append(gameState.lastRoundWinnerIndex)
        append("|openingPiece=")
        appendPiece(gameState.openingPiece)
        append("|teamScores=").append(gameState.teamScores.joinToString(","))
        append("|lastMove=")
        gameState.lastMove?.let { move ->
            append(move.playerIndex)
            append(':')
            appendPiece(move.piece)
            append(':')
            append(move.wasLaELo)
            append(':')
            append(move.wasCruzada)
        }
        append("|roundWinnerPlayer=").append(gameState.roundWinnerPlayerIndex)
        append("|roundWinnerTeam=").append(gameState.roundWinnerTeamIndex)
        append("|roundWinKind=").append(gameState.roundWinKind?.name)
        append("|gameWinnerTeam=").append(gameState.gameWinnerTeamIndex)
        append("|consecutivePasses=").append(gameState.consecutivePassTurns)
        append("|scoreMultiplier=").append(gameState.scoreMultiplier)
        append("|targetScore=").append(gameState.targetScore)
    }

    return MessageDigest
        .getInstance("SHA-256")
        .digest(canonicalState.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff)
                .toString(radix = 16)
                .padStart(length = 2, padChar = '0')
        }
}

private fun StringBuilder.appendPieces(
    pieces: List<DominoPiece>,
) {
    pieces.forEachIndexed { index, piece ->
        if (index > 0) {
            append(',')
        }

        appendPiece(piece)
    }
}

private fun StringBuilder.appendPiece(
    piece: DominoPiece?,
) {
    if (piece == null) {
        append("null")
        return
    }

    append(piece.left)
    append('-')
    append(piece.right)
}

private fun DominoMatchClockPolicy.toTraceFingerprintToken(): String {
    return when (this) {
        DominoMatchClockPolicy.Disabled -> "DISABLED"
        DominoMatchClockPolicy.OnlinePerPlayerRound -> "ONLINE_PER_PLAYER_ROUND"
    }
}

private fun DominoMatchPhase.toTraceFingerprintToken(): String {
    return when (this) {
        DominoMatchPhase.RoundIntro -> "ROUND_INTRO"
        DominoMatchPhase.WaitingForLocalMove -> "WAITING_FOR_LOCAL_MOVE"
        is DominoMatchPhase.PresentingMove -> {
            "PRESENTING_MOVE:${playerIndex}:${move.piece.left}-${move.piece.right}:" +
                    "${move.side.name}:${move.flipped}"
        }

        is DominoMatchPhase.PresentingPass -> {
            "PRESENTING_PASS:${playerIndex}"
        }

        DominoMatchPhase.RoundSummary -> "ROUND_SUMMARY"
        DominoMatchPhase.MatchFinished -> "MATCH_FINISHED"
    }
}
