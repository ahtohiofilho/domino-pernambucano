package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.LocalDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingRepository
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolver
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val ENABLE_OFFLINE_CLOCK_DEBUG = false

class LocalDominoSessionCoordinator(
    private val onlineParticipationBindingRepository:
        OnlineParticipationBindingRepository? = null,
    private val onlineAnonymousSessionRepository:
        OnlineAnonymousSessionRepository? = null,
    private val onlineRoomRepository: OnlineRoomRepository? = null,
) : DominoSessionCoordinator {
    private val pendingOnlineParticipationOnInitialization =
        resolvePendingOnlineParticipation()

    private var latestMainMenuState = DominoSessionState.MainMenu(
        pendingOnlineParticipation =
            pendingOnlineParticipationOnInitialization,
    )

    private val pendingOnlineParticipationInspectionMutex = Mutex()

    private val mutableState = MutableStateFlow<DominoSessionState>(
        latestMainMenuState,
    )

    override val state: StateFlow<DominoSessionState> =
        mutableState.asStateFlow()

    override val currentState: DominoSessionState
        get() = mutableState.value

    override fun dispatch(
        command: DominoSessionCommand,
    ) {
        when (command) {
            DominoSessionCommand.OpenPlayModeSelection -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.PlayModeSelection
            }

            DominoSessionCommand.StartLocalMatch -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.LocalMatch(
                    matchCoordinator = createLocalMatchCoordinator(),
                )
            }

            is DominoSessionCommand.StartOnlineMatch -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.OnlineMatch(
                    matchCoordinator = command.matchCoordinator,
                )
            }

            DominoSessionCommand.OpenOnlineCreateRoom -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.OnlineCreateRoom
            }

            DominoSessionCommand.OpenOnlineJoinRoom -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.OnlineJoinRoom
            }

            DominoSessionCommand.BackToMainMenu -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = latestMainMenuState
            }

            DominoSessionCommand.BackToPlayModeSelection -> {
                disposeCurrentOnlineCoordinatorIfNeeded()

                mutableState.value = DominoSessionState.PlayModeSelection
            }
        }
    }

    /*
     * Esta operação só inspeciona a participação que já foi classificada
     * localmente como pronta. Ela não cria coordinator de partida, não inicia
     * polling, não consulta a partida e não muda a navegação.
     */
    suspend fun inspectPendingOnlineParticipation() {
        pendingOnlineParticipationInspectionMutex.withLock {
            val mainMenuState = mutableState.value
                as? DominoSessionState.MainMenu
                ?: return@withLock

            val readyParticipation = mainMenuState
                .pendingOnlineParticipation
                as? OnlinePendingParticipationLocalResolution
                    .ReadyForRemoteReconciliation
                ?: return@withLock

            val previousInspection = mainMenuState
                .pendingOnlineParticipationInspection

            updateMainMenuState(
                mainMenuState.copy(
                    pendingOnlineParticipationInspection =
                        OnlinePendingParticipationInspectionState
                            .InProgress,
                ),
            )

            val inspection = try {
                onlineRoomRepository?.inspectPendingParticipation(
                    binding = readyParticipation.binding,
                ) ?: OnlinePendingParticipationRemoteInspection
                    .TemporarilyUnavailable(
                        reason =
                            "Inspeção remota de participação pendente não configurada.",
                    )
            } catch (error: CancellationException) {
                updateMainMenuState(
                    mainMenuState.copy(
                        pendingOnlineParticipation =
                            resolvePendingOnlineParticipation(),
                        pendingOnlineParticipationInspection =
                            previousInspection,
                    ),
                )

                throw error
            } catch (_: Throwable) {
                OnlinePendingParticipationRemoteInspection
                    .TemporarilyUnavailable(
                        reason =
                            "Falha ao verificar participação pendente online.",
                    )
            }

            if (
                inspection is OnlinePendingParticipationRemoteInspection
                    .NoLongerRecoverable
            ) {
                onlineParticipationBindingRepository?.clearIfMatches(
                    binding = readyParticipation.binding,
                )
            }

            updateMainMenuState(
                mainMenuState.copy(
                    pendingOnlineParticipation =
                        resolvePendingOnlineParticipation(),
                    pendingOnlineParticipationInspection =
                        OnlinePendingParticipationInspectionState
                            .Completed(
                                result = inspection,
                            ),
                    pendingOnlineParticipationSessionRejection =
                        inspection.toSessionRejection(
                            binding = readyParticipation.binding,
                        ),
                ),
            )
        }
    }

    fun recordPendingOnlineParticipationRemoteSessionRejected(
        binding: OnlineParticipationBinding,
    ) {
        val mainMenuState = mutableState.value
            as? DominoSessionState.MainMenu
            ?: return

        val readyParticipation = mainMenuState
            .pendingOnlineParticipation
            as? OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation
            ?: return

        if (readyParticipation.binding != binding) {
            return
        }

        updateMainMenuState(
            mainMenuState.copy(
                pendingOnlineParticipationSessionRejection =
                    OnlinePendingParticipationSessionRejection
                        .RemoteSessionRejected(
                            binding = binding,
                        ),
            ),
        )
    }

    fun discardRemoteSessionRejectedPendingOnlineParticipation() {
        val mainMenuState = mutableState.value
            as? DominoSessionState.MainMenu
            ?: return

        val sessionRejection = mainMenuState
            .pendingOnlineParticipationSessionRejection
            as? OnlinePendingParticipationSessionRejection
                .RemoteSessionRejected
            ?: return

        val readyParticipation = mainMenuState
            .pendingOnlineParticipation
            as? OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation
            ?: return

        if (readyParticipation.binding != sessionRejection.binding) {
            return
        }

        val bindingCleared = onlineParticipationBindingRepository
            ?.clearIfMatches(
                binding = sessionRejection.binding,
            ) ?: false

        if (!bindingCleared) {
            updateMainMenuState(
                mainMenuState.copy(
                    pendingOnlineParticipation =
                        resolvePendingOnlineParticipation(),
                    pendingOnlineParticipationInspection =
                        OnlinePendingParticipationInspectionState
                            .NotRequested,
                    pendingOnlineParticipationSessionRejection =
                        OnlinePendingParticipationSessionRejection
                            .NotRejected,
                ),
            )
            return
        }

        val anonymousSession = onlineAnonymousSessionRepository
            ?.getValidSessionOrNull()

        if (anonymousSession?.playerId == sessionRejection.binding.playerId) {
            onlineAnonymousSessionRepository.clear()
        }

        updateMainMenuState(
            mainMenuState.copy(
                pendingOnlineParticipation =
                    resolvePendingOnlineParticipation(),
                pendingOnlineParticipationInspection =
                    OnlinePendingParticipationInspectionState
                        .NotRequested,
                pendingOnlineParticipationSessionRejection =
                    OnlinePendingParticipationSessionRejection
                        .NotRejected,
            ),
        )
    }

    private fun resolvePendingOnlineParticipation():
        OnlinePendingParticipationLocalResolution {
        return OnlinePendingParticipationLocalResolver(
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
            onlineAnonymousSessionRepository =
                onlineAnonymousSessionRepository,
        ).resolve()
    }

    private fun updateMainMenuState(
        mainMenuState: DominoSessionState.MainMenu,
    ) {
        latestMainMenuState = mainMenuState

        if (mutableState.value is DominoSessionState.MainMenu) {
            mutableState.value = mainMenuState
        }
    }

    private fun disposeCurrentOnlineCoordinatorIfNeeded() {
        val state = mutableState.value

        if (state is DominoSessionState.OnlineMatch) {
            state.matchCoordinator.dispose()
        }
    }
}

private fun OnlinePendingParticipationRemoteInspection
        .toSessionRejection(
    binding: OnlineParticipationBinding,
): OnlinePendingParticipationSessionRejection {
    return when (this) {
        OnlinePendingParticipationRemoteInspection.RemoteSessionRejected -> {
            OnlinePendingParticipationSessionRejection
                .RemoteSessionRejected(
                    binding = binding,
                )
        }

        else -> OnlinePendingParticipationSessionRejection.NotRejected
    }
}

private fun createLocalMatchCoordinator(): LocalDominoMatchCoordinator {
    val clockPolicy = if (ENABLE_OFFLINE_CLOCK_DEBUG) {
        DominoMatchClockPolicy.OnlinePerPlayerRound
    } else {
        DominoMatchClockPolicy.Disabled
    }

    return LocalDominoMatchCoordinator(
        clockPolicy = clockPolicy,
    )
}