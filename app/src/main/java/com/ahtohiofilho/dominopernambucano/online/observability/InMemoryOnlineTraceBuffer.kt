package com.ahtohiofilho.dominopernambucano.online.observability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private var nextSequence = 1L

    val entries: StateFlow<List<OnlineTraceEntry>> =
        mutableEntries.asStateFlow()

    @Synchronized
    override fun record(
        event: OnlineTraceEvent,
    ) {
        val newEntry = OnlineTraceEntry(
            sequence = nextSequence++,
            event = event,
        )

        val updatedEntries = (mutableEntries.value + newEntry)
            .takeLast(capacity)

        mutableEntries.value = updatedEntries
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
    }

    private companion object {
        const val DEFAULT_CAPACITY = 4_000
    }
}