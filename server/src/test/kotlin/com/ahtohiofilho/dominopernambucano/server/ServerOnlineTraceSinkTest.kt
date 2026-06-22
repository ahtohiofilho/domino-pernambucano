package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerOnlineTraceSinkTest {
    @Test
    fun serializes_trace_event_as_a_single_structured_log_line() {
        val emittedLines = mutableListOf<String>()

        val sink = ServerOnlineTraceSink(
            emit = emittedLines::add,
        )

        val event = OnlineTraceEvent(
            occurredAtEpochMillis = 1_000L,
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.ACTION_ACCEPTED,
            context = OnlineTraceContext(
                roomId = "room-1",
                matchId = "match-1",
                actionId = "action-1",
                snapshotRevision = 2L,
            ),
            attributes = mapOf(
                "actionType" to "PLAY_MOVE",
            ),
        )

        sink.record(
            event = event,
        )

        val line = emittedLines.single()

        assertTrue(
            line.startsWith(SERVER_ONLINE_TRACE_PREFIX),
        )

        val decodedEvent = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<OnlineTraceEvent>(
            line.removePrefix(SERVER_ONLINE_TRACE_PREFIX),
        )

        assertEquals(event, decodedEvent)
    }
}
