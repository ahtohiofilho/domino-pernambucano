package com.ahtohiofilho.dominopernambucano.online

data class OnlinePlayerIdentity(
    val playerId: String,
    val playerName: String,
)

fun createDefaultOnlinePlayerIdentity(): OnlinePlayerIdentity {
    return OnlinePlayerIdentity(
        playerId = "local-player",
        playerName = "Você",
    )
}

fun createDebugHostOnlinePlayerIdentity(): OnlinePlayerIdentity {
    return OnlinePlayerIdentity(
        playerId = "fake-host",
        playerName = "Anfitrião fake",
    )
}

fun createDebugFakeOnlinePlayerIdentity(
    fakePlayerNumber: Int,
): OnlinePlayerIdentity {
    return OnlinePlayerIdentity(
        playerId = createDebugFakeOnlinePlayerId(fakePlayerNumber),
        playerName = "Jogador $fakePlayerNumber",
    )
}

fun createDebugFakeOnlinePlayerId(
    fakePlayerNumber: Int,
): String {
    return "fake-player-$fakePlayerNumber"
}