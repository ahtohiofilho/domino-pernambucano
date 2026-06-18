package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDebugOptions
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.createDebugFakeOnlinePlayerId
import com.ahtohiofilho.dominopernambucano.online.createDebugFakeOnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.createDebugHostOnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.ui.menu.MenuScaffold
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlinx.coroutines.launch

@Composable
fun OnlineJoinRoomRoute(
    roomRepository: OnlineRoomRepository,
    localPlayerIdentity: OnlinePlayerIdentity,
    debugOptions: OnlineDebugOptions,
    onStartOnlineMatch: (OnlineDominoMatchCoordinator) -> Unit,
    onBackClick: () -> Unit,
) {
    val roomSnapshot by roomRepository.roomSnapshot.collectAsState()
    val matchSnapshot by roomRepository.matchSnapshot.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val localPlayerId = localPlayerIdentity.playerId
    val localPlayerName = localPlayerIdentity.playerName

    var roomCodeInput by remember {
        mutableStateOf("")
    }

    var feedbackMessage by remember {
        mutableStateOf<String?>(null)
    }

    var hasJoinedRoom by remember {
        mutableStateOf(false)
    }

    var hasOpenedMatch by remember {
        mutableStateOf(false)
    }

    var nextFakePlayerNumber by remember {
        mutableIntStateOf(2)
    }

    LaunchedEffect(roomSnapshot?.roomCode) {
        val currentRoomCode = roomSnapshot?.roomCode

        if (
            roomCodeInput.isBlank() &&
            currentRoomCode != null &&
            !hasJoinedRoom
        ) {
            roomCodeInput = currentRoomCode
        }
    }

    LaunchedEffect(
        roomSnapshot,
        matchSnapshot,
        hasOpenedMatch,
        hasJoinedRoom,
    ) {
        val currentRoomSnapshot = roomSnapshot
        val currentMatchSnapshot = matchSnapshot

        if (
            currentRoomSnapshot == null ||
            currentMatchSnapshot == null ||
            hasOpenedMatch ||
            !hasJoinedRoom
        ) {
            return@LaunchedEffect
        }

        val localSeatIndex = findLocalSeatIndexForJoin(
            roomSnapshot = currentRoomSnapshot,
            localPlayerId = localPlayerId,
        ) ?: return@LaunchedEffect

        hasOpenedMatch = true

        val matchCoordinator = OnlineDominoMatchCoordinator(
            repository = roomRepository,
            roomId = currentMatchSnapshot.roomId,
            matchId = currentMatchSnapshot.matchId,
            localPlayerId = localPlayerId,
            localPlayerIndex = localSeatIndex,
            initialSnapshot = currentMatchSnapshot,
        )

        onStartOnlineMatch(matchCoordinator)
    }

    OnlineJoinRoomScreen(
        roomSnapshot = roomSnapshot,
        roomCodeInput = roomCodeInput,
        hasJoinedRoom = hasJoinedRoom,
        feedbackMessage = feedbackMessage,
        allowDemoRoomCreation = debugOptions.allowDemoRoomCreation,
        allowFakePlayerCompletion = debugOptions.allowFakePlayerCompletion,
        onRoomCodeChange = { value ->
            roomCodeInput = value
                .filter { char -> char.isLetterOrDigit() }
                .uppercase()
                .take(8)
        },
        onCreateDemoRoomClick = {
            if (!debugOptions.allowDemoRoomCreation) {
                feedbackMessage = "Criação de sala fake está desabilitada neste ambiente."
                return@OnlineJoinRoomScreen
            }

            coroutineScope.launch {
                val debugHostIdentity = createDebugHostOnlinePlayerIdentity()

                val result = roomRepository.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = debugHostIdentity.playerId,
                        playerName = debugHostIdentity.playerName,
                    )
                )

                if (result.accepted) {
                    roomCodeInput = result.roomSnapshot?.roomCode.orEmpty()
                    hasJoinedRoom = false
                    hasOpenedMatch = false
                    feedbackMessage = "Sala fake criada. Agora entre com o código."
                } else {
                    feedbackMessage = result.reason
                        ?: "Não foi possível criar a sala fake."
                }
            }
        },
        onJoinClick = {
            coroutineScope.launch {
                val normalizedRoomCode = roomCodeInput.trim()

                if (normalizedRoomCode.isBlank()) {
                    feedbackMessage = "Informe o código da sala."
                    return@launch
                }

                val result = roomRepository.joinRoom(
                    JoinOnlineRoomRequestDto(
                        roomCode = normalizedRoomCode,
                        localPlayerId = localPlayerId,
                        playerName = localPlayerName,
                    )
                )

                if (result.accepted) {
                    hasJoinedRoom = true
                    feedbackMessage = null
                } else {
                    feedbackMessage = result.reason
                        ?: "Não foi possível entrar na sala."
                }
            }
        },
        onCompleteWithFakePlayersClick = {
            if (!debugOptions.allowFakePlayerCompletion) {
                feedbackMessage = "Completar mesa com fakes está desabilitado neste ambiente."
                return@OnlineJoinRoomScreen
            }

            coroutineScope.launch {
                var workingSnapshot = roomRepository.roomSnapshot.value
                    ?: return@launch

                while (
                    workingSnapshot.status == OnlineRoomStatusDto.WAITING_FOR_PLAYERS &&
                    workingSnapshot.players.size < 4
                ) {
                    val fakePlayerNumber = resolveNextFakePlayerNumber(
                        players = workingSnapshot.players,
                        preferredNumber = nextFakePlayerNumber,
                    )

                    val fakePlayerIdentity = createDebugFakeOnlinePlayerIdentity(
                        fakePlayerNumber = fakePlayerNumber,
                    )

                    val result = roomRepository.joinRoom(
                        JoinOnlineRoomRequestDto(
                            roomCode = workingSnapshot.roomCode,
                            localPlayerId = fakePlayerIdentity.playerId,
                            playerName = fakePlayerIdentity.playerName,
                        )
                    )

                    if (!result.accepted) {
                        feedbackMessage = result.reason
                            ?: "Não foi possível completar a mesa."
                        return@launch
                    }

                    nextFakePlayerNumber = fakePlayerNumber + 1

                    workingSnapshot = result.roomSnapshot
                        ?: roomRepository.roomSnapshot.value
                                ?: workingSnapshot
                }

                feedbackMessage = null
            }
        },
        onBackClick = {
            coroutineScope.launch {
                if (hasJoinedRoom) {
                    roomRepository.leaveRoom()
                }

                onBackClick()
            }
        },
    )
}

