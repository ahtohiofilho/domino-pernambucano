package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnlineDominoMatchCoordinator(
    private val repository: OnlineRoomRepository,
    private val roomId: String,
    private val matchId: String,
    private val localPlayerId: String,
    private val localPlayerIndex: Int,
    initialSnapshot: OnlineMatchSnapshotDto,
) : DominoMatchCoordinator {
    private val coordinatorScope = CoroutineScope(
        SupervisorJob(),
    )

    private var latestRevision: Long = initialSnapshot.revision

    private val mutableState = MutableStateFlow(
        initialSnapshot.toRuntimeState(
            localPlayerIndex = localPlayerIndex,
        )
    )

    override val state: StateFlow<DominoMatchRuntimeState> =
        mutableState.asStateFlow()

    override val currentState: DominoMatchRuntimeState
        get() = mutableState.value

    init {
        coordinatorScope.launch {
            repository.matchSnapshot.collect { snapshot ->
                if (snapshot == null) {
                    return@collect
                }

                if (snapshot.roomId != roomId || snapshot.matchId != matchId) {
                    return@collect
                }

                latestRevision = snapshot.revision

                mutableState.value = snapshot.toRuntimeState(
                    localPlayerIndex = localPlayerIndex,
                )
            }
        }
    }

    override fun dispatch(
        command: DominoMatchCommand,
    ) {
        when (command) {
            is DominoMatchCommand.LocalMoveSelected -> {
                submitMove(command)
            }

            is DominoMatchCommand.TurnClockTick -> {
                /*
                 * No online, o relógio oficial deve ser autoritativo no backend.
                 * O cliente pode animar HUD, mas não deve decidir timeout sozinho.
                 */
            }

            DominoMatchCommand.RoundIntroFinished,
            DominoMatchCommand.BotDecisionReady,
            DominoMatchCommand.PresentationFinished -> {
                /*
                 * No online, essas fases devem ser consumidas como apresentação local.
                 * A evolução oficial da partida virá por snapshot remoto.
                 */
            }

            DominoMatchCommand.StartNextRound,
            DominoMatchCommand.StartNewMatch -> {
                requestSnapshot()
            }
        }
    }

    fun dispose() {
        coordinatorScope.cancel()
    }

    private fun submitMove(
        command: DominoMatchCommand.LocalMoveSelected,
    ) {
        val action = createOnlinePlayMoveAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
            move = command.move,
        )

        coordinatorScope.launch {
            repository.submitAction(
                action = action,
            )
        }
    }

    private fun requestSnapshot() {
        val action = createOnlineSnapshotRequestAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        coordinatorScope.launch {
            repository.submitAction(
                action = action,
            )
        }
    }
}