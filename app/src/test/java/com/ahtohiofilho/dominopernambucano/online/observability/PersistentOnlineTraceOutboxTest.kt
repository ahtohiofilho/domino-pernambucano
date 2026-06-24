package com.ahtohiofilho.dominopernambucano.online.observability

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentOnlineTraceOutboxTest {
    @Test
    fun restores_durable_pending_entries_after_recreation() =
        runBlocking {
            val directory = Files.createTempDirectory(
                "online-trace-outbox-test",
            ).toFile()

            val firstOutbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )

            firstOutbox.record(
                event = event(
                    clientSessionId = "android-session-1",
                    roomId = "room-1",
                    matchId = "match-1",
                    type = OnlineTraceType.ACTION_SUBMITTED,
                ),
            )

            firstOutbox.awaitPendingPersistence()

            val recreatedOutbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )

            assertEquals(
                listOf(1L),
                recreatedOutbox.pendingEntries(
                    roomId = "room-1",
                    matchId = "match-1",
                    limit = 10,
                ).map { entry ->
                    entry.sequence
                },
            )
            assertTrue(
                recreatedOutbox.pendingEntryVersion.value > 0L,
            )
        }

    @Test
    fun persists_exact_acknowledgement_without_erasing_other_pending_entries() =
        runBlocking {
            val directory = Files.createTempDirectory(
                "online-trace-outbox-test",
            ).toFile()

            val firstOutbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )

            firstOutbox.record(
                event = event(
                    clientSessionId = "android-session-1",
                    roomId = "room-1",
                    matchId = "match-1",
                    type = OnlineTraceType.ACTION_SUBMITTED,
                ),
            )
            firstOutbox.awaitPendingPersistence()

            val recreatedOutbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )

            recreatedOutbox.record(
                event = event(
                    clientSessionId = "android-session-2",
                    roomId = "room-1",
                    matchId = "match-1",
                    type = OnlineTraceType.ACTION_ACCEPTED,
                ),
            )
            recreatedOutbox.awaitPendingPersistence()

            val firstPendingEntry = recreatedOutbox.pendingEntries(
                roomId = "room-1",
                matchId = "match-1",
                limit = 1,
            ).single()

            recreatedOutbox.acknowledge(
                entries = listOf(firstPendingEntry),
            )

            val finalOutbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )

            assertEquals(
                listOf(2L),
                finalOutbox.pendingEntries(
                    roomId = "room-1",
                    matchId = "match-1",
                    limit = 10,
                ).map { entry ->
                    entry.sequence
                },
            )
        }

    @Test
    fun retries_restored_entries_and_removes_them_only_after_acceptance() =
        runBlocking {
            val directory = Files.createTempDirectory(
                "online-trace-outbox-test",
            ).toFile()

            val firstOutbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )

            firstOutbox.record(
                event = event(
                    clientSessionId = "android-session-1",
                    roomId = "room-1",
                    matchId = "match-1",
                    type = OnlineTraceType.ACTION_SUBMITTED,
                ),
            )
            firstOutbox.awaitPendingPersistence()

            val recreatedOutbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )
            val repository = RecordingTraceRepository(
                failNextTraceBatch = true,
            )
            val uploader = OnlineTraceBatchUploader(
                repository = repository,
                traceOutbox = recreatedOutbox,
            )

            uploader.flushPendingEntries(
                roomId = "room-1",
                matchId = "match-1",
            )

            assertEquals(
                listOf(1L),
                recreatedOutbox.pendingEntries(
                    roomId = "room-1",
                    matchId = "match-1",
                    limit = 10,
                ).map { entry ->
                    entry.sequence
                },
            )

            uploader.flushPendingEntries(
                roomId = "room-1",
                matchId = "match-1",
            )

            val finalOutbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )

            assertTrue(
                finalOutbox.pendingEntries(
                    roomId = "room-1",
                    matchId = "match-1",
                    limit = 10,
                ).isEmpty(),
            )
            assertEquals(
                listOf(
                    listOf(1L),
                    listOf(1L),
                ),
                repository.receivedBatches.map { batch ->
                    batch.entries.map { entry ->
                        entry.sequence
                    }
                },
            )
        }


    @Test
    fun persists_all_entries_recorded_concurrently_while_the_worker_is_active() =
        runBlocking {
            val directory = Files.createTempDirectory(
                "online-trace-outbox-test",
            ).toFile()
            val outbox = PersistentOnlineTraceOutbox(
                directory = directory,
            )
            val started = CountDownLatch(1)

            val producer = Thread {
                started.await(
                    5L,
                    TimeUnit.SECONDS,
                )

                repeat(32) { index ->
                    outbox.record(
                        event(
                            clientSessionId = "android-session-1",
                            roomId = "room-1",
                            matchId = "match-1",
                            type = if (index % 2 == 0) {
                                OnlineTraceType.ACTION_SUBMITTED
                            } else {
                                OnlineTraceType.ACTION_ACCEPTED
                            },
                        ),
                    )
                }
            }

            producer.start()
            started.countDown()
            producer.join()
            outbox.awaitPendingPersistence()

            assertEquals(
                (1L..32L).toList(),
                outbox.pendingEntries(
                    roomId = "room-1",
                    matchId = "match-1",
                    limit = 64,
                ).map { entry ->
                    entry.sequence
                },
            )
        }

    private fun event(
        clientSessionId: String,
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
                clientSessionId = clientSessionId,
                roomId = roomId,
                matchId = matchId,
            ),
        )
    }

    private class RecordingTraceRepository(
        private var failNextTraceBatch: Boolean = false,
    ) : OnlineRoomRepository {
        private val mutableRoomSnapshot =
            MutableStateFlow<OnlineRoomSnapshotDto?>(null)
        private val mutableMatchSnapshot =
            MutableStateFlow<OnlineMatchSnapshotDto?>(null)

        val receivedBatches = mutableListOf<OnlineTraceBatchDto>()

        override val roomSnapshot: StateFlow<OnlineRoomSnapshotDto?> =
            mutableRoomSnapshot

        override val matchSnapshot: StateFlow<OnlineMatchSnapshotDto?> =
            mutableMatchSnapshot

        override val matchSnapshotEvents: Flow<OnlineMatchSnapshotDto>
            get() = kotlinx.coroutines.flow.emptyFlow()

        override suspend fun createRoom(
            request: CreateOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto = error("Não usado no teste.")

        override suspend fun joinRoom(
            request: JoinOnlineRoomRequestDto,
        ): OnlineRoomOperationResultDto = error("Não usado no teste.")

        override suspend fun submitAction(
            action: com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto,
        ): OnlineActionResultDto = error("Não usado no teste.")

        override suspend fun submitTraceBatch(
            batch: OnlineTraceBatchDto,
        ): OnlineTraceBatchResultDto {
            receivedBatches += batch

            if (failNextTraceBatch) {
                failNextTraceBatch = false

                throw IllegalStateException("Falha de transporte simulada.")
            }

            return OnlineTraceBatchResultDto(
                accepted = true,
                storedEntryCount = batch.entries.size,
            )
        }

        override suspend fun leaveRoom() = Unit
    }
}
