package com.ahtohiofilho.dominopernambucano.online

data class OnlineRemotePollingPolicy(
    val enabled: Boolean,
    val intervalMillis: Long,
) {
    companion object {
        val Disabled = OnlineRemotePollingPolicy(
            enabled = false,
            intervalMillis = 0L,
        )

        val Default = OnlineRemotePollingPolicy(
            enabled = true,
            intervalMillis = 1_000L,
        )
    }
}