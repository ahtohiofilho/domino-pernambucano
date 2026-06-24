package com.ahtohiofilho.dominopernambucano.online.observability

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineTraceBatchUploaderTest {
    @Test
    fun uploads_only_entries_of_the_active_room_or_match_in_bounded_batches() =
        runBlocking {
            val buffer = InMemoryOnlineTraceBuffer()
            val repository = RecordingTraceRepository()
            val uploader = OnlineTraceBatchUploader(
                repository = repository,
                traceBuffer = buffer,
                batchSize = 1,
            )

            buffer.record(
                clientEvent(
                    roomId = "room-1",
                    matchId = null,
                ),
            )
            buffer.record(
                clientEvent(
                    roomId = "room-1",
                    matchId = "match-1",
                ),
            )
            buffer.record(
                clientEvent(
                    roomId = "room-2",
                    matchId = "match-2",
                ),
            )

            uploader.flushPendingEntries(
                roomId = "room-1",
                matchId = "match-1",
            )

            assertEquals(
                listOf(
                    listOf(1L),
                    listOf(2L),
                ),
                repository.receivedBatches.map { batch ->
                    batch.entries.map { entry -> entry.sequence }
                },
            )

            val health = uploader.outboxHealth.value.getValue("match-1")

            assertEquals(2L, health.lastAcknowledgedSequence)
            assertEquals(0, health.pendingEntryCount)
            assertEquals(0, health.consecutiveFailureCount)
            assertNull(health.lastFailureKind)
            assertNull(health.lastFailureAtEpochMillis)
        }

    @Test
    fun keeps_entries_pending_when_transport_fails_and_recovers_on_retry() =
        runBlocking {
            var now = 1_000L
            val buffer = InMemoryOnlineTraceBuffer()
            val repository = RecordingTraceRepository(
                failNextTraceBatch = true,
            )
            val uploader = OnlineTraceBatchUploader(
                repository = repository,
                traceBuffer = buffer,
                nowEpochMillis = { now },
            )

            buffer.record(
                clientEvent(
                    roomId = "room-1",
                    matchId = "match-1",
                ),
            )

            uploader.flushPendingEntries(
                roomId = "room-1",
                matchId = "match-1",
            )

            val failedHealth = uploader.outboxHealth.value.getValue("match-1")

            assertEquals(0L, failedHealth.lastAcknowledgedSequence)
            assertEquals(1, failedHealth.pendingEntryCount)
            assertEquals(1, failedHealth.consecutiveFailureCount)
            assertEquals(
                OnlineTraceUploadFailureKind.TRANSPORT,
                failedHealth.lastFailureKind,
            )
            assertEquals(1_000L, failedHealth.lastFailureAtEpochMillis)

            now += 1L

            uploader.flushPendingEntries(
                roomId = "room-1",
                matchId = "match-1",
            )

            val recoveredHealth = uploader.outboxHealth.value.getValue("match-1")

            assertEquals(
                listOf(
                    listOf(1L),
                    listOf(1L),
                ),
                repository.receivedBatches.map { batch ->
                    batch.entries.map { entry -> entry.sequence }
                },
            )
            assertEquals(1L, recoveredHealth.lastAcknowledgedSequence)
            assertEquals(0, recoveredHealth.pendingEntryCount)
            assertEquals(0, recoveredHealth.consecutiveFailureCount)
            assertNull(recoveredHealth.lastFailureKind)
            assertNull(recoveredHealth.lastFailureAtEpochMillis)
        }

    private fun clientEvent(
        roomId: String,
        matchId: String?,
    ): OnlineTraceEvent {
        return OnlineTraceEvent(
            occurredAtEpochMillis = 1_000L,
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.CLIENT_UI,
            type = OnlineTraceType.ANIMATION_FINISHED,
            context = OnlineTraceContext(
                clientSessionId = "android-session",
                roomId = roomId,
                matchId = matchId,
            ),
        )
    }

    private class RecordingTraceRepository(
        private var failNextTraceBatch: Boolean = false,
    ) : OnlineRoomRepository {
        private val mutableRoomSnapshot = MutableStateFlow<OnlineRoomSnapshotDto?>(null)
        private val mutableMatchSnapshot = MutableStateFlow<OnlineMatchSnapshotDto?>(null)

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
