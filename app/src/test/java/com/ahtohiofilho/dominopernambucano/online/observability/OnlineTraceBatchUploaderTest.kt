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

    private class RecordingTraceRepository : OnlineRoomRepository {
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

            return OnlineTraceBatchResultDto(
                accepted = true,
                storedEntryCount = batch.entries.size,
            )
        }

        override suspend fun leaveRoom() = Unit
    }
}
