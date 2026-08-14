package com.ahtohiofilho.dominopernambucano.miniproduction

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MiniProductionClientConfigTest {
    @Test
    fun local_mode_requires_loopback_and_rejects_production_port() {
        val stateDirectory = Files.createTempDirectory("miniproduction-config")

        assertThrows(IllegalArgumentException::class.java) {
            MiniProductionClientConfig(
                baseUri = URI.create("https://example.com:18080"),
                stateDirectory = stateDirectory,
                syntheticProvisioningSecret = TEST_SECRET,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MiniProductionClientConfig(
                baseUri = URI.create("http://127.0.0.1:8080"),
                stateDirectory = stateDirectory,
                syntheticProvisioningSecret = TEST_SECRET,
            )
        }
    }

    @Test
    fun remote_mode_requires_non_loopback_https_on_the_standard_port() {
        val stateDirectory = Files.createTempDirectory("synthetic-config")

        assertThrows(IllegalArgumentException::class.java) {
            remoteConfig(
                baseUri = URI.create("http://play.example.com"),
                stateDirectory = stateDirectory,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            remoteConfig(
                baseUri = URI.create("https://127.0.0.1"),
                stateDirectory = stateDirectory,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            remoteConfig(
                baseUri = URI.create("https://play.example.com:8443"),
                stateDirectory = stateDirectory,
            )
        }

        val config = remoteConfig(
            baseUri = URI.create("https://play.example.com"),
            stateDirectory = stateDirectory,
        )

        assertEquals(
            SyntheticRuntimeTarget.REMOTE_PRODUCTION,
            config.runtimeTarget,
        )
        assertEquals("https://play.example.com", config.normalizedBaseUrl)
    }

    @Test
    fun environment_defaults_to_sixteen_accounts_on_isolated_local_port() {
        val stateDirectory = Files.createTempDirectory("miniproduction-config")
        val values = mapOf(
            MINI_PRODUCTION_CLIENT_STATE_DIR_VARIABLE to
                stateDirectory.toString(),
            SYNTHETIC_PROVISIONING_SECRET_VARIABLE to TEST_SECRET,
        )

        val config = MiniProductionClientConfig.fromEnvironment(values::get)

        assertEquals("http://127.0.0.1:18080", config.normalizedBaseUrl)
        assertEquals(DEFAULT_SYNTHETIC_POPULATION_SIZE, config.populationSize)
        assertEquals(
            SyntheticRuntimeTarget.LOCAL_MINIPRODUCTION,
            config.runtimeTarget,
        )
    }

    @Test
    fun remote_environment_requires_explicit_remote_url_and_state() {
        val targetOnly = mapOf(
            SYNTHETIC_RUNTIME_TARGET_VARIABLE to "REMOTE_PRODUCTION",
            SYNTHETIC_PROVISIONING_SECRET_VARIABLE to TEST_SECRET,
        )
        assertThrows(IllegalStateException::class.java) {
            MiniProductionClientConfig.fromEnvironment(targetOnly::get)
        }

        val stateDirectory = Files.createTempDirectory("synthetic-config")
        val heartbeatFile = stateDirectory.resolve("runtime-heartbeat")
        val values = targetOnly + mapOf(
            SYNTHETIC_BASE_URL_VARIABLE to "https://play.example.com/",
            SYNTHETIC_CLIENT_STATE_DIR_VARIABLE to stateDirectory.toString(),
            SYNTHETIC_HEARTBEAT_FILE_VARIABLE to heartbeatFile.toString(),
        )

        val config = MiniProductionClientConfig.fromEnvironment(values::get)

        assertEquals("https://play.example.com", config.normalizedBaseUrl)
        assertEquals(stateDirectory, config.stateDirectory)
        assertEquals(heartbeatFile, config.heartbeatFile)
        assertEquals(DEFAULT_SYNTHETIC_POPULATION_SIZE, config.populationSize)
    }

    @Test
    fun remote_environment_reads_the_secret_from_a_restricted_file_contract() {
        val stateDirectory = Files.createTempDirectory("synthetic-config")
        val secretFile = stateDirectory.resolve("provisioning-secret")
        Files.writeString(secretFile, "$TEST_SECRET\n", StandardCharsets.UTF_8)
        val values = mapOf(
            SYNTHETIC_RUNTIME_TARGET_VARIABLE to "REMOTE_PRODUCTION",
            SYNTHETIC_BASE_URL_VARIABLE to "https://play.example.com",
            SYNTHETIC_CLIENT_STATE_DIR_VARIABLE to stateDirectory.toString(),
            SYNTHETIC_PROVISIONING_SECRET_FILE_VARIABLE to
                secretFile.toString(),
        )

        val config = MiniProductionClientConfig.fromEnvironment(values::get)

        assertEquals(TEST_SECRET, config.syntheticProvisioningSecret)
    }

    private fun remoteConfig(
        baseUri: URI,
        stateDirectory: java.nio.file.Path,
    ) = MiniProductionClientConfig(
        baseUri = baseUri,
        stateDirectory = stateDirectory,
        syntheticProvisioningSecret = TEST_SECRET,
        runtimeTarget = SyntheticRuntimeTarget.REMOTE_PRODUCTION,
    )

    private companion object {
        const val TEST_SECRET =
            "0123456789abcdef0123456789abcdef"
    }
}
