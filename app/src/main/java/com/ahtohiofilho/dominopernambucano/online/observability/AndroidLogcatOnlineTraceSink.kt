package com.ahtohiofilho.dominopernambucano.online.observability

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val ANDROID_ONLINE_TRACE_PREFIX = "online_trace="

class AndroidLogcatOnlineTraceSink(
    private val tag: String = DEFAULT_TAG,
    private val json: Json = Json {
        encodeDefaults = true
    },
) : OnlineTraceSink {
    override fun record(
        event: OnlineTraceEvent,
    ) {
        val message = ANDROID_ONLINE_TRACE_PREFIX + json.encodeToString(event)

        when (event.level) {
            OnlineTraceLevel.DEBUG -> Log.d(tag, message)
            OnlineTraceLevel.INFO -> Log.i(tag, message)
            OnlineTraceLevel.WARN -> Log.w(tag, message)
            OnlineTraceLevel.ERROR -> Log.e(tag, message)
        }
    }

    private companion object {
        const val DEFAULT_TAG = "DominoOnlineTrace"
    }
}
