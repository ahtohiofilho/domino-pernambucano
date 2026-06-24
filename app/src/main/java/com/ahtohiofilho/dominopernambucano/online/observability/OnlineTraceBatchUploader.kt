package com.ahtohiofilho.dominopernambucano.online.observability

import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class OnlineTraceUploadFailureKind {
    TRANSPORT,
    REJECTED,
    ACKNOWLEDGEMENT,
}

data class OnlineTraceOutboxHealth(
    val matchId: String,
    val lastAcknowledgedSequence: Long,
    val pendingEntryCount: Int,
    val oldestPendingSequence: Long?,
    val newestPendingSequence: Long?,
    val consecutiveFailureCount: Int,
    val lastFailureKind: OnlineTraceUploadFailureKind?,
    val lastFailureAtEpochMillis: Long?,
)

/*
 * Encaminha o buffer local sem bloquear a renderização e sem registrar novos
 * eventos no mesmo pipeline. Falhas ficam contidas: o próximo evento ou ciclo
 * de recomposição tenta novamente o intervalo ainda não confirmado.
 */
class OnlineTraceBatchUploader(
    private val repository: OnlineRoomRepository,
    private val traceOutbox: OnlineTraceOutbox,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {
    constructor(
        repository: OnlineRoomRepository,
        traceBuffer: InMemoryOnlineTraceBuffer,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        nowEpochMillis: () -> Long = {
            System.currentTimeMillis()
        },
    ) : this(
        repository = repository,
        traceOutbox = traceBuffer,
        batchSize = batchSize,
        nowEpochMillis = nowEpochMillis,
    )

    init {
        require(batchSize > 0) {
            "O tamanho do lote de rastreamento deve ser maior que zero."
        }
    }

    private val uploadMutex = Mutex()

    private val lastAcknowledgedSequenceByMatchId =
        mutableMapOf<String, Long>()

    private val mutableOutboxHealth =
        MutableStateFlow<Map<String, OnlineTraceOutboxHealth>>(emptyMap())

    val outboxHealth: StateFlow<Map<String, OnlineTraceOutboxHealth>> =
        mutableOutboxHealth.asStateFlow()

    suspend fun flushPendingEntries(
        roomId: String,
        matchId: String,
    ) {
        uploadMutex.withLock {
            while (true) {
                val lastAcknowledgedSequence =
                    lastAcknowledgedSequenceByMatchId[matchId] ?: 0L

                val pendingEntries = findPendingEntries(
                    roomId = roomId,
                    matchId = matchId,
                    limit = batchSize,
                )

                if (pendingEntries.isEmpty()) {
                    publishOutboxHealth(
                        roomId = roomId,
                        matchId = matchId,
                        lastAcknowledgedSequence = lastAcknowledgedSequence,
                    )
                    return
                }

                val result = try {
                    repository.submitTraceBatch(
                        batch = OnlineTraceBatchDto(
                            entries = pendingEntries,
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    publishOutboxHealth(
                        roomId = roomId,
                        matchId = matchId,
                        lastAcknowledgedSequence = lastAcknowledgedSequence,
                        failureKind = OnlineTraceUploadFailureKind.TRANSPORT,
                    )
                    return
                }

                if (!result.accepted) {
                    publishOutboxHealth(
                        roomId = roomId,
                        matchId = matchId,
                        lastAcknowledgedSequence = lastAcknowledgedSequence,
                        failureKind = OnlineTraceUploadFailureKind.REJECTED,
                    )
                    return
                }

                try {
                    traceOutbox.acknowledge(
                        entries = pendingEntries,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    publishOutboxHealth(
                        roomId = roomId,
                        matchId = matchId,
                        lastAcknowledgedSequence = lastAcknowledgedSequence,
                        failureKind = OnlineTraceUploadFailureKind.ACKNOWLEDGEMENT,
                    )
                    return
                }

                val updatedLastAcknowledgedSequence =
                    pendingEntries.last().sequence

                lastAcknowledgedSequenceByMatchId[matchId] =
                    updatedLastAcknowledgedSequence

                publishOutboxHealth(
                    roomId = roomId,
                    matchId = matchId,
                    lastAcknowledgedSequence = updatedLastAcknowledgedSequence,
                    clearFailures = true,
                )
            }
        }
    }

    private fun findPendingEntries(
        roomId: String,
        matchId: String,
        limit: Int,
    ): List<OnlineTraceEntry> {
        return traceOutbox.pendingEntries(
            roomId = roomId,
            matchId = matchId,
            limit = limit,
        )
    }

    private fun publishOutboxHealth(
        roomId: String,
        matchId: String,
        lastAcknowledgedSequence: Long,
        failureKind: OnlineTraceUploadFailureKind? = null,
        clearFailures: Boolean = false,
    ) {
        val previousHealth = mutableOutboxHealth.value[matchId]
        val pendingEntries = findPendingEntries(
            roomId = roomId,
            matchId = matchId,
            limit = Int.MAX_VALUE,
        )

        val hasFailure = failureKind != null
        val consecutiveFailureCount = when {
            hasFailure -> {
                (previousHealth?.consecutiveFailureCount ?: 0) + 1
            }

            clearFailures -> 0
            else -> previousHealth?.consecutiveFailureCount ?: 0
        }

        val lastFailureKind = when {
            hasFailure -> failureKind
            clearFailures -> null
            else -> previousHealth?.lastFailureKind
        }

        val lastFailureAtEpochMillis = when {
            hasFailure -> nowEpochMillis()
            clearFailures -> null
            else -> previousHealth?.lastFailureAtEpochMillis
        }

        val updatedHealth = OnlineTraceOutboxHealth(
            matchId = matchId,
            lastAcknowledgedSequence = lastAcknowledgedSequence,
            pendingEntryCount = pendingEntries.size,
            oldestPendingSequence = pendingEntries.firstOrNull()?.sequence,
            newestPendingSequence = pendingEntries.lastOrNull()?.sequence,
            consecutiveFailureCount = consecutiveFailureCount,
            lastFailureKind = lastFailureKind,
            lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        )

        mutableOutboxHealth.value =
            mutableOutboxHealth.value + (matchId to updatedHealth)
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 128
    }
}
