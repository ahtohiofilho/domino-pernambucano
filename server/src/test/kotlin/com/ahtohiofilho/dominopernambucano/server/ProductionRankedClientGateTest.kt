package com.ahtohiofilho.dominopernambucano.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductionRankedClientGateTest {
    @Test
    fun production_defaults_to_closed_when_mode_is_missing() {
        val gate = ProductionRankedClientGate.fromEnvironment(
            serverEnvironment = OnlineServerEnvironment.PRODUCTION,
            readEnvironmentVariable = { null },
        )

        assertEquals(
            ProductionRankedClientDecision.MAINTENANCE,
            gate.evaluate(
                suppliedClientVersionCode = "123",
                syntheticAccount = false,
            ),
        )
    }

    @Test
    fun versioned_mode_requires_exact_human_version() {
        val values = mapOf(
            PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE to
                "versioned",
            PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE to
                "123",
        )
        val gate = ProductionRankedClientGate.fromEnvironment(
            serverEnvironment = OnlineServerEnvironment.PRODUCTION,
            readEnvironmentVariable = values::get,
        )

        assertEquals(
            ProductionRankedClientDecision.UPDATE_REQUIRED,
            gate.evaluate(
                suppliedClientVersionCode = null,
                syntheticAccount = false,
            ),
        )
        assertEquals(
            ProductionRankedClientDecision.UPDATE_REQUIRED,
            gate.evaluate(
                suppliedClientVersionCode = "122",
                syntheticAccount = false,
            ),
        )
        assertEquals(
            ProductionRankedClientDecision.ALLOWED,
            gate.evaluate(
                suppliedClientVersionCode = "123",
                syntheticAccount = false,
            ),
        )
        assertEquals(
            ProductionRankedClientDecision.ALLOWED,
            gate.evaluate(
                suppliedClientVersionCode = null,
                syntheticAccount = true,
            ),
        )
    }

    @Test
    fun closed_mode_blocks_synthetic_too() {
        assertEquals(
            ProductionRankedClientDecision.MAINTENANCE,
            ProductionRankedClientGate.closedForTest().evaluate(
                suppliedClientVersionCode = "123",
                syntheticAccount = true,
            ),
        )
    }

    @Test
    fun versioned_mode_can_allow_exact_transitional_versions_32_and_33() {
        val values = mapOf(
            PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE to
                "versioned",
            PRODUCTION_REQUIRED_CLIENT_VERSION_CODE_ENVIRONMENT_VARIABLE to
                "32",
            PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE to
                "32,33",
        )

        val gate = ProductionRankedClientGate.fromEnvironment(
            serverEnvironment = OnlineServerEnvironment.PRODUCTION,
            readEnvironmentVariable = values::get,
        )

        assertEquals(
            ProductionRankedClientDecision.UPDATE_REQUIRED,
            gate.evaluate(
                suppliedClientVersionCode = "31",
                syntheticAccount = false,
            ),
        )
        assertEquals(
            ProductionRankedClientDecision.ALLOWED,
            gate.evaluate(
                suppliedClientVersionCode = "32",
                syntheticAccount = false,
            ),
        )
        assertEquals(
            ProductionRankedClientDecision.ALLOWED,
            gate.evaluate(
                suppliedClientVersionCode = "33",
                syntheticAccount = false,
            ),
        )
        assertEquals(
            ProductionRankedClientDecision.UPDATE_REQUIRED,
            gate.evaluate(
                suppliedClientVersionCode = "34",
                syntheticAccount = false,
            ),
        )
    }

    @Test
    fun transitional_allowlist_does_not_require_legacy_single_version_variable() {
        val values = mapOf(
            PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE to
                "versioned",
            PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE to
                "32, 33",
        )

        val gate = ProductionRankedClientGate.fromEnvironment(
            serverEnvironment = OnlineServerEnvironment.PRODUCTION,
            readEnvironmentVariable = values::get,
        )

        assertEquals(
            ProductionRankedClientDecision.ALLOWED,
            gate.evaluate(
                suppliedClientVersionCode = "32",
                syntheticAccount = false,
            ),
        )
        assertEquals(
            ProductionRankedClientDecision.ALLOWED,
            gate.evaluate(
                suppliedClientVersionCode = "33",
                syntheticAccount = false,
            ),
        )
    }

    @Test
    fun transitional_allowlist_rejects_invalid_values() {
        val values = mapOf(
            PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE to
                "versioned",
            PRODUCTION_ALLOWED_CLIENT_VERSION_CODES_ENVIRONMENT_VARIABLE to
                "32,abc",
        )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            ProductionRankedClientGate.fromEnvironment(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                readEnvironmentVariable = values::get,
            )
        }
    }

    @Test
    fun versioned_mode_rejects_missing_required_version_configuration() {
        assertThrows(
            IllegalStateException::class.java,
        ) {
            ProductionRankedClientGate.fromEnvironment(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                readEnvironmentVariable = { key ->
                    if (
                        key ==
                        PRODUCTION_RANKED_ACCESS_MODE_ENVIRONMENT_VARIABLE
                    ) {
                        "versioned"
                    } else {
                        null
                    }
                },
            )
        }
    }

    @Test
    fun non_production_is_unrestricted() {
        val gate = ProductionRankedClientGate.fromEnvironment(
            serverEnvironment = OnlineServerEnvironment.TEST,
            readEnvironmentVariable = { null },
        )

        assertEquals(
            ProductionRankedClientDecision.ALLOWED,
            gate.evaluate(
                suppliedClientVersionCode = null,
                syntheticAccount = false,
            ),
        )
    }
}