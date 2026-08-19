package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.domain.DominoParticipantType
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankedMatchActivation
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueClient
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.ui.game.DominoPlayerIdentityDisc
import com.ahtohiofilho.dominopernambucano.ui.game.resolveDominoVisiblePlayerCode
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun OnlineRankedQueueRoute(
    queueClient: OnlineRankedQueueClient,
    roomRepository: OnlineRoomRepository,
    playerName: String,
    onStartOnlineMatch:
        (OnlinePublicRankedMatchActivation.Ready) -> Unit,
    onBackClick: () -> Unit,
) {
    val controller = remember(
        queueClient,
        roomRepository,
    ) {
        OnlineRankedQueueController(
            queueClient = queueClient,
            activateMatch = { matchId, localSeatIndex ->
                roomRepository.activatePublicRankedMatch(
                    matchId = matchId,
                    localSeatIndex = localSeatIndex,
                )
            },
        )
    }

    val uiState by controller.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var operationJob by remember {
        mutableStateOf<Job?>(null)
    }

    fun startOrRetry() {
        operationJob?.cancel()
        operationJob = coroutineScope.launch {
            controller.open(
                playerName = playerName,
            )
        }
    }

    fun cancelQueue() {
        operationJob?.cancel()
        operationJob = coroutineScope.launch {
            controller.cancel()
        }
    }

    LaunchedEffect(controller, playerName) {
        startOrRetry()
    }

    DisposableEffect(controller) {
        onDispose {
            operationJob?.cancel()
        }
    }

    when (val state = uiState) {
        is OnlineRankedQueueUiState.MatchReady -> {
            LaunchedEffect(state.activation.matchId) {
                onStartOnlineMatch(state.activation)
            }
        }

        OnlineRankedQueueUiState.Cancelled -> {
            LaunchedEffect(Unit) {
                onBackClick()
            }
        }

        else -> Unit
    }

    OnlineRankedQueueScreen(
        uiState = uiState,
        playerName = playerName,
        onRetryClick = ::startOrRetry,
        onCancelClick = ::cancelQueue,
        onBackClick = onBackClick,
    )
}

@Composable
private fun OnlineRankedQueueScreen(
    uiState: OnlineRankedQueueUiState,
    playerName: String,
    onRetryClick: () -> Unit,
    onCancelClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    DominoScreenScaffold(
        title = stringResource(R.string.ranked_queue_title),
        layout = DominoScreenLayout.Centered,
    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.showsProgress()) {
                RankedMatchmakingCluster(
                    localPlayerName = playerName,
                )
            }

            Text(
                text = uiState.primaryMessage(),
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = uiState.secondaryMessage(),
                color =
                    DominoSemanticColors.primaryTextOnDark.copy(
                        alpha = 0.74f,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        if (
            uiState is OnlineRankedQueueUiState.Failure &&
            uiState.retryable
        ) {
            PrimaryMenuButton(
                text = stringResource(R.string.ranked_queue_retry),
                onClick = onRetryClick,
            )
        }

        when (uiState) {
            OnlineRankedQueueUiState.Idle,
            OnlineRankedQueueUiState.Resuming,
            is OnlineRankedQueueUiState.Waiting,
            OnlineRankedQueueUiState.Cancelling -> {
                SecondaryMenuButton(
                    text = stringResource(R.string.ranked_queue_cancel_search),
                    onClick = onCancelClick,
                    enabled =
                        uiState !=
                            OnlineRankedQueueUiState.Cancelling,
                )
            }

            is OnlineRankedQueueUiState.Failure -> {
                SecondaryMenuButton(
                    text = if (uiState.retryable) {
                        stringResource(R.string.ranked_queue_cancel_search)
                    } else {
                        stringResource(R.string.common_back)
                    },
                    onClick = if (uiState.retryable) {
                        onCancelClick
                    } else {
                        onBackClick
                    },
                )
            }

            OnlineRankedQueueUiState.NotQueued -> {
                PrimaryMenuButton(
                    text = stringResource(R.string.ranked_queue_search_again),
                    onClick = onRetryClick,
                )

                SecondaryMenuButton(
                    text = stringResource(R.string.common_back),
                    onClick = onBackClick,
                )
            }

            OnlineRankedQueueUiState.Cancelled,
            OnlineRankedQueueUiState.OpeningMatch,
            is OnlineRankedQueueUiState.MatchReady -> Unit
        }
    }
}

@Composable
private fun RankedMatchmakingCluster(
    localPlayerName: String,
) {
    val localCode = resolveDominoVisiblePlayerCode(
        name = localPlayerName,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DominoPlayerIdentityDisc(
                name = localPlayerName,
                participantType = DominoParticipantType.HUMAN,
                isCurrent = false,
                isWinner = false,
                identitySize = 44.dp,
            )

            Text(
                text = localCode,
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        repeat(3) {
            RankedSearchingPlayerSlot()
        }
    }
}

@Composable
private fun RankedSearchingPlayerSlot() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(
                width = 1.dp,
                color = DominoSemanticColors.primaryTextOnDark.copy(
                    alpha = 0.30f,
                ),
                shape = CircleShape,
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = DominoSemanticColors.primaryTextOnDark.copy(
                alpha = 0.62f,
            ),
            strokeWidth = 2.dp,
        )
    }
}

