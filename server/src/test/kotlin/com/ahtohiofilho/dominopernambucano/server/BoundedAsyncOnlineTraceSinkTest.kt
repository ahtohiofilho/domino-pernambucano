package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAsyncOnlineTraceSinkTest {
    @Test
    fun keeps_authoritative_callers_non_blocking_and_bounds_the_queue() =
        runBlocking {
            val delegateStarted = CountDownLatch(1)
            val releaseDelegate = CountDownLatch(1)
            val forwardedTypes = Collections.synchronizedList(
                mutableListOf<OnlineTraceType>(),
            )

            val sink = BoundedAsyncOnlineTraceSink(
                delegate = OnlineTraceSink { event ->
                    delegateStarted.countDown()

                    check(
                        releaseDelegate.await(
                            5L,
                            TimeUnit.SECONDS,
                        ),
                    )

                    forwardedTypes += event.type
                },
                capacity = 1,
                dispatcher = Dispatchers.IO,
            )

            sink.record(
                event(OnlineTraceType.ACTION_ACCEPTED),
            )

            assertTrue(
                delegateStarted.await(
                    5L,
                    TimeUnit.SECONDS,
                ),
            )

            sink.record(
                event(OnlineTraceType.SNAPSHOT_PUBLISHED),
            )
            sink.record(
                event(OnlineTraceType.AUTHORITATIVE_TICK),
            )

            val saturatedHealth = sink.health.value

            assertEquals(2L, saturatedHealth.acceptedEntryCount)
            assertEquals(2, saturatedHealth.pendingEntryCount)
            assertEquals(1L, saturatedHealth.droppedEntryCount)

            releaseDelegate.countDown()
            sink.shutdown()

            assertEquals(
                listOf(
                    OnlineTraceType.ACTION_ACCEPTED,
                    OnlineTraceType.SNAPSHOT_PUBLISHED,
                ),
                forwardedTypes.toList(),
            )

            val finalHealth = sink.health.value

            assertFalse(finalHealth.acceptingEntries)
            assertEquals(0, finalHealth.pendingEntryCount)
            assertEquals(2L, finalHealth.forwardedEntryCount)
            assertEquals(1L, finalHealth.droppedEntryCount)
        }

    @Test
    fun contains_delegate_failure_and_continues_forwarding() =
        runBlocking {
            val invocationCount = AtomicInteger(0)
            val forwardedTypes = mutableListOf<OnlineTraceType>()

            val sink = BoundedAsyncOnlineTraceSink(
                delegate = OnlineTraceSink { event ->
                    if (invocationCount.incrementAndGet() == 1) {
                        throw IllegalStateException(
                            "Falha de armazenamento simulada.",
                        )
                    }

                    forwardedTypes += event.type
                },
                capacity = 4,
                dispatcher = Dispatchers.IO,
                nowEpochMillis = { 9_000L },
            )

            sink.record(
                event(OnlineTraceType.ACTION_ACCEPTED),
            )
            sink.record(
                event(OnlineTraceType.SNAPSHOT_PUBLISHED),
            )

            sink.shutdown()

            assertEquals(
                listOf(OnlineTraceType.SNAPSHOT_PUBLISHED),
                forwardedTypes,
            )

            val health = sink.health.value

            assertEquals(0, health.pendingEntryCount)
            assertEquals(1L, health.failedEntryCount)
            assertEquals(1L, health.forwardedEntryCount)
            assertEquals(9_000L, health.lastFailureAtEpochMillis)
            assertTrue(
                health.lastFailureMessage!!.contains(
                    "Falha de armazenamento",
                ),
            )
        }

    @Test
    fun records_after_shutdown_are_dropped_without_reopening_worker() =
        runBlocking {
            val sink = BoundedAsyncOnlineTraceSink(
                delegate = OnlineTraceSink {
                    error("Nenhum evento deveria ser encaminhado.")
                },
                capacity = 1,
                dispatcher = Dispatchers.IO,
            )

            sink.shutdown()
            sink.record(
                event(OnlineTraceType.AUTHORITATIVE_TICK),
            )

            val health = sink.health.value

            assertFalse(health.acceptingEntries)
            assertEquals(0L, health.acceptedEntryCount)
            assertEquals(1L, health.droppedEntryCount)
        }

    private fun event(
        type: OnlineTraceType,
    ): OnlineTraceEvent {
        return OnlineTraceEvent(
            occurredAtEpochMillis = 1_000L,
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.SERVER_STORE,
            type = type,
            context = OnlineTraceContext(
                roomId = "room-1",
                matchId = "match-1",
            ),
        )
    }
}
