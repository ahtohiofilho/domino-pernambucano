package com.ahtohiofilho.dominopernambucano.online.observability

import kotlinx.serialization.Serializable

/*
 * Lote idempotente enviado pelo cliente ao servidor de desenvolvimento.
 *
 * A sequência pertence ao buffer local e, combinada ao clientSessionId já
 * presente no contexto de cada evento, permite ao servidor descartar retries
 * sem perder a ordem observada no dispositivo.
 */
@Serializable
data class OnlineTraceBatchDto(
    val entries: List<OnlineTraceEntry>,
)

@Serializable
data class OnlineTraceBatchResultDto(
    val accepted: Boolean,
    val storedEntryCount: Int = 0,
    val reason: String? = null,
)
