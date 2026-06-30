package com.ahtohiofilho.dominopernambucano.server

/**
 * Orquestra a durabilidade do agregado autoritativo sem acoplar as regras de
 * jogo ao mecanismo de arquivo.
 *
 * Uma mutação só é mantida em memória quando o snapshot resultante foi salvo.
 * Se a persistência falhar, o store volta ao snapshot anterior.
 */
class OnlineServerPersistenceController(
    private val store: InMemoryOnlineServerStore,
    private val stateStore: OnlineServerStateStore,
) {
    @Volatile
    private var recoveryStatus: OnlineServerRecoveryStatus =
        OnlineServerRecoveryStatus.Ready

    fun restoreAtStartup(): OnlineServerRecoveryStatus {
        val restoredStatus = when (val loadResult = stateStore.load()) {
            OnlineServerStateLoadResult.Empty -> {
                OnlineServerRecoveryStatus.Ready
            }

            is OnlineServerStateLoadResult.Invalid -> {
                OnlineServerRecoveryStatus.Invalid(
                    reason = loadResult.reason,
                )
            }

            is OnlineServerStateLoadResult.Recovered -> {
                try {
                    store.restorePersistentState(
                        state = loadResult.state,
                    )

                    reconcileRecoveredState()

                    OnlineServerRecoveryStatus.Ready
                } catch (exception: Exception) {
                    OnlineServerRecoveryStatus.Invalid(
                        reason = "Não foi possível restaurar o estado: " +
                                exception.javaClass.simpleName,
                    )
                }
            }
        }

        recoveryStatus = restoredStatus

        return restoredStatus
    }

    fun currentRecoveryStatus(): OnlineServerRecoveryStatus {
        return recoveryStatus
    }

    fun <T> mutate(
        mutation: () -> T,
    ): T {
        requireReady()

        val previousState = store.snapshotPersistentState()

        return try {
            val result = mutation()

            persistIfChanged(
                previousState = previousState,
            )

            result
        } catch (exception: Exception) {
            store.restorePersistentState(
                state = previousState,
            )

            throw exception
        }
    }

    fun clearExpiredRooms() {
        if (recoveryStatus !is OnlineServerRecoveryStatus.Ready) {
            return
        }

        val previousState = store.snapshotPersistentState()

        try {
            store.clearExpiredRooms()

            persistIfChanged(
                previousState = previousState,
            )
        } catch (exception: Exception) {
            store.restorePersistentState(
                state = previousState,
            )

            throw exception
        }
    }

    fun advanceAuthoritativeTime() {
        if (recoveryStatus !is OnlineServerRecoveryStatus.Ready) {
            return
        }

        val previousState = store.snapshotPersistentState()

        try {
            store.advanceAuthoritativeTime()

            persistIfChanged(
                previousState = previousState,
            )
        } catch (exception: Exception) {
            store.restorePersistentState(
                state = previousState,
            )

            throw exception
        }
    }

    private fun reconcileRecoveredState() {
        val previousState = store.snapshotPersistentState()

        store.clearExpiredRooms()
        store.advanceAuthoritativeTime()

        persistIfChanged(
            previousState = previousState,
        )
    }

    private fun persistIfChanged(
        previousState: OnlineServerPersistentState,
    ) {
        val updatedState = store.snapshotPersistentState()

        if (updatedState != previousState) {
            stateStore.save(
                state = updatedState,
            )
        }
    }

    private fun requireReady() {
        check(recoveryStatus is OnlineServerRecoveryStatus.Ready) {
            "A autoridade online não está disponível para mutações."
        }
    }
}

sealed interface OnlineServerRecoveryStatus {
    data object Ready : OnlineServerRecoveryStatus

    data class Invalid(
        val reason: String,
    ) : OnlineServerRecoveryStatus
}
