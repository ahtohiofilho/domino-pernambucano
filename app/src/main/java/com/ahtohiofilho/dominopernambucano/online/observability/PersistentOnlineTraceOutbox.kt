package com.ahtohiofilho.dominopernambucano.online.observability

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PersistentOnlineTraceOutboxHealth(
    val queuedForPersistenceEntryCount: Int,
    val durablePendingEntryCount: Int,
    val consecutivePersistenceFailureCount: Int,
    val lastPersistenceFailureAtEpochMillis: Long?,
    val lastPersistenceFailureMessage: String?,
)

/*
 * Outbox privada por arquivo. Cada trace pendente recebe um arquivo próprio.
 *
 * record() apenas agenda I/O; uma entrada só fica disponível para upload
 * depois que o arquivo temporário é sincronizado e renomeado no diretório
 * da outbox.
 */
class PersistentOnlineTraceOutbox(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) : OnlineTraceOutbox {
    private val lock = Any()
    private val persistenceScope = CoroutineScope(
        SupervisorJob() + ioDispatcher,
    )
    private val persistenceMutex = Mutex()

    private val mutablePendingEntryVersion = MutableStateFlow(0L)
    private val mutableHealth = MutableStateFlow(
        PersistentOnlineTraceOutboxHealth(
            queuedForPersistenceEntryCount = 0,
            durablePendingEntryCount = 0,
            consecutivePersistenceFailureCount = 0,
            lastPersistenceFailureAtEpochMillis = null,
            lastPersistenceFailureMessage = null,
        ),
    )

    private val durableFilesByEntry =
        linkedMapOf<OnlineTraceEntry, File>()

    private val queuedEntries = ArrayDeque<OnlineTraceEntry>()

    private var nextSequence = 1L
    private var persistenceJob: Job? = null
    private var consecutivePersistenceFailureCount = 0
    private var lastPersistenceFailureAtEpochMillis: Long? = null
    private var lastPersistenceFailureMessage: String? = null
    private var restartRequestedAfterCurrentWorker = false

    override val pendingEntryVersion: StateFlow<Long> =
        mutablePendingEntryVersion.asStateFlow()

    val health: StateFlow<PersistentOnlineTraceOutboxHealth> =
        mutableHealth.asStateFlow()

    init {
        val loadResult = loadDurableEntries()

        synchronized(lock) {
            durableFilesByEntry.putAll(
                loadResult.filesByEntry,
            )
            nextSequence = loadResult.nextSequence

            loadResult.failure?.let { error ->
                registerPersistenceFailureLocked(error)
            }

            if (durableFilesByEntry.isNotEmpty()) {
                publishPendingEntryVersionLocked()
            }

            publishHealthLocked()
        }
    }

    override fun record(
        event: OnlineTraceEvent,
    ) {
        synchronized(lock) {
            queuedEntries.addLast(
                OnlineTraceEntry(
                    sequence = nextSequence++,
                    event = event,
                ),
            )

            publishHealthLocked()
            schedulePersistenceLocked()
        }
    }

    override fun pendingEntries(
        roomId: String,
        matchId: String,
        limit: Int,
    ): List<OnlineTraceEntry> {
        require(limit > 0) {
            "O limite de entradas pendentes deve ser maior que zero."
        }

        return synchronized(lock) {
            durableFilesByEntry.keys
                .asSequence()
                .filter { entry ->
                    val context = entry.event.context

                    context.roomId == roomId ||
                            context.matchId == matchId
                }
                .take(limit)
                .toList()
        }
    }

    override suspend fun acknowledge(
        entries: List<OnlineTraceEntry>,
    ) {
        if (entries.isEmpty()) {
            return
        }

        persistenceMutex.withLock {
            val filesToDelete = synchronized(lock) {
                entries.mapNotNull { entry ->
                    durableFilesByEntry[entry]
                }
            }

            if (filesToDelete.isEmpty()) {
                return
            }

            filesToDelete.forEach { file ->
                if (file.exists() && !file.delete()) {
                    throw IOException(
                        "Não foi possível confirmar o trace persistido.",
                    )
                }
            }

            synchronized(lock) {
                entries.forEach { entry ->
                    durableFilesByEntry.remove(entry)
                }
                clearPersistenceFailureLocked()
                publishPendingEntryVersionLocked()
                publishHealthLocked()
            }
        }
    }

    fun retryPendingPersistence() {
        synchronized(lock) {
            if (queuedEntries.isNotEmpty()) {
                schedulePersistenceLocked()
            }
        }
    }

    suspend fun awaitPendingPersistence() {
        while (true) {
            val currentJob = synchronized(lock) {
                persistenceJob
            }

            currentJob?.join()

            val isIdle = synchronized(lock) {
                persistenceJob == null ||
                        persistenceJob?.isCompleted == true
            }

            if (isIdle) {
                return
            }
        }
    }

    private fun schedulePersistenceLocked() {
        if (persistenceJob?.isActive == true) {
            return
        }

            restartRequestedAfterCurrentWorker = true
        persistenceJob = persistenceScope.launch {
            drainQueuedEntries()
        }
        startPersistenceWorkerLocked()
    }

    private fun startPersistenceWorkerLocked() {
        restartRequestedAfterCurrentWorker = false
    }

