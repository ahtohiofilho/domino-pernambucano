package com.ahtohiofilho.dominopernambucano.online

private const val ONLINE_PLAYER_ID_STORAGE_KEY = "online_player_id"
private const val LEGACY_ONLINE_PLAYER_NAME_STORAGE_KEY = "online_player_name"
private const val ONLINE_DISPLAY_NAME_STORAGE_KEY = "online_display_name"
private const val ONLINE_TABLE_NAME_STORAGE_KEY = "online_table_name"
private const val ONLINE_TABLE_NAME_MODE_STORAGE_KEY = "online_table_name_mode"
private const val ONLINE_TABLE_NAME_MODE_GENERATED = "generated"
private const val ONLINE_TABLE_NAME_MODE_CUSTOM = "custom"

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

    fun updateDisplayName(
        displayName: String,
    ): OnlinePlayerIdentity

    fun updateTableName(
        tableName: String,
    ): OnlinePlayerIdentity

    fun resetTableNameToGenerated(): OnlinePlayerIdentity

    @Deprecated(
        message = "Use updateDisplayName.",
        replaceWith = ReplaceWith("updateDisplayName(playerName)"),
    )
    fun updatePlayerName(
        playerName: String,
    ): OnlinePlayerIdentity {
        return updateDisplayName(
            displayName = playerName,
        )
    }
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

        val storedDisplayName = storage.readString(
            key = ONLINE_DISPLAY_NAME_STORAGE_KEY,
        )

        val legacyPlayerName = storage.readString(
            key = LEGACY_ONLINE_PLAYER_NAME_STORAGE_KEY,
        )

        val displayName = normalizeOnlineDisplayName(
            rawName = storedDisplayName ?: legacyPlayerName,
        )

        val storedTableName = storage.readString(
            key = ONLINE_TABLE_NAME_STORAGE_KEY,
        )

        val storedTableNameMode = storage.readString(
            key = ONLINE_TABLE_NAME_MODE_STORAGE_KEY,
        )

        val tableNameMode = when {
            storedTableNameMode == ONLINE_TABLE_NAME_MODE_CUSTOM -> {
                ONLINE_TABLE_NAME_MODE_CUSTOM
            }

            storedTableNameMode == ONLINE_TABLE_NAME_MODE_GENERATED -> {
                ONLINE_TABLE_NAME_MODE_GENERATED
            }

            storedTableName.isNullOrBlank() -> {
                ONLINE_TABLE_NAME_MODE_GENERATED
            }

            else -> {
                ONLINE_TABLE_NAME_MODE_CUSTOM
            }
        }

        val tableName = if (
            tableNameMode == ONLINE_TABLE_NAME_MODE_CUSTOM
        ) {
            normalizeOnlineTableName(
                rawName = storedTableName,
                fallbackDisplayName = displayName,
            )
        } else {
            createDefaultOnlineTableName(
                displayName = displayName,
            )
        }

        if (playerId != storedPlayerId) {
            storage.writeString(
                key = ONLINE_PLAYER_ID_STORAGE_KEY,
                value = playerId,
            )
        }

        if (displayName != storedDisplayName) {
            storage.writeString(
                key = ONLINE_DISPLAY_NAME_STORAGE_KEY,
                value = displayName,
            )
        }

        if (displayName != legacyPlayerName) {
            storage.writeString(
                key = LEGACY_ONLINE_PLAYER_NAME_STORAGE_KEY,
                value = displayName,
            )
        }

        if (tableName != storedTableName) {
            storage.writeString(
                key = ONLINE_TABLE_NAME_STORAGE_KEY,
                value = tableName,
            )
        }

        if (tableNameMode != storedTableNameMode) {
            storage.writeString(
                key = ONLINE_TABLE_NAME_MODE_STORAGE_KEY,
                value = tableNameMode,
            )
        }

        return OnlinePlayerIdentity(
            playerId = playerId,
            displayName = displayName,
            tableName = tableName,
        )
    }

    override fun updateDisplayName(
        displayName: String,
    ): OnlinePlayerIdentity {
        val currentIdentity = getOrCreate()

        val normalizedDisplayName = normalizeOnlineDisplayName(
            rawName = displayName,
        )

        storage.writeString(
            key = ONLINE_DISPLAY_NAME_STORAGE_KEY,
            value = normalizedDisplayName,
        )
        storage.writeString(
            key = LEGACY_ONLINE_PLAYER_NAME_STORAGE_KEY,
            value = normalizedDisplayName,
        )

        val tableNameMode = storage.readString(
            key = ONLINE_TABLE_NAME_MODE_STORAGE_KEY,
        )

        val resolvedTableName = if (
            tableNameMode == ONLINE_TABLE_NAME_MODE_CUSTOM
        ) {
            currentIdentity.tableName
        } else {
            createDefaultOnlineTableName(
                displayName = normalizedDisplayName,
            ).also { generatedTableName ->
                storage.writeString(
                    key = ONLINE_TABLE_NAME_STORAGE_KEY,
                    value = generatedTableName,
                )
                storage.writeString(
                    key = ONLINE_TABLE_NAME_MODE_STORAGE_KEY,
                    value = ONLINE_TABLE_NAME_MODE_GENERATED,
                )
            }
        }

        return currentIdentity.copy(
            displayName = normalizedDisplayName,
            tableName = resolvedTableName,
        )
    }

    override fun updateTableName(
        tableName: String,
    ): OnlinePlayerIdentity {
        val currentIdentity = getOrCreate()

        val normalizedTableName = normalizeOnlineTableName(
            rawName = tableName,
            fallbackDisplayName = currentIdentity.displayName,
        )

        storage.writeString(
            key = ONLINE_TABLE_NAME_STORAGE_KEY,
            value = normalizedTableName,
        )
        storage.writeString(
            key = ONLINE_TABLE_NAME_MODE_STORAGE_KEY,
            value = ONLINE_TABLE_NAME_MODE_CUSTOM,
        )

        return currentIdentity.copy(
            tableName = normalizedTableName,
        )
    }

    override fun resetTableNameToGenerated(): OnlinePlayerIdentity {
        val currentIdentity = getOrCreate()
        val generatedTableName = createDefaultOnlineTableName(
            displayName = currentIdentity.displayName,
        )

        storage.writeString(
            key = ONLINE_TABLE_NAME_STORAGE_KEY,
            value = generatedTableName,
        )
        storage.writeString(
            key = ONLINE_TABLE_NAME_MODE_STORAGE_KEY,
            value = ONLINE_TABLE_NAME_MODE_GENERATED,
        )

        return currentIdentity.copy(
            tableName = generatedTableName,
        )
    }
}
