package com.ahtohiofilho.dominopernambucano.online.observability

import kotlinx.coroutines.flow.StateFlow

/*
 * Fonte de verdade dos traces do cliente que ainda precisam de confirmação
 * remota. Uma implementação pode manter uma cópia diagnóstica de entradas
 * reconhecidas, mas não pode devolvê-las novamente como pendentes.
 */
interface OnlineTraceOutbox : OnlineTraceSink {
    val pendingEntryVersion: StateFlow<Long>

    fun pendingEntries(
        roomId: String,
        matchId: String,
        limit: Int,
    ): List<OnlineTraceEntry>

    suspend fun acknowledge(
        entries: List<OnlineTraceEntry>,
    )
}
