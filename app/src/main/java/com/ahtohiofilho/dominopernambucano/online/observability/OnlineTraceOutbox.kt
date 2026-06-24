package com.ahtohiofilho.dominopernambucano.online.observability

import kotlinx.coroutines.flow.StateFlow

/*
 * Fonte de verdade dos traces do cliente que ainda precisam de confirmaÃ§Ã£o
 * remota. Uma implementaÃ§Ã£o pode manter uma cÃ³pia diagnÃ³stica de entradas
 * reconhecidas, mas nÃ£o pode devolvÃª-las novamente como pendentes.
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
