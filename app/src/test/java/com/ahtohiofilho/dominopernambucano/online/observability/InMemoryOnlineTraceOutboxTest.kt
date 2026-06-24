package com.ahtohiofilho.dominopernambucano.online.observability

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryOnlineTraceOutboxTest {
    @Test
    fun acknowledges_exact_entries_without_erasing_the_diagnostic_history() =
        runBlocking {
            val outbox = InMemoryOnlineTraceBuffer()

            outbox.record(
                event = event(
                    roomId = "room-1",
                    matchId = "match-1",
                    type = OnlineTraceType.ACTION_PREPARED,
                ),
            )
            outbox.record(
                event = event(
                    roomId = "room-1",
                    matchId = "match-1",
                    type = OnlineTraceType.ACTION_SUBMITTED,
                ),
            )
            outbox.record(
                event = event(
                    roomId = "room-2",
                    matchId = "match-2",
                    type = OnlineTraceType.ACTION_ACCEPTED,
                ),
            )

            val firstPendingEntry = outbox.pendingEntries(
                roomId = "room-1",
                matchId = "match-1",
                limit = 1,
            ).single()

            outbox.acknowledge(
                entries = listOf(firstPendingEntry),
            )

            assertEquals(
                listOf(2L),
                outbox.pendingEntries(
                    roomId = "room-1",
                    matchId = "match-1",
                    limit = 10,
                ).map { entry ->
                    entry.sequence
                },
            )
            assertEquals(
                listOf(1L, 2L, 3L),
                outbox.snapshot().map { entry ->
                    entry.sequence
                },
            )
        }

    private fun event(
        roomId: String,
        matchId: String,
        type: OnlineTraceType,
    ): OnlineTraceEvent {
        return OnlineTraceEvent(
            occurredAtEpochMillis = 1_000L,
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.CLIENT_COORDINATOR,
            type = type,
            context = OnlineTraceContext(
                clientSessionId = "android-session-1",
                roomId = roomId,
                matchId = matchId,
            ),
        )
    }
}
