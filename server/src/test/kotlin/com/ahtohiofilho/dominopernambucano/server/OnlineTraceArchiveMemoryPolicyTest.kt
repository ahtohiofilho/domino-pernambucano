package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEntry
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceStateSummary
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineTraceArchiveMemoryPolicyTest {
    @Test
    fun journal_window_is_bounded_while_timeline_remains_complete() {
        val reportRoot = temporaryReportRoot()
        val archive = OnlineTraceArchive(
            outputDirectory = reportRoot,
            nowEpochMillis = { 1_000L },
            memoryPolicy = OnlineTraceArchiveMemoryPolicy(
                maxEntriesPerJournal = 2,
            ),
        )

        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = "match-1",
                revision = 1L,
            ),
        )
        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = "match-1",
                revision = 2L,
            ),
        )
        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = "match-1",
                revision = 3L,
                phase = "MATCH_FINISHED",
            ),
        )

        val matchDirectory = reportRoot.resolve("match-1")
        val timeline = matchDirectory.resolve("timeline.jsonl")
        val summary = matchDirectory.resolve("resumo.txt")
        val manifest = matchDirectory.resolve("manifest.json")
        val health = archive.snapshotMemoryHealth()

        assertEquals(3, timeline.readLines().size)
        assertTrue(
            summary.readText().contains(
                "EVENTOS PERSISTIDOS NO TIMELINE: 3",
            ),
        )
        assertTrue(
            summary.readText().contains(
                "EVENTOS FORA DA JANELA CONSOLIDADA: 1",
            ),
        )
        assertTrue(
            manifest.readText().contains(
                "\"totalEntryCount\":3",
            ),
        )
        assertTrue(
            manifest.readText().contains(
                "\"retainedEntryCount\":2",
            ),
        )
        assertEquals(2L, health.retainedJournalEntryCount)
        assertEquals(1L, health.evictedJournalEntryCount)
    }

    @Test
    fun journal_capacity_never_evicts_an_active_match() {
        val reportRoot = temporaryReportRoot()
        val archive = OnlineTraceArchive(
            outputDirectory = reportRoot,
            memoryPolicy = OnlineTraceArchiveMemoryPolicy(
                maxJournalCount = 1,
            ),
        )

        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = "match-1",
                revision = 1L,
            ),
        )
        archive.record(
            serverEvent(
                roomId = "room-2",
                matchId = "match-2",
                revision = 1L,
            ),
        )

        val saturatedHealth = archive.snapshotMemoryHealth()

        assertEquals(1, saturatedHealth.journalCount)
        assertEquals(
            0L,
            saturatedHealth.evictedFinalizedJournalCount,
        )
        assertEquals(
            1L,
            saturatedHealth.untrackedJournalEntryCount,
        )
        assertEquals(
            1,
            reportRoot
                .resolve("match-2")
                .resolve("timeline.jsonl")
                .readLines()
                .size,
        )

        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = "match-1",
                revision = 2L,
                phase = "MATCH_FINISHED",
            ),
        )
        archive.record(
            serverEvent(
                roomId = "room-3",
                matchId = "match-3",
                revision = 1L,
            ),
        )

        val recoveredHealth = archive.snapshotMemoryHealth()

        assertEquals(1, recoveredHealth.journalCount)
        assertEquals(0, recoveredHealth.finalizedJournalCount)
        assertEquals(
            1L,
            recoveredHealth.evictedFinalizedJournalCount,
        )
        assertTrue(
            reportRoot
                .resolve("match-1")
                .resolve("resumo.txt")
                .isFile,
        )
    }

    @Test
    fun pending_entries_are_bounded_without_deleting_retained_entries() {
        val reportRoot = temporaryReportRoot()
        val archive = OnlineTraceArchive(
            outputDirectory = reportRoot,
            memoryPolicy = OnlineTraceArchiveMemoryPolicy(
                maxPendingRoomCount = 1,
                maxPendingEntriesPerRoom = 2,
            ),
        )

        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = null,
                revision = 1L,
            ),
        )
        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = null,
                revision = 2L,
            ),
        )
        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = null,
                revision = 3L,
            ),
        )
        archive.record(
            serverEvent(
                roomId = "room-2",
                matchId = null,
                revision = 1L,
            ),
        )

        val saturatedHealth = archive.snapshotMemoryHealth()

        assertEquals(1, saturatedHealth.pendingRoomCount)
        assertEquals(2L, saturatedHealth.pendingEntryCount)
        assertEquals(2L, saturatedHealth.droppedPendingEntryCount)

        archive.record(
            serverEvent(
                roomId = "room-1",
                matchId = "match-1",
                revision = 4L,
            ),
        )

        val associatedHealth = archive.snapshotMemoryHealth()

        assertEquals(0, associatedHealth.pendingRoomCount)
        assertEquals(0L, associatedHealth.pendingEntryCount)
        assertEquals(
            3,
            reportRoot
                .resolve("match-1")
                .resolve("timeline.jsonl")
                .readLines()
                .size,
        )
    }

    @Test
    fun client_deduplication_uses_a_bounded_recent_window() {
        val reportRoot = temporaryReportRoot()
        val archive = OnlineTraceArchive(
            outputDirectory = reportRoot,
            memoryPolicy = OnlineTraceArchiveMemoryPolicy(
                maxClientDeduplicationKeyCount = 2,
            ),
        )

        fun submit(sequence: Long) =
            archive.recordClientBatch(
                OnlineTraceBatchDto(
                    entries = listOf(
                        clientEntry(sequence),
                    ),
                ),
            )

        assertEquals(1, submit(1L).storedEntryCount)
        assertEquals(1, submit(2L).storedEntryCount)
        assertEquals(1, submit(3L).storedEntryCount)
        assertEquals(0, submit(3L).storedEntryCount)
        assertEquals(1, submit(1L).storedEntryCount)

        val health = archive.snapshotMemoryHealth()

        assertEquals(2, health.clientDeduplicationKeyCount)
        assertEquals(
            2L,
            health.evictedClientDeduplicationKeyCount,
        )
        assertEquals(
            4,
            reportRoot
                .resolve("match-1")
                .resolve("timeline.jsonl")
                .readLines()
                .size,
        )
    }

    private fun temporaryReportRoot() =
        Files.createTempDirectory(
            "online-trace-archive-memory-test",
        ).toFile()

    private fun clientEntry(
        sequence: Long,
    ): OnlineTraceEntry {
        return OnlineTraceEntry(
            sequence = sequence,
            event = OnlineTraceEvent(
                occurredAtEpochMillis = 900L,
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.CLIENT_COORDINATOR,
                type = OnlineTraceType.PRESENTATION_FINISHED,
                context = OnlineTraceContext(
                    clientSessionId = "android-session-1",
                    roomId = "room-1",
                    matchId = "match-1",
                    snapshotRevision = sequence,
                ),
                attributes = mapOf(
                    "presentationId" to "r$sequence",
                ),
            ),
        )
    }

    private fun serverEvent(
        roomId: String,
        matchId: String?,
        revision: Long,
        phase: String? = null,
    ): OnlineTraceEvent {
        return OnlineTraceEvent(
            occurredAtEpochMillis = 800L,
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.SNAPSHOT_PUBLISHED,
            context = OnlineTraceContext(
                roomId = roomId,
                matchId = matchId,
                snapshotRevision = revision,
            ),
            state = phase?.let { currentPhase ->
                OnlineTraceStateSummary(
                    roundNumber = 1,
                    phase = currentPhase,
                    currentPlayerIndex = 0,
                    boardPieceCount = 28,
                    teamScores = listOf(6, 2),
                    playerClockMillis = listOf(
                        1L,
                        1L,
                        1L,
                        1L,
                    ),
                    automaticPlayerIndexes = emptyList(),
                )
            },
        )
    }
}