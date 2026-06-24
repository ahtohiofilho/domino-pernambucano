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
) : OnlineTraceOutbox {
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

    private val acknowledgedEntries = mutableSetOf<OnlineTraceEntry>()

    private val mutablePendingEntryVersion = MutableStateFlow(0L)

    private var nextSequence = 1L
    private var totalRecordedEntryCount = 0L
    private var droppedEntryCount = 0L

    val entries: StateFlow<List<OnlineTraceEntry>> =
        mutableEntries.asStateFlow()

    val health: StateFlow<OnlineTraceBufferHealth> =
        mutableHealth.asStateFlow()

    override val pendingEntryVersion: StateFlow<Long> =
        mutablePendingEntryVersion.asStateFlow()

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
        acknowledgedEntries.retainAll(updatedEntries.toSet())
        publishHealth(updatedEntries)
        publishPendingEntryVersion()
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
    override fun pendingEntries(
        roomId: String,
        matchId: String,
        limit: Int,
    ): List<OnlineTraceEntry> {
        require(limit > 0) {
            "O limite de entradas pendentes deve ser maior que zero."
        }

        return entriesForRoomOrMatch(
            roomId = roomId,
            matchId = matchId,
        ).asSequence()
            .filterNot { entry ->
                entry in acknowledgedEntries
            }
            .take(limit)
            .toList()
    }

    override suspend fun acknowledge(
        entries: List<OnlineTraceEntry>,
    ) {
        if (entries.isEmpty()) {
            return
        }

        synchronized(this) {
            acknowledgedEntries += entries
            acknowledgedEntries.retainAll(
                mutableEntries.value.toSet(),
            )
            publishPendingEntryVersion()
        }
    }

    @Synchronized
    fun clear() {
        mutableEntries.value = emptyList()
        acknowledgedEntries.clear()
        nextSequence = 1L
        totalRecordedEntryCount = 0L
        droppedEntryCount = 0L
        publishHealth(emptyList())
        publishPendingEntryVersion()
    }

    private fun publishPendingEntryVersion() {
        mutablePendingEntryVersion.value =
            mutablePendingEntryVersion.value + 1L
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