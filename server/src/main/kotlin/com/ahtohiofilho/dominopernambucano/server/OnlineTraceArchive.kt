package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEntry
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

/*
 * Arquivo de evidência por partida. Cada evento é persistido assim que chega;
 * após o primeiro snapshot em MATCH_FINISHED, os arquivos de resumo passam a
 * ser regenerados a cada chegada tardia de trace do cliente.
 */
class OnlineTraceArchive(
    private val outputDirectory: File = File(
        "build/reports/online-traces",
    ),
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val ingestionPolicy: OnlineTraceIngestionPolicy =
        OnlineTraceIngestionPolicy.Default,
    private val json: Json = Json {
        encodeDefaults = true
    },
) : OnlineTraceSink {
    private data class MatchJournal(
        var roomId: String?,
        val matchId: String,
        val entries: MutableList<OnlineTraceArchiveEntry> = mutableListOf(),
        var finalSnapshotObserved: Boolean = false,
    )

    private val journalsByMatchId = mutableMapOf<String, MatchJournal>()
    private val matchIdsByRoomId = mutableMapOf<String, String>()
    private val pendingEntriesByRoomId =
        mutableMapOf<String, MutableList<OnlineTraceArchiveEntry>>()

    /*
     * A chave inclui clientSessionId porque a sequência reinicia após um novo
     * processo Android. Eventos do servidor não usam esse mecanismo.
     */
    private val receivedClientEntryKeys = mutableSetOf<String>()

    @Synchronized
    override fun record(
        event: OnlineTraceEvent,
    ) {
        append(
            entry = OnlineTraceArchiveEntry(
                serverReceivedAtEpochMillis = nowEpochMillis(),
                origin = OnlineTraceArchiveOrigin.SERVER,
                event = event,
            ),
        )
    }

    @Synchronized
    fun recordClientBatch(
        batch: OnlineTraceBatchDto,
    ): OnlineTraceBatchResultDto {
        val rejectionReason = ingestionPolicy.rejectionReasonOrNull(
            batch = batch,
        )

        if (rejectionReason != null) {
            return OnlineTraceBatchResultDto(
                accepted = false,
                reason = rejectionReason,
            )
        }

        var storedEntryCount = 0

        batch.entries.forEach { entry ->
            val deduplicationKey = entry.clientDeduplicationKey()

            if (!receivedClientEntryKeys.add(deduplicationKey)) {
                return@forEach
            }

            append(
                entry = OnlineTraceArchiveEntry(
                    serverReceivedAtEpochMillis = nowEpochMillis(),
                    origin = OnlineTraceArchiveOrigin.CLIENT,
                    clientSequence = entry.sequence,
                    event = entry.event,
                ),
            )

            storedEntryCount += 1
        }

        return OnlineTraceBatchResultDto(
            accepted = true,
            storedEntryCount = storedEntryCount,
        )
    }

    private fun OnlineTraceEntry.clientDeduplicationKey(): String {
        return "${event.context.clientSessionId}:$sequence"
    }

    private fun append(
        entry: OnlineTraceArchiveEntry,
    ) {
        val context = entry.event.context
        val roomId = context.roomId
        val matchId = context.matchId

        if (!matchId.isNullOrBlank()) {
            val journal = getOrCreateJournal(
                roomId = roomId,
                matchId = matchId,
            )

            appendToJournal(
                journal = journal,
                entry = entry,
            )

            return
        }

        if (roomId.isNullOrBlank()) {
            return
        }

        val associatedMatchId = matchIdsByRoomId[roomId]

        if (associatedMatchId != null) {
            val journal = journalsByMatchId[associatedMatchId]
                ?: return

            appendToJournal(
                journal = journal,
                entry = entry,
            )

            return
        }

        pendingEntriesByRoomId
            .getOrPut(roomId) { mutableListOf() }
            .add(entry)
    }

    private fun getOrCreateJournal(
        roomId: String?,
        matchId: String,
    ): MatchJournal {
        val journal = journalsByMatchId.getOrPut(matchId) {
            MatchJournal(
                roomId = roomId,
                matchId = matchId,
            )
        }

        if (!roomId.isNullOrBlank()) {
            journal.roomId = roomId
            matchIdsByRoomId[roomId] = matchId

            pendingEntriesByRoomId
                .remove(roomId)
                .orEmpty()
                .forEach { pendingEntry ->
                    appendToJournal(
                        journal = journal,
                        entry = pendingEntry,
                    )
                }
        }

        return journal
    }

    private fun appendToJournal(
        journal: MatchJournal,
        entry: OnlineTraceArchiveEntry,
    ) {
        journal.entries += entry
        appendTimelineEntry(
            matchId = journal.matchId,
            entry = entry,
        )

        if (
            entry.event.state?.phase == MATCH_FINISHED_PHASE_NAME
        ) {
            journal.finalSnapshotObserved = true
        }

        if (journal.finalSnapshotObserved) {
            writeConsolidatedFiles(
                journal = journal,
            )
        }
    }

    private fun appendTimelineEntry(
        matchId: String,
        entry: OnlineTraceArchiveEntry,
    ) {
        val matchDirectory = directoryForMatch(
            matchId = matchId,
        )

        matchDirectory.mkdirs()

        File(
            matchDirectory,
            TIMELINE_FILE_NAME,
        ).appendText(
            json.encodeToString(entry) + System.lineSeparator(),
            StandardCharsets.UTF_8,
        )
    }

    private fun writeConsolidatedFiles(
        journal: MatchJournal,
    ) {
        val matchDirectory = directoryForMatch(
            matchId = journal.matchId,
        )

        matchDirectory.mkdirs()

        File(
            matchDirectory,
            MANIFEST_FILE_NAME,
        ).writeText(
            json.encodeToString(
                OnlineTraceArchiveManifest(
                    roomId = journal.roomId,
                    matchId = journal.matchId,
                    firstServerReceivedAtEpochMillis = journal.entries
                        .minOfOrNull { entry ->
                            entry.serverReceivedAtEpochMillis
                        },
                    lastServerReceivedAtEpochMillis = journal.entries
                        .maxOfOrNull { entry ->
                            entry.serverReceivedAtEpochMillis
                        },
                    finalSnapshotObserved = journal.finalSnapshotObserved,
                    totalEntryCount = journal.entries.size,
                    clientSessionIds = journal.entries
                        .mapNotNull { entry ->
                            entry.event.context.clientSessionId
                        }
                        .distinct()
                        .sorted(),
                    generatedAtEpochMillis = nowEpochMillis(),
                ),
            ),
            StandardCharsets.UTF_8,
        )

        File(
            matchDirectory,
            SUMMARY_FILE_NAME,
        ).writeText(
            buildSummary(
                journal = journal,
            ),
            StandardCharsets.UTF_8,
        )

        File(
            matchDirectory,
            ANOMALIES_FILE_NAME,
        ).writeText(
            buildAnomalies(
                journal = journal,
            ),
            StandardCharsets.UTF_8,
        )
    }

    private fun buildSummary(
        journal: MatchJournal,
    ): String {
        val entries = journal.entries
        val events = entries.map { entry -> entry.event }
        val serverPublishedRevisions = events
            .filter { event ->
                event.source == OnlineTraceSource.SERVER_STORE ||
                        event.source == OnlineTraceSource.SERVER_TICKER
            }
            .filter { event ->
                event.type == OnlineTraceType.SNAPSHOT_PUBLISHED
            }
            .mapNotNull { event ->
                event.context.snapshotRevision
            }
            .distinct()
            .sorted()

        val clientSessions = events
            .mapNotNull { event ->
                event.context.clientSessionId
            }
            .distinct()
            .sorted()

        return buildString {
            appendLine("PARTIDA: ${journal.matchId}")
            appendLine("SALA: ${journal.roomId ?: "não informada"}")
            appendLine("STATUS: ${if (journal.finalSnapshotObserved) "FINALIZADA" else "EM ANDAMENTO"}")
            appendLine("EVENTOS CONSOLIDADOS: ${entries.size}")
            appendLine("REVISÕES PUBLICADAS PELO SERVIDOR: ${serverPublishedRevisions.size}")

            serverPublishedRevisions.lastOrNull()?.let { revision ->
                appendLine("ÚLTIMA REVISÃO AUTORITATIVA: $revision")
            }

            appendLine()
            appendLine("FONTES")
            entries
                .groupingBy { entry -> entry.origin.name }
                .eachCount()
                .toSortedMap()
                .forEach { (origin, count) ->
                    appendLine("- $origin: $count")
                }

            appendLine()
            appendLine("CLIENTES")

            if (clientSessions.isEmpty()) {
                appendLine("- Nenhum trace de cliente recebido.")
            } else {
                clientSessions.forEach { clientSessionId ->
                    val clientEvents = events.filter { event ->
                        event.context.clientSessionId == clientSessionId
                    }

                    appendLine("- $clientSessionId")
                    appendLine(
                        "  snapshots recebidos: ${clientEvents.count { event -> event.type == OnlineTraceType.SNAPSHOT_RECEIVED }}",
                    )
                    appendLine(
                        "  apresentações iniciadas/concluídas: " +
                                "${clientEvents.count { event -> event.type == OnlineTraceType.PRESENTATION_STARTED }}/" +
                                "${clientEvents.count { event -> event.type == OnlineTraceType.PRESENTATION_FINISHED }}",
                    )
                    appendLine(
                        "  animações iniciadas/concluídas/canceladas: " +
                                "${clientEvents.count { event -> event.type == OnlineTraceType.ANIMATION_STARTED }}/" +
                                "${clientEvents.count { event -> event.type == OnlineTraceType.ANIMATION_FINISHED }}/" +
                                "${clientEvents.count { event -> event.type == OnlineTraceType.ANIMATION_CANCELLED }}",
                    )
                }
            }

            val outstandingPresentations = findOutstandingPresentations(
                events = events,
            )

            appendLine()
            appendLine("PENDÊNCIAS DE APRESENTAÇÃO")

            if (outstandingPresentations.isEmpty()) {
                appendLine("- Nenhuma.")
            } else {
                outstandingPresentations.forEach { presentation ->
                    appendLine("- $presentation")
                }
            }

            appendLine()
            appendLine("ANOMALIAS REGISTRADAS: ${findAnomalyEntries(entries).size}")
            appendLine("ARQUIVO DETALHADO: $ANOMALIES_FILE_NAME")
        }
    }

    private fun buildAnomalies(
        journal: MatchJournal,
    ): String {
        val anomalyEntries = findAnomalyEntries(
            entries = journal.entries,
        )

        return buildString {
            appendLine("PARTIDA: ${journal.matchId}")
            appendLine("ANOMALIAS: ${anomalyEntries.size}")
            appendLine()

            if (anomalyEntries.isEmpty()) {
                appendLine("Nenhuma anomalia estruturada foi registrada.")
            } else {
                anomalyEntries.forEach { entry ->
                    val event = entry.event
                    appendLine(
                        "${entry.serverReceivedAtEpochMillis} " +
                                "[${entry.origin}] " +
                                "${event.level}/${event.source}/${event.type} " +
                                "room=${event.context.roomId.orEmpty()} " +
                                "match=${event.context.matchId.orEmpty()} " +
                                "revision=${event.context.snapshotRevision ?: ""} " +
                                "presentation=${event.attributes["presentationId"].orEmpty()} " +
                                "attributes=${event.attributes}",
                    )
                }
            }
        }
    }

    private fun findOutstandingPresentations(
        events: List<OnlineTraceEvent>,
    ): List<String> {
        val started = mutableSetOf<String>()
        val completed = mutableSetOf<String>()

        events.forEach { event ->
            val clientSessionId = event.context.clientSessionId ?: return@forEach
            val presentationId = event.attributes["presentationId"]
                ?: return@forEach
            val key = "$clientSessionId/$presentationId"

            when (event.type) {
                OnlineTraceType.PRESENTATION_STARTED -> started += key

                OnlineTraceType.PRESENTATION_FINISHED,
                OnlineTraceType.ANIMATION_CANCELLED -> completed += key

                else -> Unit
            }
        }

        return (started - completed).sorted()
    }

    private fun findAnomalyEntries(
        entries: List<OnlineTraceArchiveEntry>,
    ): List<OnlineTraceArchiveEntry> {
        return entries.filter { entry ->
            entry.event.level == OnlineTraceLevel.WARN ||
                    entry.event.level == OnlineTraceLevel.ERROR ||
                    entry.event.type in ANOMALY_TRACE_TYPES
        }
    }

    private fun directoryForMatch(
        matchId: String,
    ): File {
        return File(
            outputDirectory,
            matchId.toSafePathSegment(),
        )
    }

    private fun String.toSafePathSegment(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private companion object {
        const val MATCH_FINISHED_PHASE_NAME = "MATCH_FINISHED"

        const val TIMELINE_FILE_NAME = "timeline.jsonl"
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val SUMMARY_FILE_NAME = "resumo.txt"
        const val ANOMALIES_FILE_NAME = "anomalias.txt"

        val ANOMALY_TRACE_TYPES = setOf(
            OnlineTraceType.ACTION_REJECTED,
            OnlineTraceType.POLLING_FAILED,
            OnlineTraceType.TRANSPORT_FAILURE,
            OnlineTraceType.INVARIANT_VIOLATION,
            OnlineTraceType.ANIMATION_CANCELLED,
            OnlineTraceType.ANIMATION_FALLBACK_USED,
        )
    }
}

@Serializable
private enum class OnlineTraceArchiveOrigin {
    SERVER,
    CLIENT,
}

@Serializable
private data class OnlineTraceArchiveEntry(
    val serverReceivedAtEpochMillis: Long,
    val origin: OnlineTraceArchiveOrigin,
    val clientSequence: Long? = null,
    val event: OnlineTraceEvent,
)

@Serializable
private data class OnlineTraceArchiveManifest(
    val roomId: String? = null,
    val matchId: String,
    val firstServerReceivedAtEpochMillis: Long? = null,
    val lastServerReceivedAtEpochMillis: Long? = null,
    val finalSnapshotObserved: Boolean,
    val totalEntryCount: Int,
    val clientSessionIds: List<String>,
    val generatedAtEpochMillis: Long,
)
