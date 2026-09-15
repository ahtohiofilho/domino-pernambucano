package com.ahtohiofilho.dominopernambucano.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val PartnerClockLateralOffsetFraction = 0.30f
private const val LocalClockLateralOffsetFraction = -0.24f
private const val SideClockLateralOffsetFraction = 0.18f

private enum class DominoClockPairOrientation {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun DominoTurnCountdownHud(
    uiState: DominoGameUiState,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isTurnClockEnabled || uiState.turnClockTotalMillis <= 0L) {
        return
    }

    val localPlayerIndex = uiState.localPlayerIndex
    val leftPlayerIndex = (localPlayerIndex + 1) % 4
    val topPlayerIndex = (localPlayerIndex + 2) % 4
    val rightPlayerIndex = (localPlayerIndex + 3) % 4

    val currentPlayerIndex = uiState.gameState.currentPlayerIndex

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val partnerLateralOffset =
            maxWidth * PartnerClockLateralOffsetFraction

        val localLateralOffset =
            maxWidth * LocalClockLateralOffsetFraction

        val sideLateralOffset =
            maxHeight * SideClockLateralOffsetFraction

        DominoPlayerClockPair(
            playerIndex = topPlayerIndex,
            uiState = uiState,
            isCurrent =
                currentPlayerIndex == topPlayerIndex,
            orientation =
                DominoClockPairOrientation.VERTICAL,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    top =
                        DominoGameVisualTokens.HeaderSlotHeight +
                        6.dp,
                )
                .offset(
                    x = partnerLateralOffset,
                ),
        )

        DominoPlayerClockPair(
            playerIndex = leftPlayerIndex,
            uiState = uiState,
            isCurrent =
                currentPlayerIndex == leftPlayerIndex,
            orientation =
                DominoClockPairOrientation.VERTICAL,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = 10.dp,
                )
                .offset(
                    y = -sideLateralOffset,
                ),
        )

        DominoPlayerClockPair(
            playerIndex = rightPlayerIndex,
            uiState = uiState,
            isCurrent =
                currentPlayerIndex == rightPlayerIndex,
            orientation =
                DominoClockPairOrientation.VERTICAL,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    end = 10.dp,
                )
                .offset(
                    y = sideLateralOffset,
                ),
        )

        DominoPlayerClockPair(
            playerIndex = localPlayerIndex,
            uiState = uiState,
            isCurrent =
                currentPlayerIndex == localPlayerIndex,
            orientation =
                DominoClockPairOrientation.HORIZONTAL,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom =
                        DominoGameVisualTokens.LocalHandSlotHeight +
                        8.dp,
                )
                .offset(
                    x = localLateralOffset,
                ),
        )
    }
}

@Composable
private fun DominoPlayerClockPair(
    playerIndex: Int,
    uiState: DominoGameUiState,
    isCurrent: Boolean,
    orientation: DominoClockPairOrientation,
    modifier: Modifier = Modifier,
) {
    val primaryMillis =
        uiState.playerClockMillis.getOrNull(playerIndex)
            ?: return

    val reserveMillis =
        uiState.playerClockReserveMillis
            .getOrNull(playerIndex)
            ?: 0L

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.08f else 1f,
        label = "dominoPlayerClockPairScale",
    )

    val pairModifier = modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

    when (orientation) {
        DominoClockPairOrientation.HORIZONTAL -> {
            Row(
                modifier = pairModifier,
                horizontalArrangement =
                    Arrangement.spacedBy(4.dp),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                DominoClockBadges(
                    primaryMillis = primaryMillis,
                    reserveMillis = reserveMillis,
                    uiState = uiState,
                    isCurrent = isCurrent,
                    compact = false,
                )
            }
        }

        DominoClockPairOrientation.VERTICAL -> {
            Column(
                modifier = pairModifier,
                verticalArrangement =
                    Arrangement.spacedBy(3.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
            ) {
                DominoClockBadges(
                    primaryMillis = primaryMillis,
                    reserveMillis = reserveMillis,
                    uiState = uiState,
                    isCurrent = isCurrent,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun DominoClockBadges(
    primaryMillis: Long,
    reserveMillis: Long,
    uiState: DominoGameUiState,
    isCurrent: Boolean,
    compact: Boolean,
) {
    DominoPlayerClockBadge(
        remainingMillis = primaryMillis,
        kind = DominoPlayerClockKind.PRIMARY,
        isEnabled = uiState.isTurnClockEnabled,
        isCurrent = isCurrent,
        compact = compact,
    )

    DominoPlayerClockBadge(
        remainingMillis = reserveMillis,
        kind = DominoPlayerClockKind.RESERVE,
        isEnabled = uiState.isTurnClockEnabled,
        isCurrent = isCurrent,
        compact = true,
    )
}
