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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            text = "Partida rankeada",
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
                text = "Tentar novamente",
                onClick = onRetryClick,
            )
        }

        when (uiState) {
            OnlineRankedQueueUiState.Idle,
            OnlineRankedQueueUiState.Resuming,
            is OnlineRankedQueueUiState.Waiting,
            OnlineRankedQueueUiState.Cancelling -> {
                SecondaryMenuButton(
                    text = "Cancelar busca",
                    onClick = onCancelClick,
                    enabled =
                        uiState !=
                            OnlineRankedQueueUiState.Cancelling,
                )
            }

            is OnlineRankedQueueUiState.Failure -> {
                SecondaryMenuButton(
                    text = if (uiState.retryable) {
                        "Cancelar busca"
                    } else {
                        "Voltar"
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
                    text = "Buscar novamente",
                    onClick = onRetryClick,
                )

                SecondaryMenuButton(
                    text = "Voltar",
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

private fun OnlineRankedQueueUiState.primaryMessage(): String {
    return when (this) {
        OnlineRankedQueueUiState.Idle,
        OnlineRankedQueueUiState.Resuming ->
            "Verificando sua fila..."

        is OnlineRankedQueueUiState.Waiting ->
            "Você está na posição $queuePosition."

        OnlineRankedQueueUiState.Cancelling ->
            "Cancelando a busca..."

        OnlineRankedQueueUiState.OpeningMatch,
        is OnlineRankedQueueUiState.MatchReady ->
            "Partida encontrada."

        OnlineRankedQueueUiState.Cancelled ->
            "Busca cancelada."

        OnlineRankedQueueUiState.NotQueued ->
            "Você não está na fila."

        is OnlineRankedQueueUiState.Failure -> kind.userMessage()
    }
}

private fun OnlineRankedQueueUiState.secondaryMessage(): String {
    return when (this) {
        is OnlineRankedQueueUiState.Waiting ->
            "Parceiro, adversários e assentos serão definidos pelo servidor."

        OnlineRankedQueueUiState.OpeningMatch,
        is OnlineRankedQueueUiState.MatchReady ->
            "Abrindo a mesa sem utilizar código de sala."

        is OnlineRankedQueueUiState.Failure -> {
            if (retryable) {
                "A fila pode continuar ativa no servidor. Tente novamente ou cancele explicitamente."
            } else {
                "Volte ao menu de conta caso seja necessário confirmar seu acesso."
            }
        }

        else ->
            "A fila pública é exclusiva para contas autenticadas e afeta o ranking."
    }
}

private fun OnlineRankedQueueUiFailureKind.userMessage(): String {
    return when (this) {
        OnlineRankedQueueUiFailureKind.AUTHENTICATION_REQUIRED ->
            "Confirme novamente sua conta para jogar rankeado."

        OnlineRankedQueueUiFailureKind.ACCOUNT_REQUIRED ->
            "Uma conta conectada é obrigatória para jogar rankeado."

        OnlineRankedQueueUiFailureKind.RATE_LIMITED ->
            "Muitas tentativas em pouco tempo. Aguarde e tente novamente."

        OnlineRankedQueueUiFailureKind.UNAVAILABLE ->
            "A fila rankeada está temporariamente indisponível."

        OnlineRankedQueueUiFailureKind.PROTOCOL_ERROR,
        OnlineRankedQueueUiFailureKind.INVALID_MATCH ->
            "A resposta da partida não pôde ser validada com segurança."

        OnlineRankedQueueUiFailureKind.SESSION_REJECTED ->
            "Sua sessão foi rejeitada. Confirme novamente a conta."

        OnlineRankedQueueUiFailureKind.UNKNOWN ->
            "Não foi possível continuar a busca rankeada."
    }
}
