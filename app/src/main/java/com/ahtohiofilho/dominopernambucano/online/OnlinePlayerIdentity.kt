package com.ahtohiofilho.dominopernambucano.online

import java.util.UUID

@Deprecated(
    message = "Use DEFAULT_ONLINE_DISPLAY_NAME.",
    replaceWith = ReplaceWith("DEFAULT_ONLINE_DISPLAY_NAME"),
)
const val DEFAULT_ONLINE_PLAYER_NAME = DEFAULT_ONLINE_DISPLAY_NAME

@Deprecated(
    message = "Use MAX_ONLINE_DISPLAY_NAME_LENGTH.",
    replaceWith = ReplaceWith("MAX_ONLINE_DISPLAY_NAME_LENGTH"),
)
const val MAX_ONLINE_PLAYER_NAME_LENGTH = MAX_ONLINE_DISPLAY_NAME_LENGTH

data class OnlinePlayerIdentity(
    val playerId: String,
    val displayName: String,
    val tableName: String,
) {
    @Deprecated(
        message = "Use displayName ou tableName conforme a superfície.",
        replaceWith = ReplaceWith("displayName"),
    )
    val playerName: String
        get() = displayName
}

fun createOnlinePlayerId(): String {
    return "player-${UUID.randomUUID()}"
}

@Deprecated(
    message = "Use normalizeOnlineDisplayName.",
    replaceWith = ReplaceWith("normalizeOnlineDisplayName(rawName)"),
)
fun normalizeOnlinePlayerName(
    rawName: String?,
): String {
    return normalizeOnlineDisplayName(
        rawName = rawName,
    )
}

fun createDebugHostOnlinePlayerIdentity(): OnlinePlayerIdentity {
    val displayName = "Anfitrião fake"

    return OnlinePlayerIdentity(
        playerId = "fake-host",
        displayName = displayName,
        tableName = "DBG",
    )
}

fun createDebugFakeOnlinePlayerIdentity(
    fakePlayerNumber: Int,
): OnlinePlayerIdentity {
    val displayName = "Jogador $fakePlayerNumber"

    return OnlinePlayerIdentity(
        playerId = createDebugFakeOnlinePlayerId(fakePlayerNumber),
        displayName = displayName,
        tableName =
            "F" +
                (fakePlayerNumber % 100)
                    .toString()
                    .padStart(
                        length = 2,
                        padChar = '0',
                    ),
    )
}

fun createDebugFakeOnlinePlayerId(
    fakePlayerNumber: Int,
): String {
    return "fake-player-$fakePlayerNumber"
}
