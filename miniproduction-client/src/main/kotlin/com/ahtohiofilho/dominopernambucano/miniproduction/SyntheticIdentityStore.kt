package com.ahtohiofilho.dominopernambucano.miniproduction

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SYNTHETIC_IDENTITY_SCHEMA_VERSION = 1

@Serializable
internal data class SyntheticAccountCredential(
    val profileIndex: Int,
    val accountId: String,
    val playerId: String,
    val accessToken: String,
    val expiresAtEpochMillis: Long,
)

@Serializable
private data class SyntheticIdentityState(
    val schemaVersion: Int = SYNTHETIC_IDENTITY_SCHEMA_VERSION,
    val baseUrl: String,
    val accounts: List<SyntheticAccountCredential> = emptyList(),
)

internal class SyntheticIdentityStore(
    private val stateDirectory: Path,
    private val baseUrl: String,
    private val json: Json = Json {
        encodeDefaults = true
        prettyPrint = true
    },
) {
    private val stateFile = stateDirectory.resolve("synthetic-identities.json")
    private val lockFile = stateDirectory.resolve("synthetic-identities.lock")

    init {
        require(stateDirectory.isAbsolute) {
            "O diretório de identidades deve ser absoluto."
        }
    }

    fun acquireExclusiveRunLock(): AutoCloseable {
        Files.createDirectories(stateDirectory)
        val channel = FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }

        if (lock == null) {
            channel.close()
            throw IllegalStateException(
                "Já existe uma população sintética usando este diretório.",
            )
        }

        return SyntheticRunLock(
            lock = lock,
            channel = channel,
        )
    }

    fun load(): List<SyntheticAccountCredential> {
        if (!Files.isRegularFile(stateFile)) {
            return emptyList()
        }

        val state = runCatching {
            json.decodeFromString<SyntheticIdentityState>(
                Files.readString(stateFile, StandardCharsets.UTF_8),
            )
        }.getOrElse { cause ->
            throw IllegalStateException(
                "O arquivo de identidades sintéticas é inválido.",
                cause,
            )
        }

        require(state.schemaVersion == SYNTHETIC_IDENTITY_SCHEMA_VERSION) {
            "Versão de identidades sintéticas incompatível."
        }
        require(state.baseUrl == baseUrl) {
            "O estado dos clientes pertence a outro backend."
        }
        require(
            state.accounts.map { account -> account.profileIndex }
                .distinct().size == state.accounts.size,
        ) {
            "O estado contém perfis sintéticos duplicados."
        }
        require(
            state.accounts.map { account -> account.accountId }
                .distinct().size == state.accounts.size,
        ) {
            "O estado contém contas sintéticas duplicadas."
        }

        return state.accounts.sortedBy { account -> account.profileIndex }
    }

    fun save(
        accounts: List<SyntheticAccountCredential>,
    ) {
        require(
            accounts.map { account -> account.profileIndex }
                .distinct().size == accounts.size,
        ) {
            "Não é permitido persistir perfis sintéticos duplicados."
        }
        require(
            accounts.map { account -> account.accountId }
                .distinct().size == accounts.size,
        ) {
            "Não é permitido persistir contas sintéticas duplicadas."
        }
        require(
            accounts.map { account -> account.playerId }
                .distinct().size == accounts.size,
        ) {
            "Não é permitido persistir jogadores sintéticos duplicados."
        }
        require(
            accounts.all { account ->
                account.profileIndex > 0 &&
                    account.accountId.isNotBlank() &&
                    account.playerId.isNotBlank() &&
                    account.accessToken.isNotBlank() &&
                    account.expiresAtEpochMillis > 0L
            },
        ) {
            "Uma credencial sintética é inválida."
        }

        Files.createDirectories(stateDirectory)
        val state = SyntheticIdentityState(
            baseUrl = baseUrl,
            accounts = accounts.sortedBy { account -> account.profileIndex },
        )
        val temporaryFile = Files.createTempFile(
            stateDirectory,
            "synthetic-identities-",
            ".tmp",
        )

        try {
            Files.writeString(
                temporaryFile,
                json.encodeToString(state),
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    temporaryFile,
                    stateFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile,
                    stateFile,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }
}

private class SyntheticRunLock(
    private val lock: FileLock,
    private val channel: FileChannel,
) : AutoCloseable {
    override fun close() {
        try {
            lock.release()
        } finally {
            channel.close()
        }
    }
}