    private suspend fun drainQueuedEntries() {
        while (true) {
            val entriesToPersist = synchronized(lock) {
                if (queuedEntries.isEmpty()) {
                    persistenceJob = null
                    restartRequestedAfterCurrentWorker = false
                    null
                } else {
                    queuedEntries.take(
                        PERSISTENCE_BATCH_SIZE,
                    )
                }
            }

            if (entriesToPersist == null) {
                return
            }

            try {
                persistenceMutex.withLock {
                    entriesToPersist.forEach { entry ->
                        persistEntry(
                            entry = entry,
                        )
                    }
                }
            } catch (error: CancellationException) {
                synchronized(lock) {
                    persistenceJob = null
                }
                throw error
            } catch (error: Throwable) {
                synchronized(lock) {
                    registerPersistenceFailureLocked(error)
                    persistenceJob = null
                    publishHealthLocked()
                }
                return
            }

                    if (
                        restartRequestedAfterCurrentWorker &&
                        queuedEntries.isNotEmpty()
                    ) {
                        startPersistenceWorkerLocked()
                    }

            synchronized(lock) {
                entriesToPersist.forEach { expectedEntry ->
                    val queuedEntry = queuedEntries.pollFirst()

                    check(queuedEntry == expectedEntry) {
                        "A fila persistente de rastreamento perdeu a ordem."
                    }

                    durableFilesByEntry[expectedEntry] =
                        fileForEntry(expectedEntry)
                }

                clearPersistenceFailureLocked()
                publishPendingEntryVersionLocked()
                publishHealthLocked()
            }
        }
    }

    private fun persistEntry(
        entry: OnlineTraceEntry,
    ) {
        ensureDirectory()

        val targetFile = fileForEntry(entry)

        if (targetFile.isFile) {
            val existingEntry = json.decodeFromString<OnlineTraceEntry>(
                targetFile.readText(
                    StandardCharsets.UTF_8,
                ),
            )

            if (existingEntry == entry) {
                return
            }

            throw IOException(
                "A sequência do trace persistido colidiu com outra entrada.",
            )
        }

        val temporaryFile = File(
            directory,
            "${targetFile.name}.tmp",
        )

        FileOutputStream(
            temporaryFile,
            false,
        ).use { output ->
            output.write(
                json.encodeToString(entry).toByteArray(
                    StandardCharsets.UTF_8,
                ),
            )
            output.fd.sync()
        }

        if (!temporaryFile.renameTo(targetFile)) {
            throw IOException(
                "Não foi possível concluir a persistência do trace.",
            )
        }
    }

    private fun loadDurableEntries(): DurableLoadResult {
        if (!directory.exists()) {
            return DurableLoadResult(
                filesByEntry = emptyMap(),
                nextSequence = 1L,
                failure = null,
            )
        }

        return try {
            val filesByEntry = linkedMapOf<OnlineTraceEntry, File>()
            var largestSequence = 0L
            var firstFailure: Throwable? = null

            directory.listFiles()
                .orEmpty()
                .sortedBy { file ->
                    file.name
                }
                .forEach { file ->
                    when {
                        file.name.endsWith(TEMPORARY_FILE_SUFFIX) -> Unit

                        file.name.endsWith(ENTRY_FILE_SUFFIX) -> {
                            largestSequence = maxOf(
                                largestSequence,
                                file.sequenceFromFileName() ?: 0L,
                            )

                            try {
                                val entry = json.decodeFromString<OnlineTraceEntry>(
                                    file.readText(
                                        StandardCharsets.UTF_8,
                                    ),
                                )

                                filesByEntry[entry] = file
                                largestSequence = maxOf(
                                    largestSequence,
                                    entry.sequence,
                                )
                            } catch (error: Throwable) {
                                if (firstFailure == null) {
                                    firstFailure = error
                                }
                            }
                        }
                    }
                }

            DurableLoadResult(
                filesByEntry = filesByEntry,
                nextSequence = largestSequence + 1L,
                failure = firstFailure,
            )
        } catch (error: Throwable) {
            DurableLoadResult(
                filesByEntry = emptyMap(),
                nextSequence = 1L,
                failure = error,
            )
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(
                "Não foi possível criar o diretório da outbox de rastreamento.",
            )
        }
    }

    private fun fileForEntry(
        entry: OnlineTraceEntry,
    ): File {
        return File(
            directory,
            "trace-${entry.sequence.toString().padStart(20, '0')}$ENTRY_FILE_SUFFIX",
        )
    }

    private fun File.sequenceFromFileName(): Long? {
        return name
            .removePrefix("trace-")
            .removeSuffix(ENTRY_FILE_SUFFIX)
            .toLongOrNull()
    }

    private fun registerPersistenceFailureLocked(
        error: Throwable,
    ) {
        consecutivePersistenceFailureCount += 1
        lastPersistenceFailureAtEpochMillis = nowEpochMillis()
        lastPersistenceFailureMessage =
            error.message ?: error::class.java.simpleName
    }

    private fun clearPersistenceFailureLocked() {
        consecutivePersistenceFailureCount = 0
        lastPersistenceFailureAtEpochMillis = null
        lastPersistenceFailureMessage = null
    }

    private fun publishPendingEntryVersionLocked() {
        mutablePendingEntryVersion.value =
            mutablePendingEntryVersion.value + 1L
    }

    private fun publishHealthLocked() {
        mutableHealth.value = PersistentOnlineTraceOutboxHealth(
            queuedForPersistenceEntryCount = queuedEntries.size,
            durablePendingEntryCount = durableFilesByEntry.size,
            consecutivePersistenceFailureCount =
                consecutivePersistenceFailureCount,
            lastPersistenceFailureAtEpochMillis =
                lastPersistenceFailureAtEpochMillis,
            lastPersistenceFailureMessage =
                lastPersistenceFailureMessage,
        )
    }

    private data class DurableLoadResult(
        val filesByEntry: Map<OnlineTraceEntry, File>,
        val nextSequence: Long,
        val failure: Throwable?,
    )

    private companion object {
        const val PERSISTENCE_BATCH_SIZE = 64
        const val ENTRY_FILE_SUFFIX = ".trace.json"
        const val TEMPORARY_FILE_SUFFIX = ".tmp"
    }
}
