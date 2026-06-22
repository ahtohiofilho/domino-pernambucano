package com.ahtohiofilho.dominopernambucano.online.observability

import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * Encaminha o buffer local sem bloquear a renderização e sem registrar novos
 * eventos no mesmo pipeline. Falhas ficam contidas: o próximo evento ou ciclo
 * de recomposição tenta novamente o intervalo ainda não confirmado.
 */
class OnlineTraceBatchUploader(
    private val repository: OnlineRoomRepository,
    private val traceBuffer: InMemoryOnlineTraceBuffer,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    init {
        require(batchSize > 0) {
            "O tamanho do lote de rastreamento deve ser maior que zero."
        }
    }

    private val uploadMutex = Mutex()

    private val lastAcknowledgedSequenceByMatchId =
        mutableMapOf<String, Long>()

    suspend fun flushPendingEntries(
        roomId: String,
        matchId: String,
    ) {
        uploadMutex.withLock {
            while (true) {
                val lastAcknowledgedSequence =
                    lastAcknowledgedSequenceByMatchId[matchId] ?: 0L

                val pendingEntries = traceBuffer
                    .entriesForRoomOrMatch(
                        roomId = roomId,
                        matchId = matchId,
                    )
                    .asSequence()
                    .filter { entry ->
                        entry.sequence > lastAcknowledgedSequence
                    }
                    .take(batchSize)
                    .toList()

                if (pendingEntries.isEmpty()) {
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
                    return
                }

                if (!result.accepted) {
                    return
                }

                lastAcknowledgedSequenceByMatchId[matchId] =
                    pendingEntries.last().sequence
            }
        }
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 128
    }
}
