package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BoundedAsyncOnlineTraceSinkHealth(
    val capacity: Int,
    val acceptingEntries: Boolean,
    val pendingEntryCount: Int,
    val acceptedEntryCount: Long,
    val forwardedEntryCount: Long,
    val droppedEntryCount: Long,
    val failedEntryCount: Long,
    val lastFailureAtEpochMillis: Long?,
    val lastFailureMessage: String?,
)

/*
 * Retira a persistência de traces do caminho autoritativo.
 *
 * record() nunca espera por disco. Quando a capacidade é atingida, o novo
 * evento é descartado e contabilizado, preservando a disponibilidade da
 * partida. Um único worker mantém a ordem dos eventos aceitos.
 */
class BoundedAsyncOnlineTraceSink(
    private val delegate: OnlineTraceSink,
    private val capacity: Int = DEFAULT_CAPACITY,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) : OnlineTraceSink {
    init {
        require(capacity > 0) {
            "A capacidade do pipeline de traces deve ser positiva."
        }
    }

    private val lock = Any()

    private var acceptingEntries = true
    private var pendingEntryCount = 0
    private var acceptedEntryCount = 0L
    private var forwardedEntryCount = 0L
    private var droppedEntryCount = 0L
    private var failedEntryCount = 0L
    private var lastFailureAtEpochMillis: Long? = null
    private var lastFailureMessage: String? = null

    private val mutableHealth = MutableStateFlow(
        currentHealthLocked(),
    )

    val health: StateFlow<BoundedAsyncOnlineTraceSinkHealth> =
        mutableHealth.asStateFlow()

    private val workerScope = CoroutineScope(
        SupervisorJob() + dispatcher,
    )

    private val channel = Channel<OnlineTraceEvent>(
        capacity = capacity,
    )

    private val workerJob: Job = workerScope.launch {
        drainAcceptedEntries()
    }

    override fun record(
        event: OnlineTraceEvent,
    ) {
        synchronized(lock) {
            if (!acceptingEntries) {
                droppedEntryCount += 1L
                publishHealthLocked()
                return
            }

            val accepted = channel.trySend(event).isSuccess

            if (accepted) {
                pendingEntryCount += 1
                acceptedEntryCount += 1L
            } else {
                droppedEntryCount += 1L
            }

            publishHealthLocked()
        }
    }

    suspend fun shutdown() {
        val shouldClose = synchronized(lock) {
            if (acceptingEntries) {
                acceptingEntries = false
                publishHealthLocked()
                true
            } else {
                false
            }
        }

        if (shouldClose) {
            channel.close()
        }

        workerJob.join()
        workerScope.cancel()
    }

    private suspend fun drainAcceptedEntries() {
        for (event in channel) {
            val failure = try {
                delegate.record(event)
                null
            } catch (error: Exception) {
                error
            }

            synchronized(lock) {
                check(pendingEntryCount > 0) {
                    "O pipeline assíncrono perdeu a contagem da fila."
                }

                pendingEntryCount -= 1

                if (failure == null) {
                    forwardedEntryCount += 1L
                } else {
                    failedEntryCount += 1L
                    lastFailureAtEpochMillis = nowEpochMillis()
                    lastFailureMessage =
                        failure.message
                            ?.take(MAX_FAILURE_MESSAGE_CHARACTERS)
                            ?: failure::class.java.simpleName
                }

                publishHealthLocked()
            }
        }
    }

    private fun publishHealthLocked() {
        mutableHealth.value = currentHealthLocked()
    }

    private fun currentHealthLocked(): BoundedAsyncOnlineTraceSinkHealth {
        return BoundedAsyncOnlineTraceSinkHealth(
            capacity = capacity,
            acceptingEntries = acceptingEntries,
            pendingEntryCount = pendingEntryCount,
            acceptedEntryCount = acceptedEntryCount,
            forwardedEntryCount = forwardedEntryCount,
            droppedEntryCount = droppedEntryCount,
            failedEntryCount = failedEntryCount,
            lastFailureAtEpochMillis = lastFailureAtEpochMillis,
            lastFailureMessage = lastFailureMessage,
        )
    }

    private companion object {
        const val DEFAULT_CAPACITY = 4_096
        const val MAX_FAILURE_MESSAGE_CHARACTERS = 180
    }
}
