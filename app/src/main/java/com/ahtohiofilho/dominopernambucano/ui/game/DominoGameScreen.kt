package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.domain.PlayableMove
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun DominoGameScreen(
    uiState: DominoGameUiState,
    onBackToMenuClick: () -> Unit,
    onRoundIntroFinished: () -> Unit,
    onLocalMoveSelected: (PlayableMove) -> Unit,
    onPresentationFinished: () -> Unit,
    onStartNextRound: () -> Unit,
    onStartNewMatch: () -> Unit,
) {
    val gameState = uiState.gameState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DominoSemanticColors.appBackground,
                        DominoColorTokens.PernambucoBlueDark,
                    ),
                ),
            )
            .padding(
                horizontal = 14.dp,
                vertical = 12.dp,
            ),
    ) {
        DominoMatchHeader(
            uiState = uiState,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            DominoPlayerSeat(
                name = gameState.players.getOrNull(2)?.name ?: "Jogador 3",
                piecesCount = gameState.players.getOrNull(2)?.hand?.size ?: 0,
                isCurrent = gameState.currentPlayerIndex == 2,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            DominoPlayerSeat(
                name = gameState.players.getOrNull(1)?.name ?: "Jogador 2",
                piecesCount = gameState.players.getOrNull(1)?.hand?.size ?: 0,
                isCurrent = gameState.currentPlayerIndex == 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 4.dp),
            )

            DominoPlayerSeat(
                name = gameState.players.getOrNull(3)?.name ?: "Jogador 4",
                piecesCount = gameState.players.getOrNull(3)?.hand?.size ?: 0,
                isCurrent = gameState.currentPlayerIndex == 3,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-4).dp),
            )

            DominoTableArea(
                gameState = gameState,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.80f)
                    .fillMaxHeight(0.62f),
            )
        }

        DominoLocalHand(
            uiState = uiState,
            onLocalMoveSelected = onLocalMoveSelected,
        )

        Spacer(
            modifier = Modifier.height(10.dp),
        )

        DominoGameActionPanel(
            uiState = uiState,
            onBackToMenuClick = onBackToMenuClick,
            onRoundIntroFinished = onRoundIntroFinished,
            onLocalMoveSelected = onLocalMoveSelected,
            onPresentationFinished = onPresentationFinished,
            onStartNextRound = onStartNextRound,
            onStartNewMatch = onStartNewMatch,
        )
    }
}