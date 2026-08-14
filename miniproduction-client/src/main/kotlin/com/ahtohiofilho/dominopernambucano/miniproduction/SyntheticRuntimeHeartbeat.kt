package com.ahtohiofilho.dominopernambucano.miniproduction

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal class SyntheticRuntimeHeartbeat(
    private val heartbeatFile: Path?,
) {
    init {
        require(heartbeatFile == null || heartbeatFile.isAbsolute) {
            "O arquivo de heartbeat deve possuir caminho absoluto."
        }
    }

    fun record(epochMillis: Long) {
        val target = heartbeatFile ?: return
        require(epochMillis > 0L) {
            "O heartbeat deve possuir instante válido."
        }

        val parent = requireNotNull(target.parent) {
            "O arquivo de heartbeat deve possuir diretório pai."
        }
        Files.createDirectories(parent)
        val temporaryFile = Files.createTempFile(
            parent,
            ".synthetic-heartbeat-",
            ".tmp",
        )

        try {
            Files.writeString(
                temporaryFile,
                "$epochMillis\n",
                StandardCharsets.US_ASCII,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    temporaryFile,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }
}

internal class SyntheticRuntimeLiveness(
    private val maxSilenceMillis: Long,
    private val heartbeat: (Long) -> Unit,
) {
    private var lastSuccessfulInteractionAtEpochMillis: Long? = null

    fun start(now: Long) {
        lastSuccessfulInteractionAtEpochMillis = now
        heartbeat(now)
    }

    fun recordSuccessfulInteraction(now: Long) {
        lastSuccessfulInteractionAtEpochMillis = now
    }

    fun assertHealthy(now: Long) {
        val lastSuccess = requireNotNull(
            lastSuccessfulInteractionAtEpochMillis,
        ) {
            "O monitor de atividade sintética não foi iniciado."
        }
        if (now < lastSuccess) {
            lastSuccessfulInteractionAtEpochMillis = now
            return
        }
        check(now - lastSuccess <= maxSilenceMillis) {
            "O servidor não confirmou interação sintética por " +
                "$maxSilenceMillis ms."
        }
    }

    fun recordHealthyHeartbeat(now: Long) {
        assertHealthy(now)
        heartbeat(now)
    }
}
