package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase

/**
 * Display-only end-of-match receipt. No session token, hand or playable game
 * state is saved: finished online participation remains acknowledged normally.
 */
data class PostMatchRecoveryPlayer(
    val seatIndex: Int,
    val name: String,
    val pointsScored: Int,
    val touchesGiven: Int,
    val automaticRounds: Int,
)

data class PostMatchRecoverySnapshot(
    val recordedAtEpochMillis: Long,
    val matchMode: DominoMatchMode,
    val localTeamIndex: Int,
    val localTeamWon: Boolean?,
    val localScore: Int,
    val opponentScore: Int,
    val completedRounds: Int,
    val players: List<PostMatchRecoveryPlayer>,
    val teamBalanceDeltaByTeam: List<Int>,
    val mvpSeatIndex: Int?,
)

internal fun createPostMatchRecoverySnapshot(
    state: DominoGameUiState,
): PostMatchRecoverySnapshot {
    require(state.phase == DominoMatchPhase.MatchFinished)
    val localTeamIndex = state.localPlayerIndex % 2
    val opponentTeamIndex = 1 - localTeamIndex
    val stats = state.postMatchStatistics
    val winner = state.gameState.gameWinnerTeamIndex

    return PostMatchRecoverySnapshot(
        recordedAtEpochMillis = 0L, // Filled at first presentation by the ads controller.
        matchMode = state.matchMode,
        localTeamIndex = localTeamIndex,
        localTeamWon = when (winner) {
            localTeamIndex -> true
            opponentTeamIndex -> false
            else -> null
        },
        localScore = state.gameState.teamScores.getOrElse(localTeamIndex) { 0 },
        opponentScore = state.gameState.teamScores.getOrElse(opponentTeamIndex) { 0 },
        completedRounds = stats?.completedRounds?.takeIf { it > 0 }
            ?: state.roundNumber,
        players = state.gameState.players.mapIndexed { index, player ->
            val playerStats = stats?.players?.firstOrNull { it.seatIndex == index }
            PostMatchRecoveryPlayer(
                seatIndex = index,
                name = player.name.take(64),
                pointsScored = playerStats?.pointsScored ?: 0,
                touchesGiven = playerStats?.touchesGiven ?: 0,
                automaticRounds = playerStats?.automaticRounds ?: 0,
            )
        },
        teamBalanceDeltaByTeam = stats?.teamBalanceDeltaByTeam ?: listOf(0, 0),
        mvpSeatIndex = stats?.mvpSeatIndex,
    )
}

/**
 * Six-minute interstitial throttling is a separate, higher-priority gate.
 * Reopening cannot extend this fixed 60-second recovery window.
 */
internal const val PostMatchRecoveryWindowMillis = 60_000L

internal fun isPostMatchRecoveryActive(
    recordedAtEpochMillis: Long,
    nowEpochMillis: Long,
    windowMillis: Long = PostMatchRecoveryWindowMillis,
): Boolean = recordedAtEpochMillis > 0L &&
    windowMillis > 0L &&
    nowEpochMillis >= recordedAtEpochMillis &&
    nowEpochMillis - recordedAtEpochMillis < windowMillis
