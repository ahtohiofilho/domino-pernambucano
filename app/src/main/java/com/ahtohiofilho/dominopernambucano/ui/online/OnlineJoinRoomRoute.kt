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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDebugOptions
import com.ahtohiofilho.dominopernambucano.online.OnlineDevelopmentParticipantCompletion
import com.ahtohiofilho.dominopernambucano.online.OnlineDevelopmentParticipantRequest
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.createDebugFakeOnlinePlayerId
import com.ahtohiofilho.dominopernambucano.online.createDebugFakeOnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.resolvedDisplayName
import com.ahtohiofilho.dominopernambucano.online.createDebugHostOnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlinx.coroutines.launch

@Composable
fun OnlineJoinRoomRoute(
    roomRepository: OnlineRoomRepository,
    localPlayerIdentity: OnlinePlayerIdentity,
    debugOptions: OnlineDebugOptions,
    traceLogger: OnlineTraceLogger,
    onStartOnlineMatch: (OnlineDominoMatchCoordinator) -> Unit,
    onBackClick: () -> Unit,
) {
    val roomSnapshot by roomRepository.roomSnapshot.collectAsState()
    val matchSnapshot by roomRepository.matchSnapshot.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val developmentParticipantCompletion =
        roomRepository as? OnlineDevelopmentParticipantCompletion

    val allowApplicationParticipantCompletion =
        debugOptions.allowFakePlayerCompletion &&
                developmentParticipantCompletion != null

    val localPlayerId = localPlayerIdentity.playerId
    val localPlayerName = localPlayerIdentity.displayName

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

    var participationPlayerId by remember {
        mutableStateOf<String?>(null)
    }

    var nextFakePlayerNumber by remember {
        mutableIntStateOf(2)
    }

    val roomCodeRequiredMessage =
        stringResource(R.string.private_room_code_required)

    val joinFailedMessage =
        stringResource(R.string.online_room_join_failed)
    val completeFailedMessage =
        stringResource(R.string.online_room_complete_failed)
    val seatChangeFailedMessage =
        stringResource(R.string.private_room_seat_change_failed)
    val startPrivateRoomFailedMessage =
        stringResource(R.string.private_room_start_failed)

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
        participationPlayerId,
    ) {
        val currentRoomSnapshot = roomSnapshot
        val currentMatchSnapshot = matchSnapshot
        val joinedPlayerId = participationPlayerId
            ?: return@LaunchedEffect

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
            localPlayerId = joinedPlayerId,
        ) ?: return@LaunchedEffect

        hasOpenedMatch = true

        val matchCoordinator = OnlineDominoMatchCoordinator(
            repository = roomRepository,
            roomId = currentMatchSnapshot.roomId,
            matchId = currentMatchSnapshot.matchId,
            localPlayerId = joinedPlayerId,
            localPlayerIndex = localSeatIndex,
            initialSnapshot = currentMatchSnapshot,
            traceLogger = traceLogger,
        )

        onStartOnlineMatch(matchCoordinator)
    }

    OnlineJoinRoomScreen(
        roomSnapshot = roomSnapshot,
        roomCodeInput = roomCodeInput,
        hasJoinedRoom = hasJoinedRoom,
        feedbackMessage = feedbackMessage,
        localPlayerId = participationPlayerId,
        allowDemoRoomCreation = debugOptions.allowDemoRoomCreation,
        allowFakePlayerCompletion =
            allowApplicationParticipantCompletion,
        onRoomCodeChange = { value ->
            roomCodeInput = value
                .filter { char -> char.isLetterOrDigit() }
                .uppercase()
                .take(8)
        },
        onCreateDemoRoomClick = {
            if (!debugOptions.allowDemoRoomCreation) {
                feedbackMessage =
                    "Criação de sala fake está desabilitada neste ambiente."
                return@OnlineJoinRoomScreen
            }

            coroutineScope.launch {
                val debugHostIdentity = createDebugHostOnlinePlayerIdentity()

                val result = roomRepository.createRoom(
                    CreateOnlineRoomRequestDto(
                        localPlayerId = debugHostIdentity.playerId,
                        playerName = debugHostIdentity.displayName,
                    ),
                )

                if (result.accepted) {
                    roomCodeInput = result.roomSnapshot?.roomCode.orEmpty()
                    participationPlayerId = null
                    hasJoinedRoom = false
                    hasOpenedMatch = false
                    feedbackMessage =
                        "Sala fake criada. Agora entre com o código."
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
                    feedbackMessage = roomCodeRequiredMessage
                    return@launch
                }

                val result = roomRepository.joinRoom(
                    JoinOnlineRoomRequestDto(
                        roomCode = normalizedRoomCode,
                        localPlayerId = localPlayerId,
                        playerName = localPlayerName,
                    ),
                )

                if (result.accepted) {
                    participationPlayerId = result.localSeatIndex
                        ?.let { seatIndex ->
                            result.roomSnapshot
                                ?.players
                                ?.firstOrNull { player ->
                                    player.seatIndex == seatIndex
                                }
                                ?.playerId
                        }
                        ?: localPlayerId

                    hasJoinedRoom = true
                    feedbackMessage = null
                } else {
                    feedbackMessage = result.reason
                        ?: joinFailedMessage
                }
            }
        },
        onSeatClick = { targetSeatIndex ->
            coroutineScope.launch {
                val result = roomRepository.movePrivateRoomSeat(
                    targetSeatIndex = targetSeatIndex,
                )
                feedbackMessage = if (result.accepted) {
                    null
                } else {
                    result.reason ?: seatChangeFailedMessage
                }
            }
        },
        onStartPrivateRoomClick = {
            coroutineScope.launch {
                val result = roomRepository.startPrivateRoom()
                feedbackMessage = if (result.accepted) {
                    null
                } else {
                    result.reason ?: startPrivateRoomFailedMessage
                }
            }
        },
        onCompleteWithFakePlayersClick = {
            val participantCompletion =
                developmentParticipantCompletion

            if (
                !debugOptions.allowFakePlayerCompletion ||
                participantCompletion == null
            ) {
                feedbackMessage =
                    "Completar a mesa com jogadores controlados pelo aplicativo " +
                            "está desabilitado neste ambiente."
                return@OnlineJoinRoomScreen
            }

            coroutineScope.launch {
                var workingSnapshot = roomRepository.roomSnapshot.value
                    ?: return@launch

                while (
                    workingSnapshot.status ==
                    OnlineRoomStatusDto.WAITING_FOR_PLAYERS &&
                    workingSnapshot.players.size < 4
                ) {
                    val fakePlayerNumber = resolveNextFakePlayerNumber(
                        players = workingSnapshot.players,
                        preferredNumber = nextFakePlayerNumber,
                    )

                    val fakePlayerIdentity = createDebugFakeOnlinePlayerIdentity(
                        fakePlayerNumber = fakePlayerNumber,
                    )

                    val result =
                        participantCompletion.addApplicationParticipant(
                            OnlineDevelopmentParticipantRequest(
                                roomCode = workingSnapshot.roomCode,
                                playerId = fakePlayerIdentity.playerId,
                                playerName = fakePlayerIdentity.displayName,
                            ),
                        )

                    if (!result.accepted) {
                        feedbackMessage = result.reason
                            ?: completeFailedMessage
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
    localPlayerId: String?,
    allowDemoRoomCreation: Boolean,
    allowFakePlayerCompletion: Boolean,
    onRoomCodeChange: (String) -> Unit,
    onCreateDemoRoomClick: () -> Unit,
    onJoinClick: () -> Unit,
    onSeatClick: (Int) -> Unit,
    onStartPrivateRoomClick: () -> Unit,
    onCompleteWithFakePlayersClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    DominoScreenScaffold(
        title = if (hasJoinedRoom) {
            stringResource(R.string.private_room_title)
        } else {
            stringResource(R.string.private_room_join_action)
        },
        layout = DominoScreenLayout.Top,
        onBackClick = onBackClick,
        backContentDescription = stringResource(R.string.common_back),
    ) {

        Text(
            text = if (hasJoinedRoom) {
                if (allowFakePlayerCompletion) {
                    "Você entrou na sala. Complete a mesa com jogadores controlados " +
                            "pelo aplicativo para validar o fluxo ponta a ponta."
                } else {
                    if (
                        roomSnapshot?.let(::isPrivateRoomFullAndReady) == true
                    ) {
                        stringResource(R.string.private_room_waiting_host_start)
                    } else {
                        stringResource(R.string.online_room_joined_waiting)
                    }
                }
            } else {
                if (allowDemoRoomCreation) {
                    "Fluxo fake em memória para validar entrada em sala antes do backend real."
                } else {
                    stringResource(R.string.online_room_enter_code_hint)
                }
            },
            color = DominoSemanticColors.primaryTextOnDark.copy(
                alpha = 0.78f,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        if (hasJoinedRoom) {
            if (roomSnapshot == null) {
                CircularProgressIndicator(
                    color = DominoSemanticColors.primaryTextOnDark,
                )

                Text(
                    text = stringResource(R.string.private_room_loading),
                    color = DominoSemanticColors.primaryTextOnDark.copy(
                        alpha = 0.72f,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                JoinRoomCodeCard(
                    roomSnapshot = roomSnapshot,
                )

                PrivateRoomSeatListCard(
                    roomSnapshot = roomSnapshot,
                    localPlayerId = localPlayerId,
                    onSeatClick = onSeatClick,
                )

                feedbackMessage?.let { message ->
                    FeedbackText(
                        message = message,
                    )
                }

                PrivateRoomWaitingActions(
                    roomSnapshot = roomSnapshot,
                    localPlayerId = localPlayerId,
                    allowAutomaticPlayerCompletion = false,
                    onCompleteWithFakePlayersClick =
                        onCompleteWithFakePlayersClick,
                    onStartPrivateRoomClick =
                        onStartPrivateRoomClick,
                )
            }
        } else {
            JoinRoomFormCard(
                roomCodeInput = roomCodeInput,
                isFakeBackend = allowDemoRoomCreation,
                onRoomCodeChange = onRoomCodeChange,
            )

            feedbackMessage?.let { message ->
                FeedbackText(
                    message = message,
                )
            }

            PrimaryMenuButton(
                text = stringResource(R.string.private_room_join_action),
                onClick = onJoinClick,
            )

            if (allowDemoRoomCreation) {
                SecondaryMenuButton(
                    text = stringResource(R.string.online_join_create_demo_room),
                    onClick = onCreateDemoRoomClick,
                )
            }
        }


    }
}

@Composable
private fun JoinRoomFormCard(
    roomCodeInput: String,
    isFakeBackend: Boolean,
    onRoomCodeChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = DominoSemanticColors.primarySurface.copy(
                alpha = 0.96f,
            ),
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
                text = stringResource(R.string.private_room_code_label),
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
                        text = stringResource(R.string.private_room_code_example),
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor =
                        DominoSemanticColors.primaryTextOnLight,
                    unfocusedTextColor =
                        DominoSemanticColors.primaryTextOnLight,
                    focusedBorderColor = DominoColorTokens.PernambucoBlue,
                    unfocusedBorderColor =
                        DominoColorTokens.PernambucoBlue.copy(
                            alpha = 0.48f,
                        ),
                    focusedLabelColor = DominoColorTokens.PernambucoBlue,
                    unfocusedLabelColor =
                        DominoSemanticColors.primaryTextOnLight.copy(
                            alpha = 0.66f,
                        ),
                    cursorColor = DominoColorTokens.PernambucoBlue,
                ),
            )

            Text(
                text = if (isFakeBackend) {
                    "No ambiente local, a sala precisa existir nesta " +
                            "execução do aplicativo. Use a sala de teste " +
                            "para validar o caminho."
                } else {
                    stringResource(R.string.online_room_shared_code_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = DominoSemanticColors.primaryTextOnLight.copy(
                    alpha = 0.64f,
                ),
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
            containerColor = DominoSemanticColors.primarySurface.copy(
                alpha = 0.96f,
            ),
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
                text = stringResource(R.string.private_room_code_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = DominoSemanticColors.primaryTextOnLight.copy(
                    alpha = 0.66f,
                ),
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
                    roomSnapshot = roomSnapshot,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = DominoSemanticColors.primaryTextOnLight.copy(
                    alpha = 0.78f,
                ),
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
            containerColor = DominoColorTokens.PureWhite.copy(
                alpha = 0.16f,
            ),
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
                text = stringResource(
                    R.string.private_room_player_count,
                    players.size,
                ),
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
                text = stringResource(
                    R.string.private_room_seat_number,
                    seatIndex + 1,
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DominoSemanticColors.primaryTextOnDark.copy(
                    alpha = 0.58f,
                ),
            )

            Text(
                text = player?.resolvedDisplayName
                    ?: stringResource(R.string.online_waiting_player),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isOccupied) {
                    FontWeight.Black
                } else {
                    FontWeight.Normal
                },
                color = if (isOccupied) {
                    DominoSemanticColors.primaryTextOnDark
                } else {
                    DominoSemanticColors.primaryTextOnDark.copy(
                        alpha = 0.58f,
                    )
                },
            )

            if (player != null) {
                OnlineParticipantTypeLabel(
                    participantType = player.participantType,
                )
            }
        }

        Text(
            text = if (player?.connected == true) {
                "online"
            } else {
                stringResource(R.string.online_seat_available)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (player?.connected == true) {
                DominoSemanticColors.playableMove
            } else {
                DominoSemanticColors.primaryTextOnDark.copy(
                    alpha = 0.48f,
                )
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

@Composable
private fun joinRoomStatusLabel(
    roomSnapshot: OnlineRoomSnapshotDto,
): String {
    return when (roomSnapshot.status) {
        OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
            if (isPrivateRoomFullAndReady(roomSnapshot)) {
                stringResource(R.string.private_room_complete_status)
            } else {
                stringResource(R.string.online_waiting_players)
            }
        }

        OnlineRoomStatusDto.IN_MATCH -> {
            stringResource(R.string.online_match_created)
        }

        OnlineRoomStatusDto.FINISHED -> {
            stringResource(R.string.online_match_finished)
        }

        OnlineRoomStatusDto.CLOSED -> {
            stringResource(R.string.online_room_closed)
        }
    }
}
