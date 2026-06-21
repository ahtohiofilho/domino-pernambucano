package com.ahtohiofilho.dominopernambucano.online

import java.util.UUID

const val DEFAULT_ONLINE_PLAYER_NAME = "Jogador"
const val MAX_ONLINE_PLAYER_NAME_LENGTH = 24

private val nameWhitespaceRegex = Regex("\\s+")

data class OnlinePlayerIdentity(
    val playerId: String,
    val playerName: String,
)

fun createOnlinePlayerId(): String {
    return "player-${UUID.randomUUID()}"
}

fun normalizeOnlinePlayerName(
    rawName: String?,
): String {
    val normalizedName = rawName
        .orEmpty()
        .trim()
        .replace(
            regex = nameWhitespaceRegex,
            replacement = " ",
        )
        .take(MAX_ONLINE_PLAYER_NAME_LENGTH)

    return normalizedName.ifBlank {
        DEFAULT_ONLINE_PLAYER_NAME
    }
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