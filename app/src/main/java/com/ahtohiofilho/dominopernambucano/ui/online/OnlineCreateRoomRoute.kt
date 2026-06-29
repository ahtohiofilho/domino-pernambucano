package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDebugOptions
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationStore
import com.ahtohiofilho.dominopernambucano.online.createOnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.createDebugFakeOnlinePlayerId
import com.ahtohiofilho.dominopernambucano.online.createDebugFakeOnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.ui.menu.MenuScaffold
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlinx.coroutines.launch

@Composable
fun OnlineCreateRoomRoute(
    roomRepository: OnlineRoomRepository,
    localPlayerIdentity: OnlinePlayerIdentity,
    participationStore: OnlineParticipationStore,
    participationBackendScope: String,
    debugOptions: OnlineDebugOptions,
    traceLogger: OnlineTraceLogger,
    onStartOnlineMatch: (OnlineDominoMatchCoordinator) -> Unit,
    onBackClick: () -> Unit,
) {
    val roomSnapshot by roomRepository.roomSnapshot.collectAsState()
    val matchSnapshot by roomRepository.matchSnapshot.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var resolvedLocalPlayerIdentity by remember(
        roomRepository,
        localPlayerIdentity,
    ) {
        mutableStateOf<OnlinePlayerIdentity?>(null)
    }

    val localPlayerId = resolvedLocalPlayerIdentity?.playerId
    val localPlayerName = resolvedLocalPlayerIdentity?.playerName

    var feedbackMessage by remember {
        mutableStateOf<String?>(null)
    }

    var nextFakePlayerNumber by remember {
        mutableIntStateOf(2)
    }

    var hasOpenedMatch by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        roomRepository,
        localPlayerIdentity,
    ) {
        resolvedLocalPlayerIdentity = null
        feedbackMessage = "Conectando sua sessão online..."

        resolvedLocalPlayerIdentity = runCatching {
            roomRepository.resolveLocalPlayerIdentity(
                identity = localPlayerIdentity,
            )
        }.getOrElse { error ->
            feedbackMessage = error.message
                ?: "Não foi possível iniciar sua sessão online."
            null
        }
    }

    LaunchedEffect(
        roomRepository,
        localPlayerId,
        localPlayerName,
    ) {
        val resolvedPlayerId = localPlayerId ?: return@LaunchedEffect
        val resolvedPlayerName = localPlayerName ?: return@LaunchedEffect

        val result = roomRepository.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = resolvedPlayerId,
                playerName = resolvedPlayerName,
            )
        )

        if (result.accepted) {
            val room = result.roomSnapshot
            val localSeatIndex = result.localSeatIndex

            if (room != null && localSeatIndex != null) {
                createOnlineParticipationBinding(
                    backendScope = participationBackendScope,
                    roomSnapshot = room,
                    playerId = resolvedPlayerId,
                    seatIndex = localSeatIndex,
                )?.let { binding ->
                    participationStore.write(binding)
                }
            }
        }

        feedbackMessage = if (result.accepted) {
            null
        } else {
            result.reason ?: "Não foi possível criar a sala."
        }
    }

    LaunchedEffect(
        roomSnapshot,
        matchSnapshot,
        hasOpenedMatch,
    ) {
        val currentRoomSnapshot = roomSnapshot
        val currentMatchSnapshot = matchSnapshot

        val resolvedPlayerId = localPlayerId

        if (
            currentRoomSnapshot == null ||
            currentMatchSnapshot == null ||
            resolvedPlayerId == null ||
            hasOpenedMatch
        ) {
            return@LaunchedEffect
        }

        val localSeatIndex = findLocalSeatIndex(
            roomSnapshot = currentRoomSnapshot,
            localPlayerId = resolvedPlayerId,
        ) ?: return@LaunchedEffect

        hasOpenedMatch = true

        val matchCoordinator = OnlineDominoMatchCoordinator(
            repository = roomRepository,
            roomId = currentMatchSnapshot.roomId,
            matchId = currentMatchSnapshot.matchId,
            localPlayerId = resolvedPlayerId,
            localPlayerIndex = localSeatIndex,
            initialSnapshot = currentMatchSnapshot,
            traceLogger = traceLogger,
        )

        onStartOnlineMatch(matchCoordinator)
    }

    OnlineLobbyScreen(
        roomSnapshot = roomSnapshot,
        matchRevision = matchSnapshot?.revision,
        feedbackMessage = feedbackMessage,
        allowFakePlayerCompletion = debugOptions.allowFakePlayerCompletion,
        onCompleteWithFakePlayersClick = {
            if (!debugOptions.allowFakePlayerCompletion) {
                feedbackMessage = "Completar mesa com fakes está desabilitado neste ambiente."
                return@OnlineLobbyScreen
            }

            val snapshot = roomSnapshot ?: return@OnlineLobbyScreen

            coroutineScope.launch {
                var workingSnapshot = snapshot

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
                roomRepository.leaveRoom()
                participationStore.clear()
                onBackClick()
            }
        },
    )
}

@Composable
internal fun OnlineLobbyScreen(
    roomSnapshot: OnlineRoomSnapshotDto?,
    matchRevision: Long?,
    feedbackMessage: String?,
    allowFakePlayerCompletion: Boolean,
    onCompleteWithFakePlayersClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = "Sala online",
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Lobby fake em memória para validar criação de sala, entrada de jogadores e abertura automática da partida.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        if (roomSnapshot == null) {
            CircularProgressIndicator(
                color = DominoSemanticColors.primaryTextOnDark,
            )

            Text(
                text = "Criando sala.",
                color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        } else {
            RoomCodeCard(
                roomSnapshot = roomSnapshot,
                matchRevision = matchRevision,
            )

            PlayerListCard(
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
                    text = "Abrindo partida online.",
                    color = DominoSemanticColors.playableMove,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
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
private fun RoomCodeCard(
    roomSnapshot: OnlineRoomSnapshotDto,
    matchRevision: Long?,
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
                text = roomStatusLabel(
                    status = roomSnapshot.status,
                    matchRevision = matchRevision,
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
private fun PlayerListCard(
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

                PlayerSlotRow(
                    seatIndex = seatIndex,
                    player = player,
                )
            }
        }
    }
}

@Composable
private fun PlayerSlotRow(
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

private fun findLocalSeatIndex(
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

private fun roomStatusLabel(
    status: OnlineRoomStatusDto,
    matchRevision: Long?,
): String {
    return when (status) {
        OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
            "Aguardando jogadores"
        }

        OnlineRoomStatusDto.IN_MATCH -> {
            val revisionText = matchRevision?.let { revision ->
                " · snapshot $revision"
            }.orEmpty()

            "Partida criada$revisionText"
        }

        OnlineRoomStatusDto.FINISHED -> {
            "Partida finalizada"
        }

        OnlineRoomStatusDto.CLOSED -> {
            "Sala encerrada"
        }
    }
}