package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.ONLINE_TRACE_SCHEMA_VERSION
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEntry
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource

private val ALLOWED_CLIENT_TRACE_SOURCES = setOf(
    OnlineTraceSource.CLIENT_UI,
    OnlineTraceSource.CLIENT_COORDINATOR,
    OnlineTraceSource.CLIENT_REPOSITORY,
)

data class OnlineTraceIngestionPolicy(
    val maxRequestBodyBytes: Long = 1_048_576L,
    val maxBatchEntryCount: Int = 128,
    val maxIdentifierCharacters: Int = 160,
    val maxAttributeCount: Int = 24,
    val maxAttributeKeyCharacters: Int = 64,
    val maxAttributeValueCharacters: Int = 256,
    val maxTotalAttributeCharacters: Int = 4_096,
    val maxPhaseCharacters: Int = 160,
) {
    init {
        require(maxRequestBodyBytes > 0L)
        require(maxBatchEntryCount > 0)
        require(maxIdentifierCharacters > 0)
        require(maxAttributeCount >= 0)
        require(maxAttributeKeyCharacters > 0)
        require(maxAttributeValueCharacters >= 0)
        require(maxTotalAttributeCharacters >= 0)
        require(maxPhaseCharacters > 0)
    }

    fun rejectionReasonOrNull(
        batch: OnlineTraceBatchDto,
    ): String? {
        if (batch.entries.isEmpty()) {
            return "O lote de rastreamento não pode ser vazio."
        }

        if (batch.entries.size > maxBatchEntryCount) {
            return "O lote de rastreamento excede " +
                    "$maxBatchEntryCount eventos."
        }

        batch.entries.forEachIndexed { index, entry ->
            val entryReason = entryRejectionReasonOrNull(
                entry = entry,
            )

            if (entryReason != null) {
                return "Evento de índice $index inválido: $entryReason"
            }
        }

        return null
    }

    private fun entryRejectionReasonOrNull(
        entry: OnlineTraceEntry,
    ): String? {
        val event = entry.event
        val context = event.context

        if (entry.sequence <= 0L) {
            return "sequence deve ser positiva."
        }

        if (event.schemaVersion != ONLINE_TRACE_SCHEMA_VERSION) {
            return "schemaVersion não suportada."
        }

        if (event.occurredAtEpochMillis <= 0L) {
            return "occurredAtEpochMillis deve ser positivo."
        }

        if (event.source !in ALLOWED_CLIENT_TRACE_SOURCES) {
            return "source não pertence ao cliente."
        }

        val clientSessionId = context.clientSessionId

        if (
            clientSessionId.isNullOrBlank() ||
            clientSessionId.length > maxIdentifierCharacters
        ) {
            return "clientSessionId ausente ou acima do limite."
        }

        val optionalIdentifiers = listOf(
            "roomId" to context.roomId,
            "matchId" to context.matchId,
            "playerId" to context.playerId,
            "actionId" to context.actionId,
        )

        val invalidIdentifier = optionalIdentifiers.firstOrNull {
                (_, value) ->
            value != null &&
                    (
                        value.isBlank() ||
                                value.length > maxIdentifierCharacters
                        )
        }

        if (invalidIdentifier != null) {
            return "${invalidIdentifier.first} vazio ou acima do limite."
        }

        if (
            context.roomId == null &&
            context.matchId == null
        ) {
            return "roomId ou matchId deve ser informado."
        }

        if (
            context.localSeatIndex != null &&
            context.localSeatIndex !in 0..3
        ) {
            return "localSeatIndex fora do intervalo."
        }

        val revisions = listOf(
            context.actionRevision,
            context.snapshotRevision,
        )

        if (revisions.any { revision ->
                revision != null && revision < 0L
            }
        ) {
            return "Revisão negativa não é permitida."
        }

        if (event.attributes.size > maxAttributeCount) {
            return "Quantidade de atributos acima do limite."
        }

        event.attributes.forEach { (key, value) ->
            if (
                key.isBlank() ||
                key.length > maxAttributeKeyCharacters
            ) {
                return "Chave de atributo vazia ou acima do limite."
            }

            if (value.length > maxAttributeValueCharacters) {
                return "Valor de atributo acima do limite."
            }
        }

        val totalAttributeCharacters = event.attributes.entries.sumOf {
                (key, value) ->
            key.length + value.length
        }

        if (totalAttributeCharacters > maxTotalAttributeCharacters) {
            return "Orçamento total de atributos excedido."
        }

        event.state?.let { state ->
            if (state.roundNumber <= 0) {
                return "roundNumber deve ser positivo."
            }

            if (
                state.phase.isBlank() ||
                state.phase.length > maxPhaseCharacters
            ) {
                return "phase vazia ou acima do limite."
            }

            if (state.currentPlayerIndex !in 0..3) {
                return "currentPlayerIndex fora do intervalo."
            }

            if (state.boardPieceCount !in 0..28) {
                return "boardPieceCount fora do intervalo."
            }

            if (
                state.teamScores.size > 2 ||
                state.teamScores.any { score -> score < 0 }
            ) {
                return "teamScores inválido."
            }

            if (
                state.playerClockMillis.size > 4 ||
                state.playerClockMillis.any { value -> value < 0L }
            ) {
                return "playerClockMillis inválido."
            }

            if (
                state.automaticPlayerIndexes.size > 4 ||
                state.automaticPlayerIndexes.any { index ->
                    index !in 0..3
                }
            ) {
                return "automaticPlayerIndexes inválido."
            }

            val fingerprint = state.stateFingerprint

            if (
                fingerprint != null &&
                (
                    fingerprint.isBlank() ||
                            fingerprint.length >
                            maxIdentifierCharacters
                    )
            ) {
                return "stateFingerprint vazio ou acima do limite."
            }
        }

        return null
    }

    companion object {
        val Default = OnlineTraceIngestionPolicy()
    }
}
