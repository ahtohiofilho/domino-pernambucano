package com.ahtohiofilho.dominopernambucano.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineServerEnvironmentTest {
    @Test
    fun missing_environment_is_rejected() {
        assertThrows(
            IllegalStateException::class.java,
        ) {
            resolveOnlineServerEnvironment(
                readEnvironmentVariable = { null },
            )
        }
    }

    @Test
    fun unknown_environment_is_rejected() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            resolveOnlineServerEnvironment(
                readEnvironmentVariable = { "staging-unknown" },
            )
        }
    }

    @Test
    fun configured_environment_is_normalized() {
        assertEquals(
            OnlineServerEnvironment.PRODUCTION,
            resolveOnlineServerEnvironment(
                readEnvironmentVariable = { "  PrOdUcTiOn  " },
            ),
        )
    }

    @Test
    fun development_capabilities_are_disabled_in_production() {
        assertTrue(
            OnlineServerEnvironment.DEVELOPMENT
                .allowsDevelopmentIdentityHeader,
        )
        assertTrue(
            OnlineServerEnvironment.DEVELOPMENT
                .allowsDevelopmentBots,
        )
        assertFalse(
            OnlineServerEnvironment.PRODUCTION
                .allowsDevelopmentIdentityHeader,
        )
        assertFalse(
            OnlineServerEnvironment.PRODUCTION
                .allowsDevelopmentBots,
        )
    }

    @Test
    fun production_requires_configured_signing_secret() {
        assertThrows(
            IllegalStateException::class.java,
        ) {
            createDefaultOnlineSessionTokenService(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                readEnvironmentVariable = { null },
            )
        }
    }

    @Test
    fun development_can_use_an_ephemeral_signing_secret() {
        val service = createDefaultOnlineSessionTokenService(
            serverEnvironment = OnlineServerEnvironment.DEVELOPMENT,
            readEnvironmentVariable = { null },
        )

        val session = service.issueAnonymousSession()

        assertNotNull(
            service.resolveAccessToken(
                accessToken = session.accessToken,
            ),
        )
    }

    @Test
    fun configured_signing_secret_is_stable_across_services() {
        val signingSecret =
            "0123456789abcdef0123456789abcdef"

        val firstService = createDefaultOnlineSessionTokenService(
            serverEnvironment = OnlineServerEnvironment.PRODUCTION,
            readEnvironmentVariable = { signingSecret },
        )
        val secondService = createDefaultOnlineSessionTokenService(
            serverEnvironment = OnlineServerEnvironment.PRODUCTION,
            readEnvironmentVariable = { signingSecret },
        )

        val session = firstService.issueAnonymousSession()

        assertEquals(
            session.playerId,
            secondService.resolveAccessToken(
                accessToken = session.accessToken,
            )?.playerId,
        )
    }
}
