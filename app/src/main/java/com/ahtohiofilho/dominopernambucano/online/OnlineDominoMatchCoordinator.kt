package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchCommand
import com.ahtohiofilho.dominopernambucano.match.DominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        SupervisorJob() + Dispatchers.Main.immediate,
    )

    private val mutableState = MutableStateFlow(
        initialSnapshot.toRuntimeState(
            localPlayerIndex = localPlayerIndex,
        )
    )

    private var latestRevision = initialSnapshot.revision

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
            DominoMatchCommand.BotDecisionReady -> {
                requestSnapshot()
            }

            DominoMatchCommand.PresentationFinished -> {
                handlePresentationFinished()
            }

            DominoMatchCommand.StartNextRound -> {
                submitStartNextRound()
            }

            DominoMatchCommand.StartNewMatch -> {
                submitStartNewMatch()
            }
        }
    }

    fun dispose() {
        coordinatorScope.cancel()
    }

    private fun handlePresentationFinished() {
        val phase = currentState.phase

        if (
            phase is DominoMatchPhase.PresentingPass &&
            phase.playerIndex == localPlayerIndex
        ) {
            submitPassTurn()
            return
        }

        requestSnapshot()
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

        submitAction(action)
    }

    private fun submitPassTurn() {
        val action = createOnlinePassTurnAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        submitAction(action)
    }

    private fun submitStartNextRound() {
        val action = createOnlineStartNextRoundAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        submitAction(action)
    }

    private fun submitStartNewMatch() {
        val action = createOnlineStartNewMatchAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        submitAction(action)
    }

    private fun requestSnapshot() {
        val action = createOnlineSnapshotRequestAction(
            roomId = roomId,
            matchId = matchId,
            playerId = localPlayerId,
            revision = latestRevision,
        )

        submitAction(action)
    }

    private fun submitAction(
        action: OnlinePlayerActionDto,
    ) {
        coordinatorScope.launch {
            repository.submitAction(
                action = action,
            )
        }
    }
}