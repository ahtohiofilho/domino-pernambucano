package com.ahtohiofilho.dominopernambucano.miniproduction

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyntheticRuntimeHeartbeatTest {
    @Test
    fun heartbeat_is_atomically_replaced_without_credentials() {
        val root = Files.createTempDirectory("synthetic-heartbeat")
        val file = root.resolve("runtime-heartbeat")
        val heartbeat = SyntheticRuntimeHeartbeat(file)

        heartbeat.record(1_000L)
        heartbeat.record(2_000L)

        assertEquals(
            "2000\n",
            Files.readString(file, StandardCharsets.US_ASCII),
        )
        assertEquals(
            listOf("runtime-heartbeat"),
            Files.newDirectoryStream(root).use { paths ->
                paths.map { path -> path.fileName.toString() }.sorted()
            },
        )
    }

    @Test
    fun liveness_fails_after_the_configured_server_silence() {
        val heartbeats = mutableListOf<Long>()
        val liveness = SyntheticRuntimeLiveness(
            maxSilenceMillis = 30_000L,
            heartbeat = heartbeats::add,
        )

        liveness.start(1_000L)
        liveness.recordSuccessfulInteraction(2_000L)
        liveness.recordHealthyHeartbeat(31_999L)

        assertEquals(listOf(1_000L, 31_999L), heartbeats)
        assertThrows(IllegalStateException::class.java) {
            liveness.assertHealthy(32_001L)
        }
    }
}
