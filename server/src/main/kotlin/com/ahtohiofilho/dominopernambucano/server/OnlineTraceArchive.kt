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
    private val memoryPolicy: OnlineTraceArchiveMemoryPolicy =
        OnlineTraceArchiveMemoryPolicy.Default,
    private val json: Json = Json {
        encodeDefaults = true
    },
) : OnlineTraceSink {
    private data class MatchJournal(
        var roomId: String?,
        val matchId: String,
        val entries: MutableList<OnlineTraceArchiveEntry> = mutableListOf(),
        var finalSnapshotObserved: Boolean = false,
        var totalEntryCount: Long = 0L,
        var evictedEntryCount: Long = 0L,
        var firstServerReceivedAtEpochMillis: Long? = null,
        var lastServerReceivedAtEpochMillis: Long? = null,
    )

    private val journalsByMatchId =
        linkedMapOf<String, MatchJournal>()
    private val matchIdsByRoomId =
        mutableMapOf<String, String>()
    private val pendingEntriesByRoomId =
        linkedMapOf<String, MutableList<OnlineTraceArchiveEntry>>()

    /*
     * A chave inclui clientSessionId porque a sequência reinicia após um novo
     * processo Android. Eventos do servidor não usam esse mecanismo.
     */
    private val receivedClientEntryKeys = linkedSetOf<String>()

    private var evictedJournalEntryCount = 0L
    private var droppedPendingEntryCount = 0L
    private var evictedFinalizedJournalCount = 0L
    private var evictedClientDeduplicationKeyCount = 0L
    private var untrackedJournalEntryCount = 0L

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

            if (!registerClientEntryKey(deduplicationKey)) {
                return@forEach
            }

            val stored = append(
                entry = OnlineTraceArchiveEntry(
                    serverReceivedAtEpochMillis = nowEpochMillis(),
                    origin = OnlineTraceArchiveOrigin.CLIENT,
                    clientSequence = entry.sequence,
                    event = entry.event,
                ),
            )

            if (stored) {
                storedEntryCount += 1
            } else {
                receivedClientEntryKeys.remove(deduplicationKey)
            }
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
    ): Boolean {
        val context = entry.event.context
        val roomId = context.roomId
        val matchId = context.matchId

        if (!matchId.isNullOrBlank()) {
            val journal = getOrCreateJournal(
                roomId = roomId,
                matchId = matchId,
            )

            if (journal == null) {
                pendingEntriesByRoomId
                    .remove(roomId)
                    .orEmpty()
                    .forEach { pendingEntry ->
                        appendTimelineEntry(
                            matchId = matchId,
                            entry = pendingEntry,
                        )
                        untrackedJournalEntryCount += 1L
                    }

                appendTimelineEntry(
                    matchId = matchId,
                    entry = entry,
                )
                untrackedJournalEntryCount += 1L
                return true
            }

            appendToJournal(
                journal = journal,
                entry = entry,
            )

            return true
        }

        if (roomId.isNullOrBlank()) {
            return false
        }

        val associatedMatchId = matchIdsByRoomId[roomId]

        if (associatedMatchId != null) {
            val journal = journalsByMatchId[associatedMatchId]
                ?: return false

            appendToJournal(
                journal = journal,
                entry = entry,
            )

            return true
        }

        return retainPendingEntry(
            roomId = roomId,
            entry = entry,
        )
    }

    private fun registerClientEntryKey(
        deduplicationKey: String,
    ): Boolean {
        if (!receivedClientEntryKeys.add(deduplicationKey)) {
            return false
        }

        while (
            receivedClientEntryKeys.size >
            memoryPolicy.maxClientDeduplicationKeyCount
        ) {
            val iterator = receivedClientEntryKeys.iterator()

            check(iterator.hasNext()) {
                "A janela de deduplicação perdeu seu estado."
            }

            iterator.next()
            iterator.remove()
            evictedClientDeduplicationKeyCount += 1L
        }

        return true
    }

    private fun retainPendingEntry(
        roomId: String,
        entry: OnlineTraceArchiveEntry,
    ): Boolean {
        val existingEntries = pendingEntriesByRoomId[roomId]

        if (existingEntries == null) {
            if (
                pendingEntriesByRoomId.size >=
                memoryPolicy.maxPendingRoomCount
            ) {
                droppedPendingEntryCount += 1L
                return false
            }

            pendingEntriesByRoomId[roomId] =
                mutableListOf(entry)

            return true
        }

        if (
            existingEntries.size >=
            memoryPolicy.maxPendingEntriesPerRoom
        ) {
            droppedPendingEntryCount += 1L
            return false
        }

        existingEntries += entry
        return true
    }

    private fun getOrCreateJournal(
        roomId: String?,
        matchId: String,
    ): MatchJournal? {
        val existingJournal = journalsByMatchId[matchId]

        val journal = if (existingJournal != null) {
            existingJournal
        } else {
            evictFinalizedJournalsUntilCapacity()

            if (
                journalsByMatchId.size >=
                memoryPolicy.maxJournalCount
            ) {
                return null
            }

            MatchJournal(
                roomId = roomId,
                matchId = matchId,
            ).also { createdJournal ->
                journalsByMatchId[matchId] = createdJournal
            }
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

    private fun evictFinalizedJournalsUntilCapacity() {
        while (
            journalsByMatchId.size >=
            memoryPolicy.maxJournalCount
        ) {
            val candidate = journalsByMatchId.entries
                .firstOrNull { (_, journal) ->
                    journal.finalSnapshotObserved
                }
                ?: return

            val removedJournal = candidate.value
            journalsByMatchId.remove(candidate.key)

            removedJournal.roomId?.let { roomId ->
                if (matchIdsByRoomId[roomId] == candidate.key) {
                    matchIdsByRoomId.remove(roomId)
                }
            }

            evictedFinalizedJournalCount += 1L
        }
    }

    private fun appendToJournal(
        journal: MatchJournal,
        entry: OnlineTraceArchiveEntry,
    ) {
        journal.totalEntryCount += 1L

        journal.firstServerReceivedAtEpochMillis =
            journal.firstServerReceivedAtEpochMillis
                ?.let { currentFirst ->
                    minOf(
                        currentFirst,
                        entry.serverReceivedAtEpochMillis,
                    )
                }
                ?: entry.serverReceivedAtEpochMillis

        journal.lastServerReceivedAtEpochMillis =
            journal.lastServerReceivedAtEpochMillis
                ?.let { currentLast ->
                    maxOf(
                        currentLast,
                        entry.serverReceivedAtEpochMillis,
                    )
                }
                ?: entry.serverReceivedAtEpochMillis

        journal.entries += entry

        if (
            journal.entries.size >
            memoryPolicy.maxEntriesPerJournal
        ) {
            journal.entries.removeAt(0)
            journal.evictedEntryCount += 1L
            evictedJournalEntryCount += 1L
        }

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
                    firstServerReceivedAtEpochMillis =
                        journal.firstServerReceivedAtEpochMillis,
                    lastServerReceivedAtEpochMillis =
                        journal.lastServerReceivedAtEpochMillis,
                    finalSnapshotObserved = journal.finalSnapshotObserved,
                    totalEntryCount = journal.totalEntryCount,
                    retainedEntryCount = journal.entries.size,
                    evictedFromMemoryEntryCount =
                        journal.evictedEntryCount,
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
            appendLine(
                "EVENTOS PERSISTIDOS NO TIMELINE: " +
                        journal.totalEntryCount,
            )
            appendLine(
                "EVENTOS FORA DA JANELA CONSOLIDADA: " +
                        journal.evictedEntryCount,
            )
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
            appendLine("ANOMALIAS NA JANELA: ${anomalyEntries.size}")
            appendLine(
                "EVENTOS FORA DA JANELA: " +
                        journal.evictedEntryCount,
            )
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

    @Synchronized
    fun snapshotMemoryHealth(): OnlineTraceArchiveMemoryHealth {
        return OnlineTraceArchiveMemoryHealth(
            journalCount = journalsByMatchId.size,
            finalizedJournalCount = journalsByMatchId.values.count {
                    journal ->
                journal.finalSnapshotObserved
            },
            retainedJournalEntryCount = journalsByMatchId.values
                .sumOf { journal ->
                    journal.entries.size.toLong()
                },
            pendingRoomCount = pendingEntriesByRoomId.size,
            pendingEntryCount = pendingEntriesByRoomId.values
                .sumOf { entries ->
                    entries.size.toLong()
                },
            clientDeduplicationKeyCount =
                receivedClientEntryKeys.size,
            evictedJournalEntryCount =
                evictedJournalEntryCount,
            droppedPendingEntryCount =
                droppedPendingEntryCount,
            evictedFinalizedJournalCount =
                evictedFinalizedJournalCount,
            evictedClientDeduplicationKeyCount =
                evictedClientDeduplicationKeyCount,
            untrackedJournalEntryCount =
                untrackedJournalEntryCount,
        )
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
    val totalEntryCount: Long,
    val retainedEntryCount: Int,
    val evictedFromMemoryEntryCount: Long,
    val clientSessionIds: List<String>,
    val generatedAtEpochMillis: Long,
)
