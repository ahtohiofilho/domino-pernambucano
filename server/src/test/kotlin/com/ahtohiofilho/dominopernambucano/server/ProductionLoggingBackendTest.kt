package com.ahtohiofilho.dominopernambucano.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLoggerFactory

class ProductionLoggingBackendTest {
    @Test
    fun server_runtime_has_concrete_slf4j_backend() {
        val loggerFactory = LoggerFactory.getILoggerFactory()

        assertFalse(
            "O servidor não pode descartar logs com NOPLoggerFactory.",
            loggerFactory is NOPLoggerFactory,
        )

        assertTrue(
            "O nível INFO precisa permanecer visível em produção.",
            LoggerFactory
                .getLogger(ProductionLoggingBackendTest::class.java)
                .isInfoEnabled,
        )
    }
}
