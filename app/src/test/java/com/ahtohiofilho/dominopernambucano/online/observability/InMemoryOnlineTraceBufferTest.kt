package com.ahtohiofilho.dominopernambucano.online.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOnlineTraceBufferTest {
    @Test
    fun keeps_only_the_most_recent_entries_within_capacity() {
        var now = 1_000L

        val buffer = InMemoryOnlineTraceBuffer(
            capacity = 2,
        )

        val logger = OnlineTraceLogger(
            sink = buffer,
            nowEpochMillis = { now },
        )

        logger.log(
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.CLIENT_COORDINATOR,
            type = OnlineTraceType.ACTION_PREPARED,
            context = OnlineTraceContext(
                matchId = "match-1",
            ),
        )

        now += 1L

        logger.log(
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.CLIENT_COORDINATOR,
            type = OnlineTraceType.ACTION_SUBMITTED,
            context = OnlineTraceContext(
                matchId = "match-1",
            ),
        )

        now += 1L

        logger.log(
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.CLIENT_COORDINATOR,
            type = OnlineTraceType.ACTION_ACCEPTED,
            context = OnlineTraceContext(
                matchId = "match-1",
            ),
        )

        val entries = buffer.snapshot()

        assertEquals(2, entries.size)
        assertEquals(2L, entries[0].sequence)
        assertEquals(3L, entries[1].sequence)
        assertEquals(
            OnlineTraceType.ACTION_SUBMITTED,
            entries[0].event.type,
        )
        assertEquals(
            OnlineTraceType.ACTION_ACCEPTED,
            entries[1].event.type,
        )
    }

    @Test
    fun logger_injects_the_client_session_id_when_the_event_context_does_not_set_one() {
        val buffer = InMemoryOnlineTraceBuffer()
        val logger = OnlineTraceLogger(
            sink = buffer,
            clientSessionId = "android-test-session",
        )

        logger.log(
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.CLIENT_UI,
            type = OnlineTraceType.UI_MOVE_INTENT_RECEIVED,
            context = OnlineTraceContext(
                matchId = "match-1",
            ),
        )

        assertEquals(
            "android-test-session",
            buffer.snapshot().single().event.context.clientSessionId,
        )
    }

    @Test
    fun filters_entries_by_match_and_resets_cleanly() {
        val buffer = InMemoryOnlineTraceBuffer()

        buffer.record(
            event = createEvent(
                matchId = "match-1",
                type = OnlineTraceType.SNAPSHOT_RECEIVED,
            )
        )

        buffer.record(
            event = createEvent(
                matchId = "match-2",
                type = OnlineTraceType.SNAPSHOT_PUBLISHED,
            )
        )

        val matchOneEntries = buffer.entriesForMatch(
            matchId = "match-1",
        )

        assertEquals(1, matchOneEntries.size)
        assertEquals(
            OnlineTraceType.SNAPSHOT_RECEIVED,
            matchOneEntries.single().event.type,
        )

        buffer.clear()

        assertTrue(
            buffer.snapshot().isEmpty()
        )

        buffer.record(
            event = createEvent(
                matchId = "match-3",
                type = OnlineTraceType.POLLING_STARTED,
            )
        )

        assertEquals(
            1L,
            buffer.snapshot().single().sequence,
        )
    }

    private fun createEvent(
        matchId: String,
        type: OnlineTraceType,
    ): OnlineTraceEvent {
        return OnlineTraceEvent(
            occurredAtEpochMillis = 1_000L,
            level = OnlineTraceLevel.DEBUG,
            source = OnlineTraceSource.CLIENT_REPOSITORY,
            type = type,
            context = OnlineTraceContext(
                matchId = matchId,
            ),
        )
    }
}