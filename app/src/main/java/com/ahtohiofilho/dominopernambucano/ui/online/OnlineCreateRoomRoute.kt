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
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.ui.menu.MenuScaffold
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlinx.coroutines.launch

@Composable
fun OnlineCreateRoomRoute(
    roomRepository: OnlineRoomRepository,
    onStartOnlineMatch: (OnlineDominoMatchCoordinator) -> Unit,
    onBackClick: () -> Unit,
) {
    val roomSnapshot by roomRepository.roomSnapshot.collectAsState()
    val matchSnapshot by roomRepository.matchSnapshot.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val localPlayerId = remember {
        "local-player"
    }

    val localPlayerName = remember {
        "Você"
    }

    var feedbackMessage by remember {
        mutableStateOf<String?>(null)
    }

    var nextFakePlayerNumber by remember {
        mutableIntStateOf(2)
    }

    var hasOpenedMatch by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(roomRepository) {
        val result = roomRepository.createRoom(
            CreateOnlineRoomRequestDto(
                localPlayerId = localPlayerId,
                playerName = localPlayerName,
            )
        )

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

        if (
            currentRoomSnapshot == null ||
            currentMatchSnapshot == null ||
            hasOpenedMatch
        ) {
            return@LaunchedEffect
        }

        val localSeatIndex = findLocalSeatIndex(
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

    OnlineLobbyScreen(
        roomSnapshot = roomSnapshot,
        matchRevision = matchSnapshot?.revision,
        feedbackMessage = feedbackMessage,
        onAddFakePlayerClick = {
            val snapshot = roomSnapshot ?: return@OnlineLobbyScreen

            coroutineScope.launch {
                val fakePlayerNumber = nextFakePlayerNumber

                val result = roomRepository.joinRoom(
                    JoinOnlineRoomRequestDto(
                        roomCode = snapshot.roomCode,
                        localPlayerId = "fake-player-$fakePlayerNumber",
                        playerName = "Jogador $fakePlayerNumber",
                    )
                )

                if (result.accepted) {
                    feedbackMessage = null
                    nextFakePlayerNumber += 1
                } else {
                    feedbackMessage = result.reason
                        ?: "Não foi possível adicionar o jogador fake."
                }
            }
        },
        onBackClick = {
            coroutineScope.launch {
                roomRepository.leaveRoom()
                onBackClick()
            }
        },
    )
}

@Composable
private fun OnlineLobbyScreen(
    roomSnapshot: OnlineRoomSnapshotDto?,
    matchRevision: Long?,
    feedbackMessage: String?,
    onAddFakePlayerClick: () -> Unit,
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
            text = "Lobby fake em memória para validar o fluxo antes do backend real.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        if (roomSnapshot == null) {
            CircularProgressIndicator(
                color = DominoSemanticColors.primaryTextOnDark,
            )

            Text(
                text = "Criando sala...",
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
                Text(
                    text = message,
                    color = DominoSemanticColors.warningImpact,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            if (roomSnapshot.status == OnlineRoomStatusDto.WAITING_FOR_PLAYERS) {
                SecondaryMenuButton(
                    text = "Simular entrada de jogador",
                    onClick = onAddFakePlayerClick,
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

private fun findLocalSeatIndex(
    roomSnapshot: OnlineRoomSnapshotDto,
    localPlayerId: String,
): Int? {
    return roomSnapshot.players.firstOrNull { player ->
        player.playerId == localPlayerId
    }?.seatIndex
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
            if (matchRevision != null) {
                "Partida criada · revisão $matchRevision"
            } else {
                "Partida criada"
            }
        }

        OnlineRoomStatusDto.FINISHED -> {
            "Partida finalizada"
        }

        OnlineRoomStatusDto.CLOSED -> {
            "Sala encerrada"
        }
    }
}