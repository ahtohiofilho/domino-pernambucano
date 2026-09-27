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