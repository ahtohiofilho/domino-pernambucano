package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val ONLINE_SERVER_STATE_SCHEMA_VERSION = 1
const val ONLINE_SERVER_STATE_FILE_NAME = "online-server-state.json"

interface OnlineServerStateStore {
    fun load(): OnlineServerStateLoadResult

    fun save(
        state: OnlineServerPersistentState,
    )
}

sealed interface OnlineServerStateLoadResult {
    data object Empty : OnlineServerStateLoadResult

    data class Recovered(
        val state: OnlineServerPersistentState,
    ) : OnlineServerStateLoadResult

    data class Invalid(
        val reason: String,
    ) : OnlineServerStateLoadResult
}

@Serializable
data class OnlineServerPersistentState(
    val schemaVersion: Int = ONLINE_SERVER_STATE_SCHEMA_VERSION,
    val nextRoomSequence: Int = 1,
    val nextMatchSequence: Int = 1,
    val rooms: List<OnlineRoomSnapshotDto> = emptyList(),
    val matches: List<OnlineServerPersistentMatchRecord> = emptyList(),
    val actionResults: List<OnlineServerPersistentActionResult> = emptyList(),
)

@Serializable
data class OnlineServerPersistentMatchRecord(
    val roomId: String,
    val matchId: String,
    val snapshot: OnlineMatchSnapshotDto,
    val revisionHistory: List<OnlineMatchSnapshotDto>,
    val automaticSeatIndexes: List<Int>,
    val developmentBotSeatIndexes: List<Int>,
)

@Serializable
data class OnlineServerPersistentActionResult(
    val matchId: String,
    val playerId: String,
    val actionId: String,
    val result: OnlineActionResultDto,
)

class JsonFileOnlineServerStateStore(
    private val stateDirectory: Path,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    },
) : OnlineServerStateStore {
    val stateFile: Path = stateDirectory.resolve(
        ONLINE_SERVER_STATE_FILE_NAME,
    )

    override fun load(): OnlineServerStateLoadResult {
        if (!Files.exists(stateFile)) {
            return OnlineServerStateLoadResult.Empty
        }

        return try {
            val state = json.decodeFromString<OnlineServerPersistentState>(
                Files.readString(stateFile),
            )

            if (state.schemaVersion != ONLINE_SERVER_STATE_SCHEMA_VERSION) {
                OnlineServerStateLoadResult.Invalid(
                    reason = "Versão de estado não suportada: " +
                            state.schemaVersion,
                )
            } else {
                OnlineServerStateLoadResult.Recovered(
                    state = state,
                )
            }
        } catch (exception: Exception) {
            OnlineServerStateLoadResult.Invalid(
                reason = "Não foi possível ler o estado persistido: " +
                        exception.javaClass.simpleName,
            )
        }
    }

    override fun save(
        state: OnlineServerPersistentState,
    ) {
        Files.createDirectories(stateDirectory)

        val temporaryFile = stateFile.resolveSibling(
            "$ONLINE_SERVER_STATE_FILE_NAME.tmp",
        )

        Files.writeString(
            temporaryFile,
            json.encodeToString(state),
            CREATE,
            TRUNCATE_EXISTING,
            WRITE,
        )

        try {
            Files.move(
                temporaryFile,
                stateFile,
                ATOMIC_MOVE,
                REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile,
                stateFile,
                REPLACE_EXISTING,
            )
        }
    }
}
