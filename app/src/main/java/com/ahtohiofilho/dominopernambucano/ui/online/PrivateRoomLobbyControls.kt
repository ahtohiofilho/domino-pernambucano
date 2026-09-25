package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.resolvedDisplayName
import com.ahtohiofilho.dominopernambucano.ui.menu.PrimaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.menu.SecondaryMenuButton
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

internal enum class PrivateRoomSeatUiAction {
    CURRENT,
    CHOOSE,
    SWAP,
    RELEASE_AUTOMATIC,
    NONE,
}

internal fun privateRoomSeatUiAction(
    roomSnapshot: OnlineRoomSnapshotDto,
    localPlayerId: String?,
    targetSeatIndex: Int,
): PrivateRoomSeatUiAction {
    if (
        roomSnapshot.matchMode != DominoMatchMode.PRIVATE_UNRANKED ||
        roomSnapshot.status != OnlineRoomStatusDto.WAITING_FOR_PLAYERS ||
        roomSnapshot.matchId != null ||
        localPlayerId.isNullOrBlank() ||
        targetSeatIndex !in 0..3
    ) {
        return PrivateRoomSeatUiAction.NONE
    }

    val localPlayer = roomSnapshot.players.firstOrNull { player ->
        player.playerId == localPlayerId
    } ?: return PrivateRoomSeatUiAction.NONE

    if (localPlayer.seatIndex == targetSeatIndex) {
        return PrivateRoomSeatUiAction.CURRENT
    }

    val targetPlayer = roomSnapshot.players.firstOrNull { player ->
        player.seatIndex == targetSeatIndex
    }

    if (
        roomSnapshot.hostPlayerId == localPlayerId &&
        targetPlayer?.participantType ==
            OnlineParticipantTypeDto.APPLICATION
    ) {
        return PrivateRoomSeatUiAction.RELEASE_AUTOMATIC
    }

    return if (targetPlayer != null) {
        PrivateRoomSeatUiAction.SWAP
    } else {
        PrivateRoomSeatUiAction.CHOOSE
    }
}

internal fun canCompletePrivateRoomWithAutomaticPlayers(
    roomSnapshot: OnlineRoomSnapshotDto,
    localPlayerId: String?,
): Boolean {
    return roomSnapshot.matchMode ==
            DominoMatchMode.PRIVATE_UNRANKED &&
        roomSnapshot.status ==
            OnlineRoomStatusDto.WAITING_FOR_PLAYERS &&
        roomSnapshot.matchId == null &&
        !localPlayerId.isNullOrBlank() &&
        roomSnapshot.hostPlayerId == localPlayerId &&
        roomSnapshot.players.size < 4
}

internal fun isPrivateRoomFullAndReady(
    roomSnapshot: OnlineRoomSnapshotDto,
): Boolean {
    if (
        roomSnapshot.matchMode != DominoMatchMode.PRIVATE_UNRANKED ||
        roomSnapshot.status != OnlineRoomStatusDto.WAITING_FOR_PLAYERS ||
        roomSnapshot.matchId != null ||
        roomSnapshot.players.size != 4 ||
        roomSnapshot.players.any { player -> !player.connected }
    ) {
        return false
    }

    val seats = roomSnapshot.players.mapNotNull { player ->
        player.seatIndex
    }.sorted()

    return seats == listOf(0, 1, 2, 3)
}

internal fun canStartPrivateRoomFromLobby(
    roomSnapshot: OnlineRoomSnapshotDto,
    localPlayerId: String?,
): Boolean {
    if (
        localPlayerId.isNullOrBlank() ||
        roomSnapshot.hostPlayerId != localPlayerId ||
        !isPrivateRoomFullAndReady(roomSnapshot)
    ) {
        return false
    }

    return true
}

internal fun isPrivateRoomHost(
    roomSnapshot: OnlineRoomSnapshotDto,
    localPlayerId: String?,
): Boolean {
    return !localPlayerId.isNullOrBlank() &&
        roomSnapshot.hostPlayerId == localPlayerId
}

