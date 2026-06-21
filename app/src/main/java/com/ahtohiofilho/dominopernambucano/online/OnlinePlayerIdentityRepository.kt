package com.ahtohiofilho.dominopernambucano.online

private const val ONLINE_PLAYER_ID_STORAGE_KEY = "online_player_id"
private const val ONLINE_PLAYER_NAME_STORAGE_KEY = "online_player_name"

interface OnlinePlayerIdentityStorage {
    fun readString(
        key: String,
    ): String?

    fun writeString(
        key: String,
        value: String,
    )
}

interface OnlinePlayerIdentityStore {
    fun getOrCreate(): OnlinePlayerIdentity

    fun updatePlayerName(
        playerName: String,
    ): OnlinePlayerIdentity
}

class OnlinePlayerIdentityRepository(
    private val storage: OnlinePlayerIdentityStorage,
    private val playerIdFactory: () -> String = ::createOnlinePlayerId,
) : OnlinePlayerIdentityStore {
    override fun getOrCreate(): OnlinePlayerIdentity {
        val storedPlayerId = storage
            .readString(
                key = ONLINE_PLAYER_ID_STORAGE_KEY,
            )
            .orEmpty()
            .trim()

        val playerId = storedPlayerId.ifBlank {
            playerIdFactory()
                .trim()
                .also { generatedPlayerId ->
                    check(generatedPlayerId.isNotBlank()) {
                        "A fábrica de identidade online retornou um playerId vazio."
                    }
                }
        }

        val storedPlayerName = storage.readString(
            key = ONLINE_PLAYER_NAME_STORAGE_KEY,
        )

        val playerName = normalizeOnlinePlayerName(
            rawName = storedPlayerName,
        )

        if (playerId != storedPlayerId) {
            storage.writeString(
                key = ONLINE_PLAYER_ID_STORAGE_KEY,
                value = playerId,
            )
        }

        if (playerName != storedPlayerName) {
            storage.writeString(
                key = ONLINE_PLAYER_NAME_STORAGE_KEY,
                value = playerName,
            )
        }

        return OnlinePlayerIdentity(
            playerId = playerId,
            playerName = playerName,
        )
    }

    override fun updatePlayerName(
        playerName: String,
    ): OnlinePlayerIdentity {
        val currentIdentity = getOrCreate()

        val normalizedPlayerName = normalizeOnlinePlayerName(
            rawName = playerName,
        )

        if (normalizedPlayerName != currentIdentity.playerName) {
            storage.writeString(
                key = ONLINE_PLAYER_NAME_STORAGE_KEY,
                value = normalizedPlayerName,
            )
        }

        return currentIdentity.copy(
            playerName = normalizedPlayerName,
        )
    }
}