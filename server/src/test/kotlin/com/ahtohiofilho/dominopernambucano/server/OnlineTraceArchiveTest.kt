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

class OnlineTraceArchiveTest {
    @Test
    fun final_match_generates_a_consolidated_report_and_deduplicates_client_retries() {
        val reportRoot = Files.createTempDirectory(
            "online-trace-archive-test",
        ).toFile()

        val archive = OnlineTraceArchive(
            outputDirectory = reportRoot,
            nowEpochMillis = { 1_000L },
        )

        archive.record(
            serverEvent(
                type = OnlineTraceType.ROOM_CREATED,
                roomId = "room-1",
                matchId = null,
                snapshotRevision = null,
                phase = null,
            ),
        )

        archive.record(
            serverEvent(
                type = OnlineTraceType.SNAPSHOT_PUBLISHED,
                roomId = "room-1",
                matchId = "match-1",
                snapshotRevision = 9L,
                phase = "MATCH_FINISHED",
            ),
        )

        val clientEntry = OnlineTraceEntry(
            sequence = 1L,
            event = OnlineTraceEvent(
                occurredAtEpochMillis = 900L,
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.CLIENT_COORDINATOR,
                type = OnlineTraceType.PRESENTATION_FINISHED,
                context = OnlineTraceContext(
                    clientSessionId = "android-session-1",
                    roomId = "room-1",
                    matchId = "match-1",
                    snapshotRevision = 9L,
                ),
                attributes = mapOf(
                    "presentationId" to "r9",
                ),
            ),
        )

        val firstResult = archive.recordClientBatch(
            OnlineTraceBatchDto(
                entries = listOf(clientEntry),
            ),
        )
        val retryResult = archive.recordClientBatch(
            OnlineTraceBatchDto(
                entries = listOf(clientEntry),
            ),
        )

        assertTrue(firstResult.accepted)
        assertEquals(1, firstResult.storedEntryCount)
        assertTrue(retryResult.accepted)
        assertEquals(0, retryResult.storedEntryCount)

        val matchDirectory = reportRoot.resolve("match-1")
        val timeline = matchDirectory.resolve("timeline.jsonl")
        val summary = matchDirectory.resolve("resumo.txt")
        val anomalies = matchDirectory.resolve("anomalias.txt")

        assertTrue(timeline.isFile)
        assertTrue(summary.isFile)
        assertTrue(anomalies.isFile)
        assertEquals(3, timeline.readLines().size)
        assertTrue(
            summary.readText().contains(
                "STATUS: FINALIZADA",
            ),
        )
        assertTrue(
            summary.readText().contains(
                "android-session-1",
            ),
        )
    }

    private fun serverEvent(
        type: OnlineTraceType,
        roomId: String,
        matchId: String?,
        snapshotRevision: Long?,
        phase: String?,
    ): OnlineTraceEvent {
        return OnlineTraceEvent(
            occurredAtEpochMillis = 800L,
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.SERVER_STORE,
            type = type,
            context = OnlineTraceContext(
                roomId = roomId,
                matchId = matchId,
                snapshotRevision = snapshotRevision,
            ),
            state = phase?.let { currentPhase ->
                OnlineTraceStateSummary(
                    roundNumber = 1,
                    phase = currentPhase,
                    currentPlayerIndex = 0,
                    boardPieceCount = 28,
                    teamScores = listOf(6, 2),
                    playerClockMillis = listOf(1L, 1L, 1L, 1L),
                    automaticPlayerIndexes = emptyList(),
                )
            },
        )
    }
}
