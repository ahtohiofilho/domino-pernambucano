package com.ahtohiofilho.dominopernambucano.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationTest {
    @Test
    fun health_returns_ok() = testApplication {
        application {
            module()
        }

        val response = client.get(
            urlString = "/health",
        )

        assertEquals(
            HttpStatusCode.OK,
            response.status,
        )

        assertTrue(
            response.bodyAsText().contains(
                "\"status\":\"ok\"",
            ),
        )
    }
}