private fun OnlineRankedQueueUiState.showsProgress(): Boolean {
    return this == OnlineRankedQueueUiState.Idle ||
        this == OnlineRankedQueueUiState.Resuming ||
        this is OnlineRankedQueueUiState.Waiting ||
        this == OnlineRankedQueueUiState.Cancelling ||
        this == OnlineRankedQueueUiState.OpeningMatch
}

@Composable
private fun OnlineRankedQueueUiState.primaryMessage(): String {
    return when (this) {
        OnlineRankedQueueUiState.Idle,
        OnlineRankedQueueUiState.Resuming ->
            stringResource(R.string.online_queue_checking)

        is OnlineRankedQueueUiState.Waiting ->
            stringResource(R.string.online_queue_position_format, queuePosition)

        OnlineRankedQueueUiState.Cancelling ->
            stringResource(R.string.online_queue_cancelling)

        OnlineRankedQueueUiState.OpeningMatch,
        is OnlineRankedQueueUiState.MatchReady ->
            stringResource(R.string.online_queue_match_found)

        OnlineRankedQueueUiState.Cancelled ->
            stringResource(R.string.online_queue_cancelled)

        OnlineRankedQueueUiState.NotQueued ->
            stringResource(R.string.online_queue_not_joined)

        is OnlineRankedQueueUiState.Failure -> kind.userMessage()
    }
}

@Composable
private fun OnlineRankedQueueUiState.secondaryMessage(): String {
    return when (this) {
        is OnlineRankedQueueUiState.Waiting ->
            stringResource(R.string.online_queue_server_assignment)

        OnlineRankedQueueUiState.OpeningMatch,
        is OnlineRankedQueueUiState.MatchReady ->
            stringResource(R.string.online_queue_opening_table)

        is OnlineRankedQueueUiState.Failure -> {
            if (retryable) {
                stringResource(R.string.online_queue_may_remain_active)
            } else {
                stringResource(R.string.online_queue_account_access_hint)
            }
        }

        else ->
            stringResource(R.string.online_queue_ranked_policy)
    }
}

@Composable
private fun OnlineRankedQueueUiFailureKind.userMessage(): String {
    return when (this) {
        OnlineRankedQueueUiFailureKind.AUTHENTICATION_REQUIRED ->
            stringResource(R.string.online_queue_authentication_required)

        OnlineRankedQueueUiFailureKind.ACCOUNT_REQUIRED ->
            stringResource(R.string.online_queue_account_required)

        OnlineRankedQueueUiFailureKind.RATE_LIMITED ->
            stringResource(R.string.online_queue_rate_limited)

        OnlineRankedQueueUiFailureKind.UNAVAILABLE ->
            stringResource(R.string.online_queue_unavailable)

        OnlineRankedQueueUiFailureKind.PROTOCOL_ERROR,
        OnlineRankedQueueUiFailureKind.INVALID_MATCH ->
            stringResource(R.string.online_queue_invalid_match)

        OnlineRankedQueueUiFailureKind.SESSION_REJECTED ->
            stringResource(R.string.online_queue_session_rejected)

        OnlineRankedQueueUiFailureKind.UNKNOWN ->
            stringResource(R.string.online_queue_continue_failed)
    }
}
