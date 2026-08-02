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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.createDebugFakeOnlinePlayerId
import com.ahtohiofilho.dominopernambucano.online.createDebugFakeOnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.resolvedDisplayName
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
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
    resumedParticipationBinding: OnlineParticipationBinding? = null,
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


    val resumeLoadFailedMessage =
        stringResource(R.string.online_room_resume_load_failed)
    val resumeMismatchMessage =
        stringResource(R.string.online_room_resume_mismatch)
    val seatMismatchMessage =
        stringResource(R.string.online_room_seat_mismatch)
    val createFailedMessage =
        stringResource(R.string.online_room_create_failed)
    val completeFailedMessage =
        stringResource(R.string.online_room_complete_failed)
    val fillAppPlayersDisabledMessage =
        stringResource(R.string.room_fill_app_players_disabled)

    var feedbackMessage by remember(
        resumedParticipationBinding,
    ) {
        mutableStateOf<String?>(null)
    }

    var nextFakePlayerNumber by remember(
        resumedParticipationBinding,
    ) {
        mutableIntStateOf(2)
    }

    var hasOpenedMatch by remember(
        resumedParticipationBinding,
    ) {
        mutableStateOf(false)
    }

    var participationPlayerId by remember(
        resumedParticipationBinding,
        localPlayerId,
    ) {
        mutableStateOf(
            resumedParticipationBinding?.playerId ?: localPlayerId,
        )
    }

    LaunchedEffect(
        roomRepository,
        localPlayerId,
        localPlayerName,
        resumedParticipationBinding,
    ) {
        val resumedBinding = resumedParticipationBinding

        if (resumedBinding != null) {
            participationPlayerId = resumedBinding.playerId

            val resumedRoomSnapshot = roomRepository.roomSnapshot.value

            feedbackMessage = when {
                resumedRoomSnapshot == null -> {
                    resumeLoadFailedMessage
                }

                resumedRoomSnapshot.roomId != resumedBinding.roomId -> {
                    resumeMismatchMessage
                }

                findLocalSeatIndex(
                    roomSnapshot = resumedRoomSnapshot,
                    localPlayerId = resumedBinding.playerId,
                ) != resumedBinding.localSeatIndex -> {
                    seatMismatchMessage
                }

                else -> null
            }

            return@LaunchedEffect
        }

        val result = roomRepository.createRoom(
            CreateOnlineRoomRequestDto(
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

            feedbackMessage = null
        } else {
            feedbackMessage = result.reason
                ?: createFailedMessage
        }
    }

    LaunchedEffect(
        roomSnapshot,
        matchSnapshot,
        hasOpenedMatch,
        participationPlayerId,
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
            localPlayerId = participationPlayerId,
        ) ?: return@LaunchedEffect

        hasOpenedMatch = true

        val matchCoordinator = OnlineDominoMatchCoordinator(
            repository = roomRepository,
            roomId = currentMatchSnapshot.roomId,
            matchId = currentMatchSnapshot.matchId,
            localPlayerId = participationPlayerId,
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
        isFakeBackend = debugOptions.allowDemoRoomCreation,
        allowFakePlayerCompletion =
            allowApplicationParticipantCompletion,
        onCompleteWithFakePlayersClick = {
            val participantCompletion =
                developmentParticipantCompletion

            if (
                !debugOptions.allowFakePlayerCompletion ||
                participantCompletion == null
            ) {
                feedbackMessage =
                    fillAppPlayersDisabledMessage
                return@OnlineLobbyScreen
            }

            val snapshot = roomSnapshot ?: return@OnlineLobbyScreen

            coroutineScope.launch {
                var workingSnapshot = snapshot

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
    isFakeBackend: Boolean,
    allowFakePlayerCompletion: Boolean,
    onCompleteWithFakePlayersClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = stringResource(R.string.private_room_title),
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = when {
                isFakeBackend -> {
                    "Ambiente local de desenvolvimento para validar criação " +
                            "de sala, entrada de jogadores e abertura " +
                            "automática da partida."
                }

                allowFakePlayerCompletion -> {
                    "Sala conectada ao backend online remoto. Você pode " +
                            "aguardar outros jogadores ou completar a mesa " +
                            "com jogadores controlados pelo aplicativo."
                }

                else -> {
                    stringResource(R.string.online_room_waiting_others)
                }
            },
            color = DominoSemanticColors.primaryTextOnDark.copy(
                alpha = 0.78f,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        if (roomSnapshot == null) {
            CircularProgressIndicator(
                color = DominoSemanticColors.primaryTextOnDark,
            )

            Text(
                text = stringResource(R.string.private_room_creating),
                color = DominoSemanticColors.primaryTextOnDark.copy(
                    alpha = 0.72f,
                ),
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
                roomSnapshot.status ==
                OnlineRoomStatusDto.WAITING_FOR_PLAYERS &&
                allowFakePlayerCompletion
            ) {
                PrimaryMenuButton(
                    text = stringResource(R.string.private_room_complete_table),
                    onClick = onCompleteWithFakePlayersClick,
                )
            } else if (
                roomSnapshot.status ==
                OnlineRoomStatusDto.WAITING_FOR_PLAYERS
            ) {
                Text(
                    text = stringResource(R.string.private_room_waiting_players),
                    color = DominoSemanticColors.primaryTextOnDark.copy(
                        alpha = 0.72f,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = stringResource(R.string.private_room_opening_match),
                    color = DominoSemanticColors.playableMove,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }

        SecondaryMenuButton(
            text = stringResource(R.string.common_back),
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
                text = roomStatusLabel(
                    status = roomSnapshot.status,
                    matchRevision = matchRevision,
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
private fun PlayerListCard(
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

@Composable
private fun roomStatusLabel(
    status: OnlineRoomStatusDto,
    matchRevision: Long?,
): String {
    return when (status) {
        OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
            stringResource(R.string.online_waiting_players)
        }

        OnlineRoomStatusDto.IN_MATCH -> {
            val revisionText = matchRevision?.let { revision ->
                stringResource(
                    R.string.online_match_revision_suffix_format,
                    revision,
                )
            }.orEmpty()

            stringResource(R.string.online_match_created) + revisionText
        }

        OnlineRoomStatusDto.FINISHED -> {
            stringResource(R.string.online_match_finished)
        }

        OnlineRoomStatusDto.CLOSED -> {
            stringResource(R.string.online_room_closed)
        }
    }
}
