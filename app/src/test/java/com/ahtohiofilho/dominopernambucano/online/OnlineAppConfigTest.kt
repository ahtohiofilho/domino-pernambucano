package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineAppConfigTest {
    @Test
    fun development_fake_backend_remains_available() {
        val config = OnlineAppConfig.fromBuildConfig(
            backendMode = "fake",
            backendBaseUrl = "",
            allowDevelopmentBackends = true,
        )

        assertEquals(
            OnlineBackendMode.FAKE,
            config.backendConfig.mode,
        )
        assertEquals(
            OnlineDebugOptions.FakeBackend,
            config.debugOptions,
        )
    }

    @Test
    fun development_remote_backend_without_url_uses_emulator_host() {
        val config = OnlineAppConfig.fromBuildConfig(
            backendMode = "remote",
            backendBaseUrl = "",
            allowDevelopmentBackends = true,
        )

        assertEquals(
            OnlineBackendMode.REMOTE,
            config.backendConfig.mode,
        )
        assertEquals(
            "http://10.0.2.2:8080",
            config.backendConfig.baseUrl,
        )
    }

    @Test
    fun production_accepts_explicit_https_remote_backend() {
        val config = OnlineAppConfig.fromBuildConfig(
            backendMode = "  ReMoTe  ",
            backendBaseUrl = "  https://domino.example.com/api  ",
            allowDevelopmentBackends = false,
        )

        assertEquals(
            OnlineBackendMode.REMOTE,
            config.backendConfig.mode,
        )
        assertEquals(
            "https://domino.example.com/api",
            config.backendConfig.baseUrl,
        )
        assertEquals(
            OnlineDebugOptions.RealBackend,
            config.debugOptions,
        )
    }

    @Test
    fun production_rejects_fake_backend() {
        assertThrows(IllegalStateException::class.java) {
            OnlineAppConfig.fromBuildConfig(
                backendMode = "fake",
                backendBaseUrl = "",
                allowDevelopmentBackends = false,
            )
        }
    }

    @Test
    fun production_rejects_missing_remote_backend_url() {
        assertThrows(IllegalStateException::class.java) {
            OnlineAppConfig.fromBuildConfig(
                backendMode = "remote",
                backendBaseUrl = "   ",
                allowDevelopmentBackends = false,
            )
        }
    }

    @Test
    fun production_rejects_non_https_remote_backend_url() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAppConfig.fromBuildConfig(
                backendMode = "remote",
                backendBaseUrl = "http://domino.example.com",
                allowDevelopmentBackends = false,
            )
        }
    }

    @Test
    fun production_rejects_known_development_hosts() {
        val developmentUrls = listOf(
            "https://localhost:8080",
            "https://127.0.0.1:8080",
            "https://0.0.0.0:8080",
            "https://10.0.2.2:8080",
        )

        developmentUrls.forEach { developmentUrl ->
            assertThrows(IllegalArgumentException::class.java) {
                OnlineAppConfig.fromBuildConfig(
                    backendMode = "remote",
                    backendBaseUrl = developmentUrl,
                    allowDevelopmentBackends = false,
                )
            }
        }
    }

    @Test
    fun malformed_remote_backend_url_is_rejected_in_production() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAppConfig.fromBuildConfig(
                backendMode = "remote",
                backendBaseUrl = "not a url",
                allowDevelopmentBackends = false,
            )
        }
    }

    @Test
    fun unknown_backend_mode_is_rejected_instead_of_falling_back_to_fake() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAppConfig.fromBuildConfig(
                backendMode = "remtoe",
                backendBaseUrl = "https://domino.example.com",
                allowDevelopmentBackends = true,
            )
        }
    }
}
