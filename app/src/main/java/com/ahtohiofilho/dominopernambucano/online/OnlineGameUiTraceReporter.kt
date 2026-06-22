package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType

data class OnlineUiTraceContext(
    val presentationId: String? = null,
    val snapshotRevision: Long? = null,
)

interface OnlineGameUiTraceReporter {
    fun currentUiTraceContext(): OnlineUiTraceContext

    fun traceUiEvent(
        type: OnlineTraceType,
        traceContext: OnlineUiTraceContext,
        attributes: Map<String, String> = emptyMap(),
    )
}