@Composable
internal fun PrivateRoomSeatListCard(
    roomSnapshot: OnlineRoomSnapshotDto,
    localPlayerId: String?,
    onSeatClick: (Int) -> Unit,
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
                    roomSnapshot.players.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = DominoSemanticColors.primaryTextOnDark,
            )

            if (
                roomSnapshot.status ==
                    OnlineRoomStatusDto.WAITING_FOR_PLAYERS
            ) {
                Text(
                    text = stringResource(
                        R.string.private_room_seat_selection_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = DominoSemanticColors.primaryTextOnDark.copy(
                        alpha = 0.68f,
                    ),
                )
            }

            repeat(4) { seatIndex ->
                val player = roomSnapshot.players.firstOrNull { roomPlayer ->
                    roomPlayer.seatIndex == seatIndex
                }
                val action = privateRoomSeatUiAction(
                    roomSnapshot = roomSnapshot,
                    localPlayerId = localPlayerId,
                    targetSeatIndex = seatIndex,
                )

                PrivateRoomSeatRow(
                    seatIndex = seatIndex,
                    player = player,
                    action = action,
                    onClick = {
                        onSeatClick(seatIndex)
                    },
                )
            }
        }
    }
}

@Composable
private fun PrivateRoomSeatRow(
    seatIndex: Int,
    player: OnlineRoomPlayerDto?,
    action: PrivateRoomSeatUiAction,
    onClick: () -> Unit,
) {
    val actionable =
        action == PrivateRoomSeatUiAction.CHOOSE ||
            action == PrivateRoomSeatUiAction.SWAP ||
            action == PrivateRoomSeatUiAction.RELEASE_AUTOMATIC

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(
                enabled = actionable,
                onClick = onClick,
            ),
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
                fontWeight = if (player != null) {
                    FontWeight.Black
                } else {
                    FontWeight.Normal
                },
                color = if (player != null) {
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
            text = when (action) {
                PrivateRoomSeatUiAction.CURRENT ->
                    stringResource(R.string.private_room_seat_you)

                PrivateRoomSeatUiAction.CHOOSE ->
                    stringResource(R.string.private_room_seat_choose)

                PrivateRoomSeatUiAction.SWAP ->
                    stringResource(R.string.private_room_seat_swap)

                PrivateRoomSeatUiAction.RELEASE_AUTOMATIC ->
                    stringResource(R.string.private_room_seat_release)

                PrivateRoomSeatUiAction.NONE -> {
                    if (player?.connected == true) {
                        stringResource(R.string.online_seat_online)
                    } else {
                        stringResource(R.string.online_seat_available)
                    }
                }
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when (action) {
                PrivateRoomSeatUiAction.CURRENT ->
                    DominoSemanticColors.playableMove

                PrivateRoomSeatUiAction.CHOOSE,
                PrivateRoomSeatUiAction.SWAP ->
                    DominoSemanticColors.primaryTextOnDark

                PrivateRoomSeatUiAction.RELEASE_AUTOMATIC ->
                    DominoSemanticColors.warningImpact

                PrivateRoomSeatUiAction.NONE ->
                    DominoSemanticColors.primaryTextOnDark.copy(
                        alpha = 0.52f,
                    )
            },
        )
    }
}

@Composable
internal fun PrivateRoomWaitingActions(
    roomSnapshot: OnlineRoomSnapshotDto,
    localPlayerId: String?,
    allowAutomaticPlayerCompletion: Boolean,
    onCompleteWithFakePlayersClick: () -> Unit,
    onStartPrivateRoomClick: () -> Unit,
) {
    if (roomSnapshot.status != OnlineRoomStatusDto.WAITING_FOR_PLAYERS) {
        Text(
            text = stringResource(R.string.private_room_opening_match),
            color = DominoSemanticColors.playableMove,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        return
    }

    if (
        allowAutomaticPlayerCompletion &&
        roomSnapshot.players.size < 4
    ) {
        SecondaryMenuButton(
            text = stringResource(R.string.private_room_complete_table),
            onClick = onCompleteWithFakePlayersClick,
        )

        Text(
            text = stringResource(R.string.private_room_complete_hint),
            color = DominoSemanticColors.primaryTextOnDark.copy(
                alpha = 0.64f,
            ),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }

    if (isPrivateRoomHost(roomSnapshot, localPlayerId)) {
        val canStart = canStartPrivateRoomFromLobby(
            roomSnapshot = roomSnapshot,
            localPlayerId = localPlayerId,
        )

        PrimaryMenuButton(
            text = stringResource(R.string.private_room_start_match),
            onClick = onStartPrivateRoomClick,
            enabled = canStart,
        )

        if (!canStart) {
            Text(
                text = stringResource(R.string.private_room_waiting_players),
                color = DominoSemanticColors.primaryTextOnDark.copy(
                    alpha = 0.72f,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        val message = if (roomSnapshot.players.size == 4) {
            stringResource(R.string.private_room_waiting_host_start)
        } else {
            stringResource(R.string.private_room_waiting_players)
        }

        Text(
            text = message,
            color = DominoSemanticColors.primaryTextOnDark.copy(
                alpha = 0.72f,
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