@Composable
private fun OnlineJoinRoomScreen(
    roomSnapshot: OnlineRoomSnapshotDto?,
    roomCodeInput: String,
    hasJoinedRoom: Boolean,
    feedbackMessage: String?,
    allowDemoRoomCreation: Boolean,
    allowFakePlayerCompletion: Boolean,
    onRoomCodeChange: (String) -> Unit,
    onCreateDemoRoomClick: () -> Unit,
    onJoinClick: () -> Unit,
    onCompleteWithFakePlayersClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = if (hasJoinedRoom) {
                "Sala online"
            } else {
                "Entrar em sala"
            },
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = if (hasJoinedRoom) {
                if (allowFakePlayerCompletion) {
                    "Você entrou na sala. Complete a mesa com jogadores fake para validar o fluxo ponta a ponta."
                } else {
                    "Você entrou na sala. Aguarde os demais jogadores para iniciar a partida."
                }
            } else {
                if (allowDemoRoomCreation) {
                    "Fluxo fake em memória para validar entrada em sala antes do backend real."
                } else {
                    "Informe o código da sala para entrar na partida online."
                }
            },
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        if (hasJoinedRoom) {
            if (roomSnapshot == null) {
                CircularProgressIndicator(
                    color = DominoSemanticColors.primaryTextOnDark,
                )

                Text(
                    text = "Carregando sala...",
                    color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                JoinRoomCodeCard(
                    roomSnapshot = roomSnapshot,
                )

                JoinPlayerListCard(
                    players = roomSnapshot.players,
                )

                feedbackMessage?.let { message ->
                    FeedbackText(
                        message = message,
                    )
                }

                if (
                    roomSnapshot.status == OnlineRoomStatusDto.WAITING_FOR_PLAYERS &&
                    allowFakePlayerCompletion
                ) {
                    PrimaryMenuButton(
                        text = "Completar mesa com fakes",
                        onClick = onCompleteWithFakePlayersClick,
                    )
                } else if (roomSnapshot.status == OnlineRoomStatusDto.WAITING_FOR_PLAYERS) {
                    Text(
                        text = "Aguardando jogadores.",
                        color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = "Abrindo partida online...",
                        color = DominoSemanticColors.playableMove,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            JoinRoomFormCard(
                roomCodeInput = roomCodeInput,
                onRoomCodeChange = onRoomCodeChange,
            )

            feedbackMessage?.let { message ->
                FeedbackText(
                    message = message,
                )
            }

            PrimaryMenuButton(
                text = "Entrar na sala",
                onClick = onJoinClick,
            )

            if (allowDemoRoomCreation) {
                SecondaryMenuButton(
                    text = "Criar sala fake de teste",
                    onClick = onCreateDemoRoomClick,
                )
            }
        }

        SecondaryMenuButton(
            text = "Voltar",
            onClick = onBackClick,
        )
    }
}

@Composable
private fun JoinRoomFormCard(
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = DominoSemanticColors.primarySurface.copy(alpha = 0.96f),
            contentColor = DominoSemanticColors.primaryTextOnLight,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Código da sala",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = DominoSemanticColors.primaryTextOnLight,
            )

            OutlinedTextField(
                value = roomCodeInput,
                onValueChange = onRoomCodeChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(
                        text = "Ex.: 0001",
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DominoSemanticColors.primaryTextOnLight,
                    unfocusedTextColor = DominoSemanticColors.primaryTextOnLight,
                    focusedBorderColor = DominoColorTokens.PernambucoBlue,
                    unfocusedBorderColor = DominoColorTokens.PernambucoBlue.copy(alpha = 0.48f),
                    focusedLabelColor = DominoColorTokens.PernambucoBlue,
                    unfocusedLabelColor = DominoSemanticColors.primaryTextOnLight.copy(alpha = 0.66f),
                    cursorColor = DominoColorTokens.PernambucoBlue,
                ),
            )

            Text(
                text = "No fake repository, a sala precisa existir nesta execução do app. Use a sala fake de teste para validar o caminho.",
                style = MaterialTheme.typography.bodySmall,
                color = DominoSemanticColors.primaryTextOnLight.copy(alpha = 0.64f),
            )
        }
    }
}

