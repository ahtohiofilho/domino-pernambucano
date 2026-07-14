package com.ahtohiofilho.dominopernambucano.server

data class OnlineTraceArchiveMemoryPolicy(
    val maxJournalCount: Int = 32,
    val maxEntriesPerJournal: Int = 1_024,
    val maxPendingRoomCount: Int = 128,
    val maxPendingEntriesPerRoom: Int = 32,
    val maxClientDeduplicationKeyCount: Int = 16_384,
) {
    init {
        require(maxJournalCount > 0) {
            "O limite de journals deve ser positivo."
        }
        require(maxEntriesPerJournal > 0) {
            "O limite de eventos por journal deve ser positivo."
        }
        require(maxPendingRoomCount > 0) {
            "O limite de salas pendentes deve ser positivo."
        }
        require(maxPendingEntriesPerRoom > 0) {
            "O limite de eventos pendentes deve ser positivo."
        }
        require(maxClientDeduplicationKeyCount > 0) {
            "O limite de chaves de deduplicação deve ser positivo."
        }
    }

    companion object {
        val Default = OnlineTraceArchiveMemoryPolicy()
    }
}

data class OnlineTraceArchiveMemoryHealth(
    val journalCount: Int,
    val finalizedJournalCount: Int,
    val retainedJournalEntryCount: Long,
    val pendingRoomCount: Int,
    val pendingEntryCount: Long,
    val clientDeduplicationKeyCount: Int,
    val evictedJournalEntryCount: Long,
    val droppedPendingEntryCount: Long,
    val evictedFinalizedJournalCount: Long,
    val evictedClientDeduplicationKeyCount: Long,
    val untrackedJournalEntryCount: Long,
)