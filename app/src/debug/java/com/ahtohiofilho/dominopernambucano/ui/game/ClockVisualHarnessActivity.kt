package com.ahtohiofilho.dominopernambucano.ui.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ahtohiofilho.dominopernambucano.domain.DominoBoardChain
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.DominoPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoPlayer
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

class ClockVisualHarnessActivity : ComponentActivity() {
    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        enableImmersiveMode()

        val harnessState = ClockHarnessState.fromWireName(
            intent.getStringExtra(EXTRA_HARNESS_STATE),
        )

        setContent {
            DominoPernambucanoTheme {
                ClockVisualHarnessScreen(
                    harnessState = harnessState,
                )
            }
        }
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean,
    ) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(
            window,
            false,
        )

        val controller = WindowInsetsControllerCompat(
            window,
            window.decorView,
        )

        controller.hide(
            WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.navigationBars(),
        )

        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    companion object {
        const val EXTRA_HARNESS_STATE =
            "domino_clock_harness_state"
    }
}

private enum class ClockHarnessState(
    val wireName: String,
    val currentPlayerIndex: Int,
    val primaryMillis: List<Long>,
    val reserveMillis: List<Long>,
) {
    NORMAL(
        wireName = "normal",
        currentPlayerIndex = 0,
        primaryMillis = listOf(
            20_000L,
            20_000L,
            20_000L,
            20_000L,
        ),
        reserveMillis = listOf(
            20_000L,
            20_000L,
            20_000L,
            20_000L,
        ),
    ),
    INTERMEDIATE(
        wireName = "intermediate",
        currentPlayerIndex = 1,
        primaryMillis = listOf(
            12_000L,
            12_000L,
            10_000L,
            8_000L,
        ),
        reserveMillis = listOf(
            8_000L,
            8_000L,
            15_000L,
            12_000L,
        ),
    ),
    CRITICAL(
        wireName = "critical",
        currentPlayerIndex = 2,
        primaryMillis = listOf(
            5_000L,
            4_000L,
            3_000L,
            1_000L,
        ),
        reserveMillis = listOf(
            3_000L,
            2_000L,
            1_000L,
            0L,
        ),
    ),
    EXHAUSTED(
        wireName = "exhausted",
        currentPlayerIndex = 3,
        primaryMillis = listOf(
            0L,
            0L,
            0L,
            0L,
        ),
        reserveMillis = listOf(
            0L,
            0L,
            0L,
            0L,
        ),
    );

    companion object {
        fun fromWireName(
            value: String?,
        ): ClockHarnessState {
            return values().firstOrNull { state ->
                state.wireName == value
            } ?: NORMAL
        }
    }
}

@Composable
private fun ClockVisualHarnessScreen(
    harnessState: ClockHarnessState,
) {
    val uiState = buildClockHarnessUiState(
        harnessState = harnessState,
    )
    val gameState = uiState.gameState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DominoSemanticColors.appBackground,
                        DominoColorTokens.PernambucoBlueDark,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 10.dp,
                    vertical = 8.dp,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        DominoGameVisualTokens.HeaderSlotHeight,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                DominoMatchHeader(
                    uiState = uiState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            DominoGameTableStage(
                gameState = gameState,
                localPlayerIndex = uiState.localPlayerIndex,
                localPlayableMoves = emptyList(),
                showDropTargets = false,
                highlightedDropSide = null,
                animatedPlayableMove = null,
                animatedMovePresentationKey = null,
                revealOpponentHands = false,
                roundWinnerPlayerIndex = null,
                visualPiecesForPlayer = { playerIndex ->
                    gameState.players
                        .getOrNull(playerIndex)
                        ?.hand
                        .orEmpty()
                },
                onDropTargetsChanged = {},
                onAnimatedMoveTargetChanged = {},
                onPlayerSeatBoundsChanged = { _, _ -> },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        vertical =
                            DominoGameVisualTokens.TableStageVerticalPadding,
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        DominoGameVisualTokens.LocalHandSlotHeight,
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                DominoLocalHand(
                    uiState = uiState,
                    pieces = gameState.players
                        .getOrNull(uiState.localPlayerIndex)
                        ?.hand
                        .orEmpty(),
                    onLocalMoveSelected = {},
                    onLocalHandBoundsChanged = {},
                    onPieceDragStart = { _, _ -> },
                    onPieceDrag = {},
                    onPieceDragEnd = {},
                    onPieceDragCancel = {},
                )
            }
        }

        DominoTurnCountdownHud(
            uiState = uiState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun buildClockHarnessUiState(
    harnessState: ClockHarnessState,
): DominoGameUiState {
    val players = listOf(
        DominoPlayer(
            id = 0,
            name = "ANTONIO FILHO",
            hand = listOf(
                DominoPiece(6, 6),
                DominoPiece(6, 5),
                DominoPiece(4, 4),
                DominoPiece(3, 2),
                DominoPiece(1, 0),
            ),
        ),
        DominoPlayer(
            id = 1,
            name = "BENTO COSTA",
            hand = listOf(
                DominoPiece(5, 5),
                DominoPiece(5, 4),
                DominoPiece(4, 3),
                DominoPiece(2, 2),
                DominoPiece(1, 1),
            ),
        ),
        DominoPlayer(
            id = 2,
            name = "CARLA LIMA",
            hand = listOf(
                DominoPiece(6, 4),
                DominoPiece(6, 3),
                DominoPiece(5, 2),
                DominoPiece(3, 3),
                DominoPiece(2, 0),
            ),
        ),
        DominoPlayer(
            id = 3,
            name = "DIEGO MOURA",
            hand = listOf(
                DominoPiece(6, 2),
                DominoPiece(6, 1),
                DominoPiece(5, 3),
                DominoPiece(4, 2),
                DominoPiece(0, 0),
            ),
        ),
    )

    val gameState = DominoGameState(
        board = emptyList(),
        boardChain = DominoBoardChain(),
        players = players,
        sleepingPieces = emptyList(),
        currentPlayerIndex =
            harnessState.currentPlayerIndex,
        lastRoundWinnerIndex = null,
        openingPiece = null,
        teamScores = listOf(0, 0),
        lastMove = null,
        roundWinnerPlayerIndex = null,
        roundWinnerTeamIndex = null,
        roundWinKind = null,
        gameWinnerTeamIndex = null,
    )

    return DominoGameUiState(
        gameState = gameState,
        roundNumber = 1,
        localPlayerIndex = 0,
        phase = DominoMatchPhase.WaitingForLocalMove,
        localPlayableMoves = emptyList(),
        playerClockMillis =
            harnessState.primaryMillis,
        playerClockReserveMillis =
            harnessState.reserveMillis,
        isTurnClockEnabled = true,
        turnClockTotalMillis = 20_000L,
    )
}