@Composable
private fun JoinRoomCodeCard(
    roomSnapshot: OnlineRoomSnapshotDto,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = DominoSemanticColors.primarySurface.copy(alpha = 0.96f),
            contentColor = DominoSemanticColors.primaryTextOnLight,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 16.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Código da sala",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = DominoSemanticColors.primaryTextOnLight.copy(alpha = 0.66f),
            )

            Text(
                text = roomSnapshot.roomCode,
                fontSize = 42.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Black,
                color = DominoColorTokens.PernambucoBlue,
            )

            Text(
                text = joinRoomStatusLabel(
                    status = roomSnapshot.status,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = DominoSemanticColors.primaryTextOnLight.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun JoinPlayerListCard(
    players: List<OnlineRoomPlayerDto>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = DominoColorTokens.PureWhite.copy(alpha = 0.16f),
            contentColor = DominoSemanticColors.primaryTextOnDark,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 14.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Jogadores ${players.size}/4",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = DominoSemanticColors.primaryTextOnDark,
            )

            repeat(4) { seatIndex ->
                val player = players.firstOrNull { roomPlayer ->
                    roomPlayer.seatIndex == seatIndex
                }

                JoinPlayerSlotRow(
                    seatIndex = seatIndex,
                    player = player,
                )
            }
        }
    }
}

@Composable
private fun JoinPlayerSlotRow(
    seatIndex: Int,
    player: OnlineRoomPlayerDto?,
) {
    val isOccupied = player != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Lugar ${seatIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.58f),
            )

            Text(
                text = player?.name ?: "Aguardando jogador",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isOccupied) {
                    FontWeight.Black
                } else {
                    FontWeight.Normal
                },
                color = if (isOccupied) {
                    DominoSemanticColors.primaryTextOnDark
                } else {
                    DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.58f)
                },
            )
        }

        Text(
            text = if (player?.connected == true) {
                "online"
            } else {
                "livre"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (player?.connected == true) {
                DominoSemanticColors.playableMove
            } else {
                DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.48f)
            },
        )
    }
}

@Composable
private fun FeedbackText(
    message: String,
) {
    Text(
        text = message,
        color = DominoSemanticColors.warningImpact,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

private fun findLocalSeatIndexForJoin(
    roomSnapshot: OnlineRoomSnapshotDto,
    localPlayerId: String,
): Int? {
    return roomSnapshot.players.firstOrNull { player ->
        player.playerId == localPlayerId
    }?.seatIndex
}

private fun resolveNextFakePlayerNumber(
    players: List<OnlineRoomPlayerDto>,
    preferredNumber: Int,
): Int {
    var candidate = preferredNumber

    while (
        players.any { player ->
            player.playerId == createDebugFakeOnlinePlayerId(candidate)
        }
    ) {
        candidate += 1
    }

    return candidate
}

private fun joinRoomStatusLabel(
    status: OnlineRoomStatusDto,
): String {
    return when (status) {
        OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
            "Aguardando jogadores"
        }

        OnlineRoomStatusDto.IN_MATCH -> {
            "Partida criada"
        }

        OnlineRoomStatusDto.FINISHED -> {
            "Partida finalizada"
        }

        OnlineRoomStatusDto.CLOSED -> {
            "Sala encerrada"
        }
    }
}