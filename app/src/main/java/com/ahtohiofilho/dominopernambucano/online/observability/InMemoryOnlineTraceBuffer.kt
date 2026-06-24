package com.ahtohiofilho.dominopernambucano.online.observability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnlineTraceBufferHealth(
    val capacity: Int,
    val retainedEntryCount: Int,
    val totalRecordedEntryCount: Long,
    val droppedEntryCount: Long,
    val oldestRetainedSequence: Long?,
    val newestRetainedSequence: Long?,
)

class InMemoryOnlineTraceBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) : OnlineTraceSink {
    init {
        require(capacity > 0) {
            "A capacidade do buffer de rastreamento deve ser maior que zero."
        }
    }

    private val mutableEntries =
        MutableStateFlow<List<OnlineTraceEntry>>(emptyList())

    private val mutableHealth = MutableStateFlow(
        OnlineTraceBufferHealth(
            capacity = capacity,
            retainedEntryCount = 0,
            totalRecordedEntryCount = 0L,
            droppedEntryCount = 0L,
            oldestRetainedSequence = null,
            newestRetainedSequence = null,
        ),
    )

    private var nextSequence = 1L
    private var totalRecordedEntryCount = 0L
    private var droppedEntryCount = 0L

    val entries: StateFlow<List<OnlineTraceEntry>> =
        mutableEntries.asStateFlow()

    val health: StateFlow<OnlineTraceBufferHealth> =
        mutableHealth.asStateFlow()

    @Synchronized
    override fun record(
        event: OnlineTraceEvent,
    ) {
        val newEntry = OnlineTraceEntry(
            sequence = nextSequence++,
            event = event,
        )

        val entriesBeforeCapacityLimit = mutableEntries.value + newEntry
        val droppedEntryCountForRecord =
            (entriesBeforeCapacityLimit.size - capacity).coerceAtLeast(0)

        val updatedEntries = entriesBeforeCapacityLimit
            .takeLast(capacity)

        totalRecordedEntryCount += 1L
        droppedEntryCount += droppedEntryCountForRecord.toLong()
        mutableEntries.value = updatedEntries
        publishHealth(updatedEntries)
    }

    @Synchronized
    fun snapshot(): List<OnlineTraceEntry> {
        return mutableEntries.value
    }

    @Synchronized
    fun entriesForMatch(
        matchId: String,
    ): List<OnlineTraceEntry> {
        return mutableEntries.value.filter { entry ->
            entry.event.context.matchId == matchId
        }
    }

    @Synchronized
    fun entriesForRoomOrMatch(
        roomId: String,
        matchId: String,
    ): List<OnlineTraceEntry> {
        return mutableEntries.value.filter { entry ->
            val context = entry.event.context

            context.roomId == roomId ||
                    context.matchId == matchId
        }
    }

    @Synchronized
    fun clear() {
        mutableEntries.value = emptyList()
        nextSequence = 1L
        totalRecordedEntryCount = 0L
        droppedEntryCount = 0L
        publishHealth(emptyList())
    }

    private fun publishHealth(
        entries: List<OnlineTraceEntry>,
    ) {
        mutableHealth.value = OnlineTraceBufferHealth(
            capacity = capacity,
            retainedEntryCount = entries.size,
            totalRecordedEntryCount = totalRecordedEntryCount,
            droppedEntryCount = droppedEntryCount,
            oldestRetainedSequence = entries.firstOrNull()?.sequence,
            newestRetainedSequence = entries.lastOrNull()?.sequence,
        )
    }

    private companion object {
        const val DEFAULT_CAPACITY = 4_000
    }
}