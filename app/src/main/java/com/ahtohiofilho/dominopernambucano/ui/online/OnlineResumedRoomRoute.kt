package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationStore
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import kotlinx.coroutines.launch

/*
 * Rota de lobby para uma participação já existente. Ela não cria sala e não
 * executa join novamente: apenas acompanha o snapshot que foi reidratado pelo
 * repositório e entra na partida quando o servidor a disponibilizar.
 */
@Composable
fun OnlineResumedRoomRoute(
    roomRepository: OnlineRoomRepository,
    participationBinding: OnlineParticipationBinding,
    participationStore: OnlineParticipationStore,
    traceLogger: OnlineTraceLogger,
    onStartOnlineMatch: (OnlineDominoMatchCoordinator) -> Unit,
    onBackClick: () -> Unit,
) {
    val roomSnapshot by roomRepository.roomSnapshot.collectAsState()
    val matchSnapshot by roomRepository.matchSnapshot.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var feedbackMessage by remember {
        mutableStateOf<String?>(null)
    }

    var hasOpenedMatch by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        roomSnapshot,
        matchSnapshot,
        participationBinding,
        hasOpenedMatch,
    ) {
        val room = roomSnapshot
        val match = matchSnapshot

        if (
            room == null ||
            match == null ||
            room.status != OnlineRoomStatusDto.IN_MATCH ||
            hasOpenedMatch
        ) {
            return@LaunchedEffect
        }

        val localPlayer = room.players.firstOrNull { player ->
            player.playerId == participationBinding.playerId
        }

        if (localPlayer?.seatIndex != participationBinding.seatIndex) {
            feedbackMessage = "Seu vínculo com esta sala não é mais válido."
            participationStore.clear()
            return@LaunchedEffect
        }

        if (
            match.roomId != room.roomId ||
            match.matchId != room.matchId
        ) {
            feedbackMessage = "A partida online ainda está sincronizando."
            return@LaunchedEffect
        }

        hasOpenedMatch = true

        onStartOnlineMatch(
            OnlineDominoMatchCoordinator(
                repository = roomRepository,
                roomId = match.roomId,
                matchId = match.matchId,
                localPlayerId = participationBinding.playerId,
                localPlayerIndex = participationBinding.seatIndex,
                initialSnapshot = match,
                traceLogger = traceLogger,
            )
        )
    }

    OnlineLobbyScreen(
        roomSnapshot = roomSnapshot,
        matchRevision = matchSnapshot?.revision,
        feedbackMessage = feedbackMessage,
        allowFakePlayerCompletion = false,
        onCompleteWithFakePlayersClick = { },
        onBackClick = {
            coroutineScope.launch {
                roomRepository.leaveRoom()
                participationStore.clear()
                onBackClick()
            }
        },
    )
}
