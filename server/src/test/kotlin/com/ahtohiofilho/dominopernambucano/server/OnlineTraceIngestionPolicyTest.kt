package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.ONLINE_TRACE_SCHEMA_VERSION
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEntry
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineTraceIngestionPolicyTest {
    @Test
    fun accepts_a_structurally_bounded_client_batch() {
        val reason = OnlineTraceIngestionPolicy.Default
            .rejectionReasonOrNull(
                batch = OnlineTraceBatchDto(
                    entries = listOf(
                        clientEntry(),
                    ),
                ),
            )

        assertNull(reason)
    }

    @Test
    fun rejects_empty_and_oversized_batches() {
        val policy = OnlineTraceIngestionPolicy.Default.copy(
            maxBatchEntryCount = 2,
        )

        assertTrue(
            policy.rejectionReasonOrNull(
                OnlineTraceBatchDto(
                    entries = emptyList(),
                ),
            )!!.contains("vazio"),
        )

        assertTrue(
            policy.rejectionReasonOrNull(
                OnlineTraceBatchDto(
                    entries = List(3) { index ->
                        clientEntry(
                            sequence = index + 1L,
                        )
                    },
                ),
            )!!.contains("excede"),
        )
    }

    @Test
    fun rejects_unknown_schema_and_unbounded_attributes() {
        val unknownSchemaEntry = clientEntry().let { entry ->
            entry.copy(
                event = entry.event.copy(
                    schemaVersion =
                        ONLINE_TRACE_SCHEMA_VERSION + 1,
                ),
            )
        }

        val schemaReason = OnlineTraceIngestionPolicy.Default
            .rejectionReasonOrNull(
                OnlineTraceBatchDto(
                    entries = listOf(unknownSchemaEntry),
                ),
            )

        assertTrue(
            schemaReason!!.contains("schemaVersion"),
        )

        val oversizedAttributeEntry = clientEntry().let { entry ->
            entry.copy(
                event = entry.event.copy(
                    attributes = mapOf(
                        "reason" to "x".repeat(257),
                    ),
                ),
            )
        }

        val attributeReason = OnlineTraceIngestionPolicy.Default
            .rejectionReasonOrNull(
                OnlineTraceBatchDto(
                    entries = listOf(oversizedAttributeEntry),
                ),
            )

        assertTrue(
            attributeReason!!.contains("Valor de atributo"),
        )
    }

    @Test
    fun archive_rejects_invalid_batch_without_creating_trace_files() {
        val reportRoot = Files.createTempDirectory(
            "online-trace-ingestion-policy-test",
        ).toFile()

        val archive = OnlineTraceArchive(
            outputDirectory = reportRoot,
            nowEpochMillis = { 2_000L },
        )

        val result = archive.recordClientBatch(
            OnlineTraceBatchDto(
                entries = listOf(
                    clientEntry().let { entry ->
                        entry.copy(
                            event = entry.event.copy(
                                context = entry.event.context.copy(
                                    clientSessionId =
                                        "x".repeat(161),
                                ),
                            ),
                        )
                    },
                ),
            ),
        )

        assertFalse(result.accepted)
        assertEquals(0, result.storedEntryCount)
        assertTrue(reportRoot.listFiles().orEmpty().isEmpty())
    }

    private fun clientEntry(
        sequence: Long = 1L,
    ): OnlineTraceEntry {
        return OnlineTraceEntry(
            sequence = sequence,
            event = OnlineTraceEvent(
                occurredAtEpochMillis = 1_000L,
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.CLIENT_COORDINATOR,
                type = OnlineTraceType.ACTION_SUBMITTED,
                context = OnlineTraceContext(
                    clientSessionId = "android-session-1",
                    roomId = "room-1",
                    matchId = "match-1",
                    playerId = "player-1",
                ),
                attributes = mapOf(
                    "operation" to "submit_action",
                ),
            ),
        )
    }
}
