package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineTraceRequestBodyLimitTest {
    @Test
    fun rejects_trace_request_above_the_physical_body_limit() =
        testApplication {
            val ingestionPolicy =
                OnlineTraceIngestionPolicy.Default.copy(
                    maxRequestBodyBytes = 256L,
                )

            application {
                module(
                    store = InMemoryOnlineServerStore(
                        nowEpochMillis = { 1_000L },
                    ),
                    serverEnvironment =
                        OnlineServerEnvironment.TEST,
                    traceIngestionPolicy = ingestionPolicy,
                    traceArchive = OnlineTraceArchive(
                        outputDirectory =
                            Files.createTempDirectory(
                                "online-trace-body-limit-test",
                            ).toFile(),
                        ingestionPolicy = ingestionPolicy,
                    ),
                )
            }

            val oversizedBody =
                """{"entries":[],"padding":"${"x".repeat(512)}"}"""

            val response = client.post(
                urlString =
                    "/${OnlineRemoteRoutes.SUBMIT_TRACE_BATCH}",
            ) {
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    "player-1",
                )
                contentType(ContentType.Application.Json)
                setBody(oversizedBody)
            }

            assertEquals(
                HttpStatusCode.PayloadTooLarge,
                response.status,
            )
        }
}
