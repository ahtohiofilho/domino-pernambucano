package com.ahtohiofilho.dominopernambucano.online

data class OnlineDebugOptions(
    val allowDemoRoomCreation: Boolean,
    val allowFakePlayerCompletion: Boolean,
) {
    companion object {
        val FakeBackend = OnlineDebugOptions(
            allowDemoRoomCreation = true,
            allowFakePlayerCompletion = true,
        )

        val RealBackend = OnlineDebugOptions(
            allowDemoRoomCreation = false,
            allowFakePlayerCompletion = true,
        )
    }
}