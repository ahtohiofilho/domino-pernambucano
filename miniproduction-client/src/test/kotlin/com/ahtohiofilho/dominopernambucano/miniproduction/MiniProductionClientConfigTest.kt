package com.ahtohiofilho.dominopernambucano.miniproduction

import java.net.URI
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MiniProductionClientConfigTest {
    @Test
    fun loopback_is_required_and_production_port_is_rejected() {
        val stateDirectory = Files.createTempDirectory("miniproduction-config")

        assertThrows(IllegalArgumentException::class.java) {
            MiniProductionClientConfig(
                baseUri = URI.create("https://example.com:18080"),
                stateDirectory = stateDirectory,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MiniProductionClientConfig(
                baseUri = URI.create("http://127.0.0.1:8080"),
                stateDirectory = stateDirectory,
            )
        }
    }

    @Test
    fun environment_defaults_to_fifteen_accounts_on_isolated_port() {
        val stateDirectory = Files.createTempDirectory("miniproduction-config")
        val values = mapOf(
            MINI_PRODUCTION_CLIENT_STATE_DIR_VARIABLE to
                stateDirectory.toString(),
        )

        val config = MiniProductionClientConfig.fromEnvironment(values::get)

        assertEquals("http://127.0.0.1:18080", config.normalizedBaseUrl)
        assertEquals(DEFAULT_SYNTHETIC_POPULATION_SIZE, config.populationSize)
    }
}
