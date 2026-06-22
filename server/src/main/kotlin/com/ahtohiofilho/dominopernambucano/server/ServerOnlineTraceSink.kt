package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceEvent
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSink
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val SERVER_ONLINE_TRACE_PREFIX = "online_trace="

class ServerOnlineTraceSink(
    private val emit: (String) -> Unit,
    private val json: Json = Json {
        encodeDefaults = true
    },
) : OnlineTraceSink {
    override fun record(
        event: OnlineTraceEvent,
    ) {
        emit(
            SERVER_ONLINE_TRACE_PREFIX + json.encodeToString(event),
        )
    }
}
