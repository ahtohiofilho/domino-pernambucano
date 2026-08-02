package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankedMatchActivation
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueClient
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.ui.menu.MenuScaffold
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
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
        onRetryClick = ::startOrRetry,
        onCancelClick = ::cancelQueue,
        onBackClick = onBackClick,
    )
}

@Composable
private fun OnlineRankedQueueScreen(
    uiState: OnlineRankedQueueUiState,
    onRetryClick: () -> Unit,
    onCancelClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = stringResource(R.string.ranked_queue_title),
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.showsProgress()) {
                CircularProgressIndicator()
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